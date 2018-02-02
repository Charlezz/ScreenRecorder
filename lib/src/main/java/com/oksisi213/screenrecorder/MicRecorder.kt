/*
 * Copyright (c) 2017 Yrom Wang <http://www.yrom.net>
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

import android.media.*
import android.media.MediaCodec.*
import android.os.*
import android.os.Build.VERSION_CODES.N
import android.util.Log
import android.util.SparseLongArray
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author yrom
 * @version 2017/12/4
 */
internal class MicRecorder(config: AudioEncodeConfig) : Encoder {

	private val mEncoder: AudioEncoder
	private val mRecordThread: HandlerThread
	private var mRecordHandler: RecordHandler? = null
	private var mMic: AudioRecord? = null // access in mRecordThread only!
	private val mSampleRate: Int
	private val mChannelConfig: Int
	private val mFormat = AudioFormat.ENCODING_PCM_16BIT

	private val mForceStop = AtomicBoolean(false)
	private var mCallback: BaseEncoder.Callback? = null
	private var mCallbackDelegate: CallbackDelegate? = null
	private val mChannelsSampleRate: Int
	private val mFramesUsCache = SparseLongArray(2)

	init {
		mEncoder = AudioEncoder(config)
		mSampleRate = config.sampleRate
		mChannelsSampleRate = mSampleRate * config.channelCount
		if (VERBOSE) Log.i(TAG, "in bitrate " + mChannelsSampleRate * 16 /* PCM_16BIT*/)
		mChannelConfig = if (config.channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
		mRecordThread = HandlerThread(TAG)
	}

	override fun setCallback(callback: Encoder.Callback) {
		this.mCallback = callback as BaseEncoder.Callback
	}

	fun setCallback(callback: BaseEncoder.Callback) {
		this.mCallback = callback
	}

	@Throws(IOException::class)
	override fun prepare() {
		val myLooper = Objects.requireNonNull(Looper.myLooper(), "Should prepare in HandlerThread")
		// run callback in caller thread
		mCallbackDelegate = CallbackDelegate(myLooper, mCallback)
		mRecordThread.start()
		mRecordHandler = RecordHandler(mRecordThread.looper)
		mRecordHandler!!.sendEmptyMessage(MSG_PREPARE)
	}

	override fun stop() {
		// clear callback queue
		mCallbackDelegate!!.removeCallbacksAndMessages(null)
		mForceStop.set(true)
		if (mRecordHandler != null) mRecordHandler!!.sendEmptyMessage(MSG_STOP)
	}

	override fun release() {
		if (mRecordHandler != null) mRecordHandler!!.sendEmptyMessage(MSG_RELEASE)
		mRecordThread.quitSafely()
	}

	fun releaseOutputBuffer(index: Int) {
		if (VERBOSE) Log.d(TAG, "audio encoder released output buffer index=" + index)
		Message.obtain(mRecordHandler, MSG_RELEASE_OUTPUT, index, 0).sendToTarget()
	}


	fun getOutputBuffer(index: Int): ByteBuffer? {
		return mEncoder.getOutputBuffer(index)
	}


	private class CallbackDelegate internal constructor(l: Looper, private val mCallback: BaseEncoder.Callback?) : Handler(l) {


		internal fun onError(encoder: Encoder, exception: Exception) {
			Message.obtain(this) {
				mCallback?.onError(encoder, exception)
			}.sendToTarget()
		}

		internal fun onOutputFormatChanged(encoder: BaseEncoder, format: MediaFormat) {
			Message.obtain(this) {
				mCallback?.onOutputFormatChanged(encoder, format)
			}.sendToTarget()
		}

		internal fun onOutputBufferAvailable(encoder: BaseEncoder, index: Int, info: MediaCodec.BufferInfo) {
			Message.obtain(this) {
				mCallback?.onOutputBufferAvailable(encoder, index, info)
			}.sendToTarget()
		}

	}

	private inner class RecordHandler internal constructor(l: Looper) : Handler(l) {

		private val mCachedInfos = LinkedList<MediaCodec.BufferInfo>()
		private val mMuxingOutputBufferIndices = LinkedList<Int>()
		private val mPollRate = 2048000 / mSampleRate // poll per 2048 samples

		override fun handleMessage(msg: Message) {
			when (msg.what) {
				MSG_PREPARE -> {
					val r = createAudioRecord(mSampleRate, mChannelConfig, mFormat)
					if (r == null) {
						Log.e(TAG, "create audio record failure")
						mCallbackDelegate!!.onError(this@MicRecorder, IllegalArgumentException())
						return
					} else {
						r.startRecording()
						mMic = r
					}
					try {
						mEncoder.prepare()
					} catch (e: Exception) {
						mCallbackDelegate!!.onError(this@MicRecorder, e)
						return
					}

					if (!mForceStop.get()) {
						val index = pollInput()
						if (VERBOSE)
							Log.d(TAG, "audio encoder returned input buffer index=" + index)
						if (index >= 0) {
							feedAudioEncoder(index)
							// tell encoder to eat the fresh meat!
							if (!mForceStop.get()) sendEmptyMessage(MSG_DRAIN_OUTPUT)
						} else {
							// try later...
							if (VERBOSE) Log.i(TAG, "try later to poll input buffer")
							sendEmptyMessageDelayed(MSG_FEED_INPUT, mPollRate.toLong())
						}
					}
				}
				MSG_FEED_INPUT -> if (!mForceStop.get()) {
					val index = pollInput()
					if (VERBOSE)
						Log.d(TAG, "audio encoder returned input buffer index=" + index)
					if (index >= 0) {
						feedAudioEncoder(index)
						if (!mForceStop.get()) sendEmptyMessage(MSG_DRAIN_OUTPUT)
					} else {
						if (VERBOSE) Log.i(TAG, "try later to poll input buffer")
						sendEmptyMessageDelayed(MSG_FEED_INPUT, mPollRate.toLong())
					}
				}
				MSG_DRAIN_OUTPUT -> {
					offerOutput()
					pollInputIfNeed()
				}
				MSG_RELEASE_OUTPUT -> {
					mEncoder.releaseOutputBuffer(msg.arg1)
					mMuxingOutputBufferIndices.poll() // Nobody care what it exactly is.
					if (VERBOSE)
						Log.d(TAG, "audio encoder released output buffer index="
								+ msg.arg1 + ", remaining=" + mMuxingOutputBufferIndices.size)
					pollInputIfNeed()
				}
				MSG_STOP -> {
					if (mMic != null) {
						mMic!!.stop()
					}
					mEncoder.stop()
				}
				MSG_RELEASE -> {
					if (mMic != null) {
						mMic!!.release()
						mMic = null
					}
					mEncoder.release()
				}
			}
		}

		private fun offerOutput() {
			while (!mForceStop.get()) {
				var info: MediaCodec.BufferInfo? = mCachedInfos.poll()
				if (info == null) {
					info = MediaCodec.BufferInfo()
				}
				val index = mEncoder.encoder.dequeueOutputBuffer(info, 1)
				if (VERBOSE) Log.d(TAG, "audio encoder returned output buffer index=" + index)
				if (index == INFO_OUTPUT_FORMAT_CHANGED) {
					mCallbackDelegate!!.onOutputFormatChanged(mEncoder, mEncoder.encoder.outputFormat)
				}
				if (index < 0) {
					info.set(0, 0, 0, 0)
					mCachedInfos.offer(info)
					break
				}
				mMuxingOutputBufferIndices.offer(index)
				mCallbackDelegate!!.onOutputBufferAvailable(mEncoder, index, info)

			}
		}

		private fun pollInput(): Int {
			return mEncoder.encoder.dequeueInputBuffer(0)
		}

		private fun pollInputIfNeed() {
			if (mMuxingOutputBufferIndices.size <= 1 && !mForceStop.get()) {
				// need fresh data, right now!
				removeMessages(MSG_FEED_INPUT)
				sendEmptyMessageDelayed(MSG_FEED_INPUT, 0)
			}
		}
	}

	/**
	 * NOTE: Should waiting all output buffer disappear queue input buffer
	 */
	private fun feedAudioEncoder(index: Int) {
		if (index < 0 || mForceStop.get()) return
		val r = Objects.requireNonNull<AudioRecord>(mMic, "maybe release")
		val eos = r.recordingState == AudioRecord.RECORDSTATE_STOPPED
		val frame = mEncoder.getInputBuffer(index)
		val offset = frame?.position()
		val limit = frame?.limit()
		var read = 0
		if (!eos) {
			read = r.read(frame, limit!!)
			if (VERBOSE)
				Log.d(TAG, "Read frame data size " + read + " for index "
						+ index + " buffer : " + offset + ", " + limit)
			if (read < 0) {
				read = 0
			}
		}

		val pstTs = calculateFrameTimestamp(read shl 3)
		var flags = BUFFER_FLAG_KEY_FRAME

		if (eos) {
			flags = BUFFER_FLAG_END_OF_STREAM
		}
		// feed frame to encoder
		if (VERBOSE)
			Log.d(TAG, "Feed codec index=" + index + ", presentationTimeUs="
					+ pstTs + ", flags=" + flags)
		mEncoder.queueInputBuffer(index, offset!!, read, pstTs, flags)
	}

	/**
	 * Gets presentation time (us) of polled frame.
	 * 1 sample = 16 bit
	 */
	private fun calculateFrameTimestamp(totalBits: Int): Long {
		val samples = totalBits shr 4
		var frameUs = mFramesUsCache.get(samples, -1)
		if (frameUs == -1L) {
			frameUs = (samples * 1000000 / mChannelsSampleRate).toLong()
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
		if (VERBOSE)
			Log.i(TAG, "count samples pts: $currentUs, time pts: $timeUs, samples: $samples")
		// maybe too late to acquire sample data
		if (timeUs - currentUs >= frameUs shl 1) {
			// reset
			currentUs = timeUs
		}
		mFramesUsCache.put(LAST_FRAME_ID, currentUs + frameUs)
		return currentUs
	}

	companion object {
		private val TAG = "MicRecorder"
		private val VERBOSE = false

		private val MSG_PREPARE = 0
		private val MSG_FEED_INPUT = 1
		private val MSG_DRAIN_OUTPUT = 2
		private val MSG_RELEASE_OUTPUT = 3
		private val MSG_STOP = 4
		private val MSG_RELEASE = 5


		private val LAST_FRAME_ID = -1

		private fun createAudioRecord(sampleRateInHz: Int, channelConfig: Int, audioFormat: Int): AudioRecord? {
			val minBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
			if (minBytes <= 0) {
				Log.e(TAG, String.format(Locale.US, "Bad arguments: getMinBufferSize(%d, %d, %d)",
						sampleRateInHz, channelConfig, audioFormat))
				return null
			}
			val record = AudioRecord(MediaRecorder.AudioSource.MIC,
					sampleRateInHz,
					channelConfig,
					audioFormat,
					minBytes * 2)

			if (record.state == AudioRecord.STATE_UNINITIALIZED) {
				Log.e(TAG, String.format(Locale.US, "Bad arguments to new AudioRecord %d, %d, %d",
						sampleRateInHz, channelConfig, audioFormat))
				return null
			}
			if (VERBOSE) {
				Log.i(TAG, "created AudioRecord $record, MinBufferSize= $minBytes")
				if (Build.VERSION.SDK_INT >= N) {
					Log.d(TAG, " size in frame " + record.bufferSizeInFrames)
				}
			}
			return record
		}
	}

}
