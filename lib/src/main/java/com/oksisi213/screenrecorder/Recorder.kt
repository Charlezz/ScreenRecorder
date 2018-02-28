package com.oksisi213.screenrecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.HandlerThread
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.util.SparseLongArray
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*


/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 21..
 */
open abstract class Recorder<out T> {

	private val dstPath by lazy {
		val dateFormat = SimpleDateFormat("yyMMdd_HHmmss")
		val date = dateFormat.format(Date())
		Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/record" + date
	}
	protected val muxer: MediaMuxer by lazy {
		MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
	}

	protected var videoConfig: VideoConfig? = null
	protected var audioConfig: AudioConfig? = null

	fun setVideoConfig(config: VideoConfig): T {
		this.videoConfig = config
		return this as T
	}

	fun setAudioConfig(config: AudioConfig): T {
		this.audioConfig = config
		return this as T
	}
}


class ScreenRecorder constructor(context: Context, data: Intent) : Recorder<ScreenRecorder>(), WeakRefHandler.IMessageListener {
	val TAG = ScreenRecorder::class.java.simpleName

	companion object {
		fun requestCaptureIntent(activity: Activity, requestCode: Int) {
			val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			activity.startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
		}
	}

	private val MSG_START = 0
	private val MSG_STOP = 1
	private val INVALID_INDEX = -1

