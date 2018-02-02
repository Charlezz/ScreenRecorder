/*
 * Copyright (c) 2014 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oksisi213.screenrecorder

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaFormat.MIMETYPE_AUDIO_AAC
import android.media.MediaFormat.MIMETYPE_VIDEO_AVC
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Yrom
 */
class ScreenRecorder
/**
 * @param dpi for [VirtualDisplay]
 */
(video: VideoEncodeConfig,
 audio: AudioEncodeConfig?,
 private val mDpi: Int, private var mMediaProjection: MediaProjection?,
 val savedPath: String) {
	private val mWidth: Int
	private val mHeight: Int
	private var mVideoEncoder: VideoEncoder? = null
	private var mAudioEncoder: MicRecorder? = null

	private var mVideoOutputFormat: MediaFormat? = null
	private var mAudioOutputFormat: MediaFormat? = null
	private var mVideoTrackIndex = INVALID_INDEX
	private var mAudioTrackIndex = INVALID_INDEX
	private var mMuxer: MediaMuxer? = null
	private var mMuxerStarted = false

	private val mForceQuit = AtomicBoolean(false)
	private val mIsRunning = AtomicBoolean(false)
	private var mVirtualDisplay: VirtualDisplay? = null
	private val mProjectionCallback = object : MediaProjection.Callback() {
		override fun onStop() {
			quit()
		}
	}

	private var mWorker: HandlerThread? = null
	private var mHandler: CallbackHandler? = null

	private var mCallback: Callback? = null
	private val mPendingVideoEncoderBufferIndices = LinkedList<Int>()
	private val mPendingAudioEncoderBufferIndices = LinkedList<Int>()
	private val mPendingAudioEncoderBufferInfos = LinkedList<MediaCodec.BufferInfo>()
	private val mPendingVideoEncoderBufferInfos = LinkedList<MediaCodec.BufferInfo>()

	private var mVideoPtsOffset: Long = 0
	private var mAudioPtsOffset: Long = 0

	init {
		mWidth = video.width
		mHeight = video.height
		mVideoEncoder = VideoEncoder(video)
		mAudioEncoder = if (audio == null) null else MicRecorder(audio)

	}

	/**
	 * stop task
	 */
	fun quit() {
		Log.e(TAG, "quit")
		mForceQuit.set(true)
		if (!mIsRunning.get()) {
			release()
		} else {
			signalStop(false)
		}

	}

	fun start() {
		Log.e(TAG, "start")
		if (mWorker != null) {
			throw IllegalStateException()
		}
		mWorker = HandlerThread(TAG)
		mWorker!!.start()
		mHandler = CallbackHandler(mWorker!!.looper)
		mHandler!!.sendEmptyMessage(MSG_START)
	}

	fun setCallback(callback: Callback) {
		mCallback = callback
	}

	interface Callback {
		fun onStop(error: Throwable)

		fun onStart()

		fun onRecording(presentationTimeUs: Long)
	}

	private inner class CallbackHandler internal constructor(looper: Looper) : Handler(looper) {

		override fun handleMessage(msg: Message) {
			when (msg.what) {
				MSG_START -> {
					try {
						record()
						if (mCallback != null) {
							mCallback!!.onStart()
						}
						return
					} catch (e: Exception) {
						msg.obj = e
					}

					stopEncoders()
					if (msg.arg1 != STOP_WITH_EOS) signalEndOfStream()
					if (mCallback != null) {
						mCallback!!.onStop(msg.obj as Throwable)
					}
					release()
				}
				MSG_STOP, MSG_ERROR -> {
					stopEncoders()
					if (msg.arg1 != STOP_WITH_EOS) signalEndOfStream()
					if (mCallback != null) {
						if (msg.obj != null) {
							mCallback!!.onStop(msg.obj as Throwable)
						}

					}
					release()
				}
			}
		}
	}

	private fun signalEndOfStream() {
		val eos = MediaCodec.BufferInfo()
		val buffer = ByteBuffer.allocate(0)
		eos.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
		if (VERBOSE) Log.i(TAG, "Signal EOS to muxer ")
		if (mVideoTrackIndex != INVALID_INDEX) {
			writeSampleData(mVideoTrackIndex, eos, buffer)
		}
		if (mAudioTrackIndex != INVALID_INDEX) {
			writeSampleData(mAudioTrackIndex, eos, buffer)
		}
		mVideoTrackIndex = INVALID_INDEX
		mAudioTrackIndex = INVALID_INDEX
	}

	private fun record() {
		if (mIsRunning.get() || mForceQuit.get()) {
			throw IllegalStateException()
		}
		if (mMediaProjection == null) {
			throw IllegalStateException("maybe release")
		}
		mIsRunning.set(true)

		mMediaProjection!!.registerCallback(mProjectionCallback, mHandler)
		try {
			// create muxer
			mMuxer = MediaMuxer(savedPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
			// create encoder and input surface
			prepareVideoEncoder()
			prepareAudioEncoder()
		} catch (e: IOException) {
			throw RuntimeException(e)
		}

		mVirtualDisplay = mMediaProjection!!.createVirtualDisplay(TAG + "-display",
				mWidth, mHeight, mDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
				mVideoEncoder!!.inputSurface, null, null)
		if (VERBOSE) Log.d(TAG, "created virtual display: " + mVirtualDisplay!!.display)
	}

	private fun muxVideo(index: Int, buffer: MediaCodec.BufferInfo) {
		if (!mIsRunning.get()) {
			Log.w(TAG, "muxVideo: Already stopped!")
			return
		}
		if (!mMuxerStarted || mVideoTrackIndex == INVALID_INDEX) {
			mPendingVideoEncoderBufferIndices.add(index)
			mPendingVideoEncoderBufferInfos.add(buffer)
			return
		}
		val encodedData = mVideoEncoder!!.getOutputBuffer(index)
		writeSampleData(mVideoTrackIndex, buffer, encodedData)
		mVideoEncoder!!.releaseOutputBuffer(index)
		if (buffer.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
			if (VERBOSE)
				Log.d(TAG, "Stop encoder and muxer, since the buffer has been marked with EOS")
			// send release msg
			mVideoTrackIndex = INVALID_INDEX
			signalStop(true)
		}
	}


	private fun muxAudio(index: Int, buffer: MediaCodec.BufferInfo) {
		if (!mIsRunning.get()) {
			Log.w(TAG, "muxAudio: Already stopped!")
			return
		}
		if (!mMuxerStarted || mAudioTrackIndex == INVALID_INDEX) {
			mPendingAudioEncoderBufferIndices.add(index)
			mPendingAudioEncoderBufferInfos.add(buffer)
			return

		}
		val encodedData = mAudioEncoder!!.getOutputBuffer(index)
		writeSampleData(mAudioTrackIndex, buffer, encodedData)
		mAudioEncoder!!.releaseOutputBuffer(index)
		if (buffer.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
			if (VERBOSE)
				Log.d(TAG, "Stop encoder and muxer, since the buffer has been marked with EOS")
			mAudioTrackIndex = INVALID_INDEX
			signalStop(true)
		}
	}

	private fun writeSampleData(track: Int, buffer: MediaCodec.BufferInfo, encodedData: ByteBuffer?) {
		var encodedData = encodedData
		if (buffer.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
			// The codec config data was pulled out and fed to the muxer when we got
			// the INFO_OUTPUT_FORMAT_CHANGED status.
			// Ignore it.
			if (VERBOSE) Log.d(TAG, "Ignoring BUFFER_FLAG_CODEC_CONFIG")
			buffer.size = 0
		}
		val eos = buffer.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
		if (buffer.size == 0 && !eos) {
			if (VERBOSE) Log.d(TAG, "info.size == 0, drop it.")
			encodedData = null
		} else {
			if (buffer.presentationTimeUs != 0L) { // maybe 0 if eos
				if (track == mVideoTrackIndex) {
					resetVideoPts(buffer)
				} else if (track == mAudioTrackIndex) {
					resetAudioPts(buffer)
				}
			}
			if (VERBOSE)
				Log.d(TAG, "[" + Thread.currentThread().id + "] Got buffer, track=" + track
						+ ", info: size=" + buffer.size
						+ ", presentationTimeUs=" + buffer.presentationTimeUs)
			if (!eos && mCallback != null) {
				mCallback!!.onRecording(buffer.presentationTimeUs)
			}
		}
		if (encodedData != null) {
			encodedData.position(buffer.offset)
			encodedData.limit(buffer.offset + buffer.size)
			mMuxer!!.writeSampleData(track, encodedData, buffer)
			if (VERBOSE)
				Log.i(TAG, "Sent " + buffer.size + " bytes to MediaMuxer on track " + track)
		}
	}

	private fun resetAudioPts(buffer: MediaCodec.BufferInfo) {
		if (mAudioPtsOffset == 0L) {
			mAudioPtsOffset = buffer.presentationTimeUs
			buffer.presentationTimeUs = 0
		} else {
			buffer.presentationTimeUs -= mAudioPtsOffset
		}
	}

	private fun resetVideoPts(buffer: MediaCodec.BufferInfo) {
		if (mVideoPtsOffset == 0L) {
			mVideoPtsOffset = buffer.presentationTimeUs
			buffer.presentationTimeUs = 0
		} else {
			buffer.presentationTimeUs -= mVideoPtsOffset
		}
	}

	private fun resetVideoOutputFormat(newFormat: MediaFormat) {
		// should happen before receiving buffers, and should only happen once
		if (mVideoTrackIndex >= 0 || mMuxerStarted) {
			throw IllegalStateException("output format already changed!")
		}
		if (VERBOSE)
			Log.i(TAG, "Video output format changed.\n New format: " + newFormat.toString())
		mVideoOutputFormat = newFormat
	}

	private fun resetAudioOutputFormat(newFormat: MediaFormat) {
		// should happen before receiving buffers, and should only happen once
		if (mAudioTrackIndex >= 0 || mMuxerStarted) {
			throw IllegalStateException("output format already changed!")
		}
		if (VERBOSE)
			Log.i(TAG, "Audio output format changed.\n New format: " + newFormat.toString())
		mAudioOutputFormat = newFormat
	}

	private fun startMuxerIfReady() {
		if (mMuxerStarted || mVideoOutputFormat == null
				|| mAudioEncoder != null && mAudioOutputFormat == null) {
			return
		}

		mVideoTrackIndex = mMuxer!!.addTrack(mVideoOutputFormat!!)
		mAudioTrackIndex = if (mAudioEncoder == null) INVALID_INDEX else mMuxer!!.addTrack(mAudioOutputFormat!!)
		mMuxer!!.start()
		mMuxerStarted = true
		if (VERBOSE) Log.i(TAG, "Started media muxer, videoIndex=" + mVideoTrackIndex)
		if (mPendingVideoEncoderBufferIndices.isEmpty() && mPendingAudioEncoderBufferIndices.isEmpty()) {
			return
		}
		if (VERBOSE) Log.i(TAG, "Mux pending video output buffers...")
		var info: MediaCodec.BufferInfo? = null


		while ({ info = mPendingVideoEncoderBufferInfos.poll();info }() != null) {
			val index = mPendingVideoEncoderBufferIndices.poll()
			muxVideo(index, info!!)
		}
		if (mAudioEncoder != null) {
			while ({ info = mPendingAudioEncoderBufferInfos.poll();info }() != null) {
				val index = mPendingAudioEncoderBufferIndices.poll()
				muxAudio(index, info!!)
			}
		}
		if (VERBOSE) Log.i(TAG, "Mux pending video output buffers done.")
	}

	// @WorkerThread
	@Throws(IOException::class)
	private fun prepareVideoEncoder() {


		val callback = object : BaseEncoder.Callback() {
			internal var ranIntoError = false

			override fun onOutputBufferAvailable(codec: BaseEncoder, index: Int, info: MediaCodec.BufferInfo) {
				if (VERBOSE) Log.i(TAG, "VideoEncoder output buffer available: index=" + index)
				try {
					muxVideo(index, info)
				} catch (e: Exception) {
					Log.e(TAG, "Muxer encountered an error! ", e)
					Message.obtain(mHandler, MSG_ERROR, e).sendToTarget()
				}

			}

			override fun onError(codec: Encoder, e: Exception) {
				ranIntoError = true
				Log.e(TAG, "VideoEncoder ran into an error! ", e)
				Message.obtain(mHandler, MSG_ERROR, e).sendToTarget()
			}

			override fun onOutputFormatChanged(codec: BaseEncoder, format: MediaFormat) {
				resetVideoOutputFormat(format)
				startMuxerIfReady()
			}
		}
		mVideoEncoder!!.setCallback(callback)
		mVideoEncoder!!.prepare()
	}

	@Throws(IOException::class)
	private fun prepareAudioEncoder() {
		val micRecorder = mAudioEncoder ?: return
		val callback = object : BaseEncoder.Callback() {
			internal var ranIntoError = false

			override fun onOutputBufferAvailable(codec: BaseEncoder, index: Int, info: MediaCodec.BufferInfo) {
				if (VERBOSE)
					Log.i(TAG, "[" + Thread.currentThread().id + "] AudioEncoder output buffer available: index=" + index)
				try {
					muxAudio(index, info)
				} catch (e: Exception) {
					Log.e(TAG, "Muxer encountered an error! ", e)
					Message.obtain(mHandler, MSG_ERROR, e).sendToTarget()
				}

			}

			override fun onOutputFormatChanged(codec: BaseEncoder, format: MediaFormat) {
				if (VERBOSE)
					Log.d(TAG, "[" + Thread.currentThread().id + "] AudioEncoder returned new format " + format)
				resetAudioOutputFormat(format)
				startMuxerIfReady()
			}

			override fun onError(codec: Encoder, e: Exception) {
				ranIntoError = true
				Log.e(TAG, "MicRecorder ran into an error! ", e)
				Message.obtain(mHandler, MSG_ERROR, e).sendToTarget()
			}


		}
		micRecorder.setCallback(callback)
		micRecorder.prepare()
	}

	private fun signalStop(stopWithEOS: Boolean) {
		val msg = Message.obtain(mHandler, MSG_STOP, if (stopWithEOS) STOP_WITH_EOS else 0, 0)
		mHandler!!.sendMessageAtFrontOfQueue(msg)
	}

	private fun stopEncoders() {
		mIsRunning.set(false)
		mPendingAudioEncoderBufferInfos.clear()
		mPendingAudioEncoderBufferIndices.clear()
		mPendingVideoEncoderBufferInfos.clear()
		mPendingVideoEncoderBufferIndices.clear()
		// maybe called on an error has been occurred
		try {
			if (mVideoEncoder != null) mVideoEncoder!!.stop()
		} catch (e: IllegalStateException) {
			// ignored
		}

		try {
			if (mAudioEncoder != null) mAudioEncoder!!.stop()
		} catch (e: IllegalStateException) {
			// ignored
		}

	}

	private fun release() {
		Log.e(TAG, "release")
		if (mMediaProjection != null) {
			mMediaProjection!!.unregisterCallback(mProjectionCallback)
		}
		if (mVirtualDisplay != null) {
			mVirtualDisplay!!.release()
			mVirtualDisplay = null
		}

		mAudioOutputFormat = null
		mVideoOutputFormat = mAudioOutputFormat
		mAudioTrackIndex = INVALID_INDEX
		mVideoTrackIndex = mAudioTrackIndex
		mMuxerStarted = false

		if (mWorker != null) {
			mWorker!!.quitSafely()
			mWorker = null
		}
		if (mVideoEncoder != null) {
			mVideoEncoder!!.release()
			mVideoEncoder = null
		}
		if (mAudioEncoder != null) {
			mAudioEncoder!!.release()
			mAudioEncoder = null
		}

		if (mMediaProjection != null) {
			mMediaProjection!!.stop()
			mMediaProjection = null
		}
		if (mMuxer != null) {
			try {
				mMuxer!!.stop()
				mMuxer!!.release()
			} catch (e: Exception) {
				// ignored
			}

			mMuxer = null
		}
		mHandler = null
	}

	@Throws(Throwable::class)
	protected fun finalize() {
		if (mMediaProjection != null) {
			Log.e(TAG, "release() not called!")
			release()
		}
	}

	companion object {
		private val TAG = "ScreenRecorder"
		private val VERBOSE = false
		private val INVALID_INDEX = -1
		internal val VIDEO_AVC = MIMETYPE_VIDEO_AVC // H.264 Advanced Video Coding
		internal val AUDIO_AAC = MIMETYPE_AUDIO_AAC // H.264 Advanced Audio Coding

		private val MSG_START = 0
		private val MSG_STOP = 1
		private val MSG_ERROR = 2
		private val STOP_WITH_EOS = 1
	}

}
