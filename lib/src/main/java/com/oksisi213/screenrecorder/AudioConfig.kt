package com.oksisi213.screenrecorder

import android.media.MediaCodec
import android.media.MediaFormat
import android.text.TextUtils
import android.util.Log
import java.io.IOException
import java.util.*

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 21..
 */

class AudioConfig {
	val TAG = AudioConfig::class.java.simpleName

	companion object {
		fun getDefaultConfig() = AudioConfig(
				codecName = CodecUtil.findAudioEncoderList(MediaFormat.MIMETYPE_AUDIO_AAC)[0].name,
				mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
				bitrate = 80000,
				sampleRate = 44100,
				channelCount = 1
		)
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
					Log.e(TAG, "No codec name, will create encorder by type")
					throw IOException("No codec name, will create encorder by type")
				}
			} catch (e: IOException) {
				e.printStackTrace()
				MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
			}
}