	private val mediaProjectionManager: MediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
	private val mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)
	private var micRecord: AudioRecord? = null
	private var audioOutputFormat: MediaFormat? = null
	private var videoOutputFormat: MediaFormat? = null
	private var minBytes = 0
	private var audioTrackIndex = INVALID_INDEX
	private var videoTrackIndex = INVALID_INDEX

	private var videoEncoder: MediaCodec? = null
	private var audioEncoder: MediaCodec? = null
	private var isMuxerStart = false

	private var pendingVideoBuffer = LinkedList<Pair<Int, MediaCodec.BufferInfo>>()
	private var pendingAudioBuffer = LinkedList<Pair<Int, MediaCodec.BufferInfo>>()

	init {
		audioConfig = AudioConfig.getDefaultConfig()
		videoConfig = VideoConfig.getDefaultConfig()
	}


	val worker = HandlerThread("worker").apply {
		start()
	}
	val handler = WeakRefHandler(worker.looper, this)
	var timeStamp = 0L


	override fun handleMessage(message: Message) {
		when (message.what) {
			MSG_START -> {
				videoEncoder = videoConfig?.createMediaCodec()
				videoEncoder?.configure(videoConfig?.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				videoEncoder?.setCallback(object : MediaCodec.Callback() {
					override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
						Log.i(TAG, "video onOutputBufferAvailable")
						info?.let {
							muxVideo(index, info)
						}
					}

					override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
						Log.e(TAG, "video onInputBufferAvailable")
					}

					override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
						Log.e(TAG, "video onOutputFormatChanged")
						videoOutputFormat = format
						startMuxerIfReady()
					}

					override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
						Log.e(TAG, "video onError")
					}
				})

				mediaProjection?.createVirtualDisplay(
						TAG,
						videoConfig!!.width,
						videoConfig!!.height,
						1,
						DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
						videoEncoder?.createInputSurface(),
						object : VirtualDisplay.Callback() {
							override fun onResumed() {
								super.onResumed()
								Log.e(TAG, "virtual display onResumed")
							}

							override fun onPaused() {
								super.onPaused()
								Log.e(TAG, "virtual display onPaused")
							}

							override fun onStopped() {
								super.onStopped()
								Log.e(TAG, "virtual display onStopped")
							}
						},
						null
				)
				videoEncoder?.start()


				///audio
				minBytes = AudioRecord.getMinBufferSize(
						audioConfig!!.sampleRate,
						if (audioConfig!!.channelCount == 1) {
							AudioFormat.CHANNEL_IN_MONO
						} else {
							AudioFormat.CHANNEL_IN_STEREO
						},
						AudioFormat.ENCODING_PCM_16BIT
				)
				Log.e(TAG, "minBytes:${minBytes}")

				audioEncoder = audioConfig?.createMediaCodec()
				Log.e(TAG, audioConfig.toString())
				audioEncoder?.configure(audioConfig?.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				audioEncoder?.setCallback(object : MediaCodec.Callback() {
					override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
						info?.let {
							muxAudio(index, it)
						}
					}

					override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
						val inputBuffer = codec?.getInputBuffer(index)
						val bufferCount = micRecord?.read(inputBuffer, inputBuffer!!.limit())
						codec?.queueInputBuffer(
								index,
								inputBuffer!!.position(),
								inputBuffer.limit(),
								calculateFrameTimestamp(bufferCount!! shl 3),
								MediaCodec.BUFFER_FLAG_KEY_FRAME)
					}

					override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
						Log.e(TAG, "audio onOutputFormatChanged")
						audioOutputFormat = format
						startMuxerIfReady()
					}

					override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
						Log.e(TAG, "audio onError")
					}
				})

				var minBytes = AudioRecord.getMinBufferSize(audioConfig!!.sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
				if (minBytes <= 0) {
					Log.e(TAG, String.format(Locale.US, "Bad arguments: getMinBufferSize(%d, %d, %d)",
							audioConfig!!.sampleRate, audioConfig!!.channelCount, AudioFormat.ENCODING_PCM_16BIT))
					return
				}

				micRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
						audioConfig!!.sampleRate,
						AudioFormat.CHANNEL_IN_MONO,
						AudioFormat.ENCODING_PCM_16BIT,
						minBytes * 1)


				if (micRecord?.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
					Log.e(TAG, "Mic is used by other app")
				}

				if (micRecord == null) {
					throw NullPointerException("MicRecorder is null")
				}

				if (micRecord?.state == AudioRecord.STATE_UNINITIALIZED) {
					Log.e(TAG, "bad arguments")
				}

				micRecord?.startRecording()

				if (micRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
					Log.e(TAG, "started to record but Mic is used by other app")
				}
				audioEncoder?.start()

			}
			MSG_STOP -> {
				Log.e(TAG, "stop")
				videoEncoder?.stop()
				videoEncoder?.release()

				audioEncoder?.stop()
				audioEncoder?.release()

				micRecord?.stop()
				micRecord?.release()

				val eos = MediaCodec.BufferInfo()
				val buffer = ByteBuffer.allocate(0)
				eos.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)

				muxer.writeSampleData(audioTrackIndex, buffer, eos)
				muxer.writeSampleData(videoTrackIndex, buffer, eos)

				audioTrackIndex = INVALID_INDEX
				videoTrackIndex = INVALID_INDEX

				videoPtsOffset = 0
				audioPtsOffset = 0


				muxer.stop()
				muxer.release()
			}
		}
	}

	fun startMuxerIfReady() {
		Log.e(TAG, "startMuxerIfReady")
		if ((videoConfig != null && videoOutputFormat == null) ||
				(audioConfig != null && audioOutputFormat == null)) {
			Log.e(TAG, "see you soon")
			return
		}

		videoTrackIndex = muxer.addTrack(videoOutputFormat)
		Log.e(TAG, "videoTrackIndex=$videoTrackIndex")
		audioTrackIndex = muxer.addTrack(audioOutputFormat)
		Log.e(TAG, "audioTrackIndex=$audioTrackIndex")
		isMuxerStart = true
		muxer.start()

		while (pendingAudioBuffer.isEmpty() && pendingVideoBuffer.isEmpty()) {
			Log.e(TAG, "no pending data")
			return
		}

		var pendingBuffer: Pair<Int, MediaCodec.BufferInfo>? = null

		while ({ pendingBuffer = pendingVideoBuffer.poll(); pendingBuffer }() != null) {
			pendingBuffer?.let {
				muxVideo(it.first, it.second)
			}
		}

		pendingBuffer = null

		while ({ pendingBuffer = pendingAudioBuffer.poll(); pendingBuffer }() != null) {
			pendingBuffer?.let {
				muxAudio(it.first, it.second)
			}
		}
	}

	private fun muxAudio(index: Int, bufferInfo: MediaCodec.BufferInfo) {
		Log.i(TAG, "mux audio:${bufferInfo.presentationTimeUs}")

		if (audioTrackIndex == INVALID_INDEX || !isMuxerStart) {
			Log.e(TAG, "audio Track is not ready yet")
			pendingAudioBuffer.push(Pair(index, bufferInfo))
			return
		}

		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
			Log.e(TAG, "muxAudio:BUFFER_FLAG_CODEC_CONFIG")
			bufferInfo.size = 0
		} else {
			resetAudioPts(bufferInfo)
			Log.e(TAG, "audio pts:${bufferInfo.presentationTimeUs}")
		}
		muxer.writeSampleData(audioTrackIndex, audioEncoder?.getOutputBuffer(index), bufferInfo)
		audioEncoder?.releaseOutputBuffer(index, false)
		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
			Log.e(TAG, "Stop Audio encoder and muxer, since the buffer has been marked with EOS")
			audioTrackIndex = INVALID_INDEX
//			mVideoTrackIndex = INVALID_INDEX
//			signalStop(true)
		}
	}

	private fun muxVideo(index: Int, bufferInfo: MediaCodec.BufferInfo) {
		Log.i(TAG, "mux video:${bufferInfo.presentationTimeUs}")

		if (videoTrackIndex == INVALID_INDEX || !isMuxerStart) {
			Log.e(TAG, "video Track is not ready yet")
			pendingVideoBuffer.push(Pair(index, bufferInfo))
			return
		}


		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
			Log.e(TAG, "muxAudio:BUFFER_FLAG_CODEC_CONFIG")
			bufferInfo.size = 0
		} else {
			resetVideoPts(bufferInfo)
			Log.e(TAG, "video pts:${bufferInfo.presentationTimeUs}")
		}

		muxer.writeSampleData(videoTrackIndex, videoEncoder?.getOutputBuffer(index), bufferInfo)
		videoEncoder?.releaseOutputBuffer(index, false)

		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
			Log.e(TAG, "Stop Video encoder and muxer, since the buffer has been marked with EOS")
			videoTrackIndex = INVALID_INDEX
//			mVideoTrackIndex = INVALID_INDEX
//			signalStop(true)
		}
	}

	fun record(): ScreenRecorder {
		handler.sendEmptyMessage(MSG_START)
		return this
	}

	fun stop() {
		handler.sendEmptyMessage(MSG_STOP)
	}

	private val LAST_FRAME_ID = -1
	private val mFramesUsCache = SparseLongArray(2)
	private fun calculateFrameTimestamp(totalBits: Int): Long {
		val samples = totalBits shr 4
		var frameUs = mFramesUsCache.get(samples, -1)
		if (frameUs == -1L) {
			frameUs = (samples * 1000000 / 44100).toLong()
			mFramesUsCache.put(samples, frameUs)
		}
		var timeUs = SystemClock.elapsedRealtimeNanos() / 1000
		// accounts the delay of polling the audio sample data
		timeUs -= frameUs
		var currentUs: Long
		val lastFrameUs = mFramesUsCache.get(LAST_FRAME_ID, -1)
		if (lastFrameUs == -1L) { // it's the first frame
			currentUs = timeUs
		} else {
			currentUs = lastFrameUs
		}
		Log.i(TAG, "count samples pts: $currentUs, time pts: $timeUs, samples: $samples")
		// maybe too late to acquire sample data
		if (timeUs - currentUs >= frameUs shl 1) {
			// reset
			currentUs = timeUs
		}
		mFramesUsCache.put(LAST_FRAME_ID, currentUs + frameUs)
		return currentUs
	}

	private var videoPtsOffset: Long = 0
	private var audioPtsOffset: Long = 0

	private fun resetAudioPts(buffer: MediaCodec.BufferInfo) {
		if (audioPtsOffset == 0L) {
			audioPtsOffset = buffer.presentationTimeUs
			buffer.presentationTimeUs = 0
		} else {
			buffer.presentationTimeUs -= audioPtsOffset
		}
	}

	private fun resetVideoPts(buffer: MediaCodec.BufferInfo) {
		if (videoPtsOffset == 0L) {
			videoPtsOffset = buffer.presentationTimeUs
			buffer.presentationTimeUs = 0
		} else {
			buffer.presentationTimeUs -= videoPtsOffset
		}
	}


}