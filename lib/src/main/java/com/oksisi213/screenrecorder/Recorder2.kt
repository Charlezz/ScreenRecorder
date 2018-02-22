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
open abstract class Recorder2<out T> {

	private val dstPath by lazy {
		val dateFormat = SimpleDateFormat("yyMMdd_HHmmss")
		val date = dateFormat.format(Date())
		Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/record" + date
	}
	protected val muxer: MediaMuxer by lazy {
		MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
	}

	protected var videoConfig: VideoConfig = VideoConfig.getDefaultConfig()
	protected var audioConfig: AudioConfig = AudioConfig.getDefaultConfig()

	fun setVideoConfig(config: VideoConfig): T {
		this.videoConfig = config
		return this as T
	}

	fun setAudioConfig(config: AudioConfig): T {
		this.audioConfig = config
		return this as T
	}
}


class ScreenRecorder2 constructor(context: Context, data: Intent) : Recorder2<ScreenRecorder2>(), WeakRefHandler.IMessageListener {
	val TAG = ScreenRecorder2::class.java.simpleName

	companion object {
		fun requestCaptureIntent(activity: Activity, requestCode: Int) {
			val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			activity.startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
		}
	}

	private val MSG_START = 0
	private val MSG_STOP = 1
	private val mediaProjectionManager: MediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
	private val mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)
	private var micRecord: AudioRecord? = null
	private var audioOutputFormat: MediaFormat? = null
	private var minBytes = 0
	private var audioTrackIndex = -1
	private var videoEncoder: MediaCodec? = null
	private var audioEncoder: MediaCodec? = null


	val worker = HandlerThread("worker").apply {
		start()
	}
	val handler = WeakRefHandler(worker.looper, this)
	var timeStamp = 0L

	override fun handleMessage(message: Message) {
		when (message.what) {
			MSG_START -> {
				videoEncoder = videoConfig.createMediaCodec()
				videoEncoder?.configure(videoConfig.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				videoEncoder?.setCallback(object : MediaCodec.Callback() {
					override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
						Log.e(TAG, "video onOutputBufferAvailable")
						codec?.releaseOutputBuffer(index, false)
					}

					override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
						Log.e(TAG, "video onInputBufferAvailable")
					}

					override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
						Log.e(TAG, "video onOutputFormatChanged")
					}

					override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
						Log.e(TAG, "video onError")
					}

				})

				mediaProjection?.createVirtualDisplay(
						TAG,
						videoConfig.width,
						videoConfig.height,
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
				audioEncoder = audioConfig.createMediaCodec()
				Log.e(TAG, audioConfig.toString())
				audioEncoder?.configure(audioConfig.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				audioEncoder?.setCallback(object : MediaCodec.Callback() {
					override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
						Log.w(TAG, "audio onOutputBufferAvailable ${info!!.presentationTimeUs}")

						if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG !== 0) {
							info.size = 0
						}

						muxer.writeSampleData(audioTrackIndex, codec?.getOutputBuffer(index), info)
						codec?.releaseOutputBuffer(index, false)
					}

					override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
						Log.w(TAG, "audio onInputBufferAvailable")
						val inputBuffer = codec?.getInputBuffer(index)

						Log.e(TAG, "minBytes:${minBytes}")
						Log.e(TAG, "inputBuffer!!.limit():${inputBuffer!!.limit()}")
						val bufferCount = micRecord?.read(inputBuffer, inputBuffer!!.limit())
						Log.e(TAG, "bufferCount:$bufferCount")
						codec?.queueInputBuffer(
								index,
								inputBuffer!!.position(),
								inputBuffer!!.limit(),
								calculateFrameTimestamp(bufferCount!! shl 3),
								MediaCodec.BUFFER_FLAG_KEY_FRAME)
					}

					override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
						audioOutputFormat = format
						minBytes = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
						Log.e(TAG, "audio onOutputFormatChanged")
						audioTrackIndex = muxer.addTrack(audioOutputFormat)
						muxer.start()
					}

					override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
						Log.e(TAG, "audio onError")
					}
				})

				var minBytes = AudioRecord.getMinBufferSize(audioConfig.sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
				if (minBytes <= 0) {
					Log.e(TAG, String.format(Locale.US, "Bad arguments: getMinBufferSize(%d, %d, %d)",
							audioConfig.sampleRate, audioConfig.channelCount, AudioFormat.ENCODING_PCM_16BIT))
					return
				}

				micRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
						audioConfig.sampleRate,
						AudioFormat.CHANNEL_IN_MONO,
						AudioFormat.ENCODING_PCM_16BIT,
						minBytes * 1)

				if (micRecord?.state == AudioRecord.STATE_UNINITIALIZED) {
					Log.e(TAG, "bad arguments")
				}
				micRecord?.startRecording()
				audioEncoder?.start()

			}
			MSG_STOP -> {
				videoEncoder?.stop()
				audioEncoder?.stop()
				val eos = MediaCodec.BufferInfo()
				val buffer = ByteBuffer.allocate(0)
				eos.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
				muxer.writeSampleData(audioTrackIndex, buffer, eos)
				muxer.stop()
				muxer.release()
			}
		}
	}

	fun record(): ScreenRecorder2 {
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

}