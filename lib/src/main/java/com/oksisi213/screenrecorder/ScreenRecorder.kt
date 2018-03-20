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
import java.util.concurrent.locks.ReentrantLock


/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 21..
 */


class ScreenRecorder constructor(context: Context, data: Intent) : WeakRefHandler.IMessageListener {
	companion object {
		private val TAG = ScreenRecorder::class.java.simpleName
		fun requestCaptureIntent(activity: Activity, requestCode: Int) {
			val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			activity.startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
		}
	}

	var dstPath: String = {
		val dateFormat = SimpleDateFormat("yyMMdd_HHmmss")
		val date = dateFormat.format(Date())
		Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath + "/record" + date
	}()


	private var muxer: MediaMuxer? = null

	var videoConfig: VideoConfig? = null
	var audioConfig: AudioConfig? = null

	private var stateChangeListener: OnStateChangeListener? = null

	fun setOnStateChangeListener(stateChangeListener: OnStateChangeListener?) {
		this.stateChangeListener = stateChangeListener
	}


	interface OnStateChangeListener {
		fun onError(errorCode: ErrorCode, message: String)
		fun onStateChanged(state: State)
	}

	private val MSG_START = 0
	private val MSG_STOP = 1
	private val MSG_WRITE_AUDIO = 2

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

	private val pendingVideoOutputBuffer = LinkedList<Pair<Int, MediaCodec.BufferInfo>>()
	private val pendingAudioOutputBuffer = LinkedList<Pair<Int, MediaCodec.BufferInfo>>()

	private val pendingAudioInputBuffer = LinkedList<Pair<Int, MediaCodec?>>()
	private var audioBufferPool: ByteBufferPool? = null

	private var state: State = State.NONE
	var usingMic = false


	init {
		audioConfig = AudioConfig.getDefaultConfig()
		videoConfig = VideoConfig.getDefaultConfig(context as Activity)
		usingMic = true
	}


	private val worker = HandlerThread(String.format("%s %s", TAG, "Worker")).apply {
		start()
	}
	private val handler = WeakRefHandler(worker.looper, this)

	override fun handleMessage(message: Message) {
		when (message.what) {
			MSG_START -> {
				setState(State.PREPARING)
				setUpVideoEncoder()
				setUpAudioEncoder()
				setState(State.RECORDING)
			}
			MSG_STOP -> {
				setState(State.STOPPED)
				stateChangeListener = null
				releaseAudio()
				releaseVideo()
				releaseMuxer()
			}

			MSG_WRITE_AUDIO -> {
				writePCMToPool(message)
			}
		}
	}

	private fun setUpVideoEncoder() {
		if (videoConfig == null) {
			stateChangeListener?.onError(ErrorCode.VIDEO_CONFIG_ERROR, "VideoConfig Must be initialized")
			stop()
			return
		}

		videoEncoder = videoConfig?.createMediaCodec()

		if (videoEncoder == null) {
			stateChangeListener?.onError(ErrorCode.VIDEO_ENCODER_ERROR, "Failed to create MediaCodec")
		}

		videoEncoder?.configure(videoConfig?.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
		videoEncoder?.setCallback(object : MediaCodec.Callback() {
			override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
				info?.let {
					muxVideo(index, info)
				}
			}

			override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {

			}

			override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
				Log.e(TAG, "video onOutputFormatChanged")
				videoOutputFormat = format
				startMuxingIfReady()
			}

			override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
				stateChangeListener?.onError(ErrorCode.VIDEO_ENCODER_ERROR, e.toString())
				stop()
			}
		})

		mediaProjection?.createVirtualDisplay(
				TAG,
				videoConfig!!.width,
				videoConfig!!.height,
				videoConfig!!.densityDpi,
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
	}

	private fun setUpAudioEncoder() {
		if (audioConfig != null) {
			minBytes = AudioRecord.getMinBufferSize(
					audioConfig!!.sampleRate,
					if (audioConfig!!.channelCount == 1) {
						AudioFormat.CHANNEL_IN_MONO
					} else {
						AudioFormat.CHANNEL_IN_STEREO
					},
					AudioFormat.ENCODING_PCM_16BIT
			)
			if (minBytes <= 0) {
				stateChangeListener?.onError(ErrorCode.AUDIO_MIN_BUFFER_SIZE_ERROR, "Check audio sample_rate or channel count")
				stop()
				return
			}

			audioBufferPool = ByteBufferPool(5, minBytes)

			if (usingMic) {
				micRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
						audioConfig!!.sampleRate,
						AudioFormat.CHANNEL_IN_MONO,
						AudioFormat.ENCODING_PCM_16BIT,
						minBytes)

				if (micRecord?.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
					stateChangeListener?.onError(ErrorCode.MIC_IN_USE, "Check If Other app is already using mic")
					stop()
					return
				}

				if (micRecord == null) {
					stateChangeListener?.onError(ErrorCode.AUDIO_RECORD_ERROR, "Check audio sample_rate or channel count")
					stop()
					return
				}

				if (micRecord?.state == AudioRecord.STATE_UNINITIALIZED) {
					stateChangeListener?.onError(ErrorCode.AUDIO_RECORD_ERROR, "Check audio sample_rate or channel count")
					stop()
					return
				}
				micRecord?.startRecording()

				if (micRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
					stateChangeListener?.onError(ErrorCode.MIC_IN_USE, "Check If Other app is already using mic")
					stop()
					return
				}
			}

			audioEncoder = audioConfig?.createMediaCodec()
			if (audioEncoder == null) {
				stateChangeListener?.onError(ErrorCode.AUDIO_ENCODER_ERROR, "Failed to create audio media codec")
			}
			audioEncoder?.configure(audioConfig?.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
			audioEncoder?.setCallback(object : MediaCodec.Callback() {
				override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
					info?.let {
						Log.e(TAG, "onOutputBufferAvailable")
						muxAudio(index, it)
					}
				}

				override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
					codec?.let {
						if (usingMic) {
							queueMicToInputBuffer(codec, index)
						} else {
							queuePCMToInputBuffer(codec, index)
						}
					}
				}

				override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
					Log.e(TAG, "audio onOutputFormatChanged")
					audioOutputFormat = format
					startMuxingIfReady()
				}

				override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
					stateChangeListener?.onError(ErrorCode.AUDIO_ENCODER_ERROR, e.toString())
				}
			})
			audioEncoder?.start()
		}
	}

	val lock = ReentrantLock()
	private fun queuePCMToInputBuffer(codec: MediaCodec?, index: Int) {
		Log.e(TAG, "queuePCMToInputBuffer")
		lock.lock()
		pendingAudioInputBuffer.push(Pair(index, codec))


		while (pendingAudioInputBuffer.isNotEmpty()) {
			var inputIndexWithbuffer: Pair<Int, MediaCodec?> = pendingAudioInputBuffer.pollLast()

			val index = inputIndexWithbuffer.first
			val codec = inputIndexWithbuffer.second
			val inputBuffer = codec?.getInputBuffer(index)
			val audioBuffer = audioBufferPool?.get()



			audioBuffer?.let {
				val position = it.position()
				inputBuffer?.put(it.array(), 0, position)
				codec?.queueInputBuffer(
						index,
						0,
						position,
						calculateFrameTimestamp(position shl 3),
						MediaCodec.BUFFER_FLAG_KEY_FRAME)
				audioBufferPool?.release(it)

			}

			if (audioBuffer == null) {
				Log.e(TAG, "audio buffer is null")
				pendingAudioInputBuffer.push(Pair(index, codec!!))
				break
			}
		}
		lock.unlock()
	}


	fun writePCMToPool(message: Message) {
		lock.lock()
		val audioBuffer = message.obj as ByteArray
		val size = message.arg1



		if (getState() == State.RECORDING) {
			audioBufferPool?.put(audioBuffer, size)
		}

		if (pendingAudioInputBuffer.isNotEmpty()) {
			val audioBufferWithIndex = pendingAudioInputBuffer.pollLast()
			val index = audioBufferWithIndex.first
			val codec = audioBufferWithIndex.second
			val inputBuffer = codec?.getInputBuffer(index)
			val audioBuffer = audioBufferPool!!.get()

			audioBuffer?.let {
				val position = it.position()
				inputBuffer?.put(it.array(), 0, position)
				codec?.queueInputBuffer(
						index,
						0,
						position,
						calculateFrameTimestamp(position shl 3),
						MediaCodec.BUFFER_FLAG_KEY_FRAME)
				audioBufferPool?.release(it)
			}
		} else {
			Log.e(TAG, "pendingAudioInputBuffer is empty")
		}
		lock.unlock()
	}

	fun writeAudioBuffer(byteArray: ByteArray, size: Int) {
		handler.obtainMessage(MSG_WRITE_AUDIO, size, -1, byteArray).sendToTarget()
	}

	private fun queueMicToInputBuffer(codec: MediaCodec, index: Int) {
		val inputBuffer = codec.getInputBuffer(index)
		val bufferCount = micRecord?.read(inputBuffer, inputBuffer.limit())
		codec.queueInputBuffer(
				index,
				inputBuffer.position(),
				inputBuffer.limit(),
				calculateFrameTimestamp(bufferCount!! shl 3),
				MediaCodec.BUFFER_FLAG_KEY_FRAME)
	}


	fun startMuxingIfReady() {
		Log.e(TAG, "startMuxingIfReady")
		if ((videoConfig != null && videoOutputFormat == null)) {
			return
		}
		if (audioConfig != null && audioOutputFormat == null) {
			return
		}

		muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

		muxer?.let {
			videoTrackIndex = it.addTrack(videoOutputFormat)

			if (audioConfig != null) {
				audioTrackIndex = it.addTrack(audioOutputFormat)
			}

			muxer?.start()
			setState(State.RECORDING)

			while (pendingAudioOutputBuffer.isEmpty() && pendingVideoOutputBuffer.isEmpty()) {
				return
			}

			var pendingBuffer: Pair<Int, MediaCodec.BufferInfo>? = null

			while ({ pendingBuffer = pendingVideoOutputBuffer.pollLast(); pendingBuffer }() != null) {
				pendingBuffer?.let {
					muxVideo(it.first, it.second)
				}
			}

			if (audioConfig != null) {
				pendingBuffer = null
				while ({ pendingBuffer = pendingAudioOutputBuffer.pollLast(); pendingBuffer }() != null) {
					pendingBuffer?.let {
						muxAudio(it.first, it.second)
					}
				}
			}
		} ?: kotlin.run {
			stateChangeListener?.onError(ErrorCode.MUXER_ERROR, "Media Muxer is null")
		}

	}

	private fun muxAudio(index: Int, bufferInfo: MediaCodec.BufferInfo) {
		if (audioTrackIndex == INVALID_INDEX || getState() != State.RECORDING) {
			pendingAudioOutputBuffer.push(Pair(index, bufferInfo))
			return
		}

		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
			bufferInfo.size = 0
		} else {
			resetAudioPts(bufferInfo)
		}
		muxer?.writeSampleData(audioTrackIndex, audioEncoder?.getOutputBuffer(index), bufferInfo)
		audioEncoder?.releaseOutputBuffer(index, false)
		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
			audioTrackIndex = INVALID_INDEX
		}
	}

	private fun muxVideo(index: Int, bufferInfo: MediaCodec.BufferInfo) {
		if (videoTrackIndex == INVALID_INDEX || getState() != State.RECORDING) {
			pendingVideoOutputBuffer.push(Pair(index, bufferInfo))
			return
		}

		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
			bufferInfo.size = 0
		} else {
			resetVideoPts(bufferInfo)
		}
		muxer?.writeSampleData(videoTrackIndex, videoEncoder?.getOutputBuffer(index), bufferInfo)
		videoEncoder?.releaseOutputBuffer(index, false)

		if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
			videoTrackIndex = INVALID_INDEX
		}
	}

	fun record() {
		handler.sendEmptyMessage(MSG_START)
	}

	private fun releaseAudio() {
		audioEncoder?.stop()
		audioEncoder?.release()
		micRecord?.stop()
		micRecord?.release()
		audioConfig = null
		audioEncoder = null
		micRecord = null
		audioPtsOffset = 0
	}

	private fun releaseVideo() {
		videoEncoder?.stop()
		videoEncoder?.release()
		videoEncoder = null
		videoConfig = null
		videoPtsOffset = 0
	}

	private fun releaseMuxer() {
		if (muxer != null) {
			val eos = MediaCodec.BufferInfo()
			val buffer = ByteBuffer.allocate(0)
			eos.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
			if (audioTrackIndex != INVALID_INDEX) {
				muxer?.writeSampleData(audioTrackIndex, buffer, eos)
			}
			muxer?.writeSampleData(videoTrackIndex, buffer, eos)

			audioTrackIndex = INVALID_INDEX
			videoTrackIndex = INVALID_INDEX

			muxer?.stop()
			muxer?.release()
		}
	}

	fun stop() {
		handler.sendEmptyMessage(MSG_STOP)
	}

	private val LAST_FRAME_ID = -1
	private val framesUsCache = SparseLongArray(2)
	private var videoPtsOffset: Long = 0
	private var audioPtsOffset: Long = 0

	private fun calculateFrameTimestamp(totalBits: Int): Long {
		val samples = totalBits shr 4
		var frameUs = framesUsCache.get(samples, -1)
		if (frameUs == -1L) {
			frameUs = (samples * 1000000 / 44100).toLong()
			framesUsCache.put(samples, frameUs)
		}
		var timeUs = SystemClock.elapsedRealtimeNanos() / 1000

		timeUs -= frameUs

		val lastFrameUs = framesUsCache.get(LAST_FRAME_ID, -1)
		var currentUs = if (lastFrameUs == -1L) {
			timeUs
		} else {
			lastFrameUs
		}
		if (timeUs - currentUs >= frameUs shl 1) {
			currentUs = timeUs
		}
		framesUsCache.put(LAST_FRAME_ID, currentUs + frameUs)
		return currentUs
	}

	private fun resetAudioPts(buffer: MediaCodec.BufferInfo) {
//		Log.e(TAG, "audioPtsOffset = ${audioPtsOffset} , buffer.presentationTimeUS=${buffer.presentationTimeUs}")
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

	@Synchronized
	private fun setState(state: State) {
		this.state = state
		stateChangeListener?.onStateChanged(state)
	}

	@Synchronized
	fun getState(): State {
		return state
	}
}