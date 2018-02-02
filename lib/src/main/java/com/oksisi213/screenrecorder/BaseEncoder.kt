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

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * @author yrom
 * @version 2017/12/4
 */
 abstract class BaseEncoder : Encoder {

	val encoder: MediaCodec
		get() = Objects.requireNonNull<MediaCodec>(mEncoder, "doesn't prepare()")

	private var mCodecName: String? = null
	private var mEncoder: MediaCodec? = null
	private var mCallback: Callback? = null
	/**
	 * let media codec run async mode if mCallback != null
	 */
	private val mCodecCallback = object : MediaCodec.Callback() {
		override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
			mCallback!!.onInputBufferAvailable(this@BaseEncoder, index)
		}

		override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
			mCallback!!.onOutputBufferAvailable(this@BaseEncoder, index, info)
		}

		override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
			mCallback!!.onError(this@BaseEncoder, e)
		}

		override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
			mCallback!!.onOutputFormatChanged(this@BaseEncoder, format)
		}
	}

	public open abstract class Callback : Encoder.Callback {
		open fun onInputBufferAvailable(encoder: BaseEncoder, index: Int) {}

		open fun onOutputFormatChanged(encoder: BaseEncoder, format: MediaFormat) {}

		open fun onOutputBufferAvailable(encoder: BaseEncoder, index: Int, info: MediaCodec.BufferInfo) {}
	}

	constructor(codecName: String) {
		this.mCodecName = codecName
	}

	override fun setCallback(callback: Encoder.Callback) {
		if (callback !is Callback) {
			throw IllegalArgumentException()
		}
		this.setCallback(callback)
	}

	fun setCallback(callback: Callback) {
		if (this.mEncoder != null) throw IllegalStateException("mEncoder is not null")
		this.mCallback = callback
	}

	/**
	 * Must call in a worker handler thread!
	 */
	@Throws(IOException::class)
	override fun prepare() {
		if (Looper.myLooper() == null || Looper.myLooper() == Looper.getMainLooper()) {
			throw IllegalStateException("should run in a HandlerThread")
		}
		if (mEncoder != null) {
			throw IllegalStateException("prepared!")
		}
		val format = createMediaFormat()
		Log.d("Encoder", "Create media format: " + format)

		val mimeType = format.getString(MediaFormat.KEY_MIME)
		val encoder = createEncoder(mimeType)
		try {
			encoder.setCallback(if (this.mCallback == null) null else mCodecCallback)
			encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
			onEncoderConfigured(encoder)
			encoder.start()
		} catch (e: MediaCodec.CodecException) {
			Log.e("Encoder", "Configure codec failure!\n  with format$format", e)
			throw e
		}

		mEncoder = encoder
	}

	/**
	 * call immediately after [MediaCodec][.getEncoder]
	 * configure with [MediaFormat][.createMediaFormat] success
	 *
	 * @param encoder
	 */
	protected open fun onEncoderConfigured(encoder: MediaCodec) {}

	/**
	 * create a new instance of MediaCodec
	 */
	@Throws(IOException::class)
	private fun createEncoder(type: String): MediaCodec {
		try {
			// use codec name first
			if (this.mCodecName != null) {
				return MediaCodec.createByCodecName(mCodecName)
			}
		} catch (e: IOException) {
			Log.w("@@", "Create MediaCodec by name '$mCodecName' failure!", e)
		}

		return MediaCodec.createEncoderByType(type)
	}

	/**
	 * create [MediaFormat] for [MediaCodec]
	 */
	protected abstract fun createMediaFormat(): MediaFormat

	/**
	 * @throws NullPointerException if prepare() not call
	 * @see MediaCodec.getOutputBuffer
	 */
	fun getOutputBuffer(index: Int): ByteBuffer? {
		return encoder.getOutputBuffer(index)
	}

	/**
	 * @throws NullPointerException if prepare() not call
	 * @see MediaCodec.getInputBuffer
	 */
	fun getInputBuffer(index: Int): ByteBuffer? {
		return encoder.getInputBuffer(index)
	}

	/**
	 * @throws NullPointerException if prepare() not call
	 * @see MediaCodec.queueInputBuffer
	 * @see MediaCodec.getInputBuffer
	 */
	fun queueInputBuffer(index: Int, offset: Int, size: Int, pstTs: Long, flags: Int) {
		encoder.queueInputBuffer(index, offset, size, pstTs, flags)
	}

	/**
	 * @throws NullPointerException if prepare() not call
	 * @see MediaCodec.releaseOutputBuffer
	 */
	fun releaseOutputBuffer(index: Int) {
		encoder.releaseOutputBuffer(index, false)
	}

	/**
	 * @see MediaCodec.stop
	 */
	override fun stop() {
		if (mEncoder != null) {
			mEncoder!!.stop()
		}
	}

	/**
	 * @see MediaCodec.release
	 */
	override fun release() {
		if (mEncoder != null) {
			mEncoder!!.release()
			mEncoder = null
		}
	}


}
