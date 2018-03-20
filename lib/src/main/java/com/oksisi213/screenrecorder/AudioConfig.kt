package com.oksisi213.screenrecorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.text.TextUtils
import java.io.IOException
import java.util.*


/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 21..
 */

class AudioConfig {

	companion object {
		val TAG = AudioConfig::class.java.simpleName
		fun getDefaultConfig(): AudioConfig {
			val mimeType = MediaFormat.MIMETYPE_AUDIO_AAC
			val mediaCodecInfo = CodecUtil.findAudioEncoderList(mimeType)[0]
			val codecName = mediaCodecInfo.name
			val bitrate = CodecUtil.getAudioBitrateRange(mediaCodecInfo, mimeType).let {
				val preferred = 80000
				return@let it.clamp(preferred)
			}
			val sampleRate = CodecUtil.getAudioSampleRates(mediaCodecInfo).let {
				val preferred = 44100
				var diff = Integer.MAX_VALUE
				return@let it.lastOrNull { diff > Math.abs(preferred - it) } ?: preferred
			}
			val channelCount = 1
			return AudioConfig(codecName, mimeType, bitrate, sampleRate, channelCount)
		}
	}

	val codecName: String? get
	val mimeType: String get
	val bitrate: Int get
	val sampleRate: Int get
	val channelCount: Int get

	val mediaFormat: MediaFormat get

	constructor(
			codecName: String?,
			mimeType: String,
			bitrate: Int,
			sampleRate: Int,
			channelCount: Int
	) {
		this.codecName = codecName
		this.mimeType = Objects.requireNonNull(mimeType)
		this.bitrate = bitrate
		this.sampleRate = sampleRate
		this.channelCount = channelCount

		this.mediaFormat = createMediaFormat()
	}


	override fun toString(): String {
		return "AudioEncodeConfig{" +
				"codecName='" + codecName + '\''.toString() +
				", mimeType='" + mimeType + '\''.toString() +
				", bitRate=" + bitrate +
				", sampleRate=" + sampleRate +
				", channelCount=" + channelCount +
				'}'.toString()
	}

	fun createMediaFormat(): MediaFormat =
			MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount).apply {
				//				setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
				setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
			}

	fun createMediaCodec(): MediaCodec =
			try {
				if (!TextUtils.isEmpty(codecName)) {
					MediaCodec.createByCodecName(codecName)
				} else {
					throw IOException("No codec name, will create encorder by type")
				}
			} catch (e: IOException) {
				e.printStackTrace()
				MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
			}
}