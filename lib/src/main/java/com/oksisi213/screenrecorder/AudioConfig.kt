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
	val TAG = AudioConfig::class.java.simpleName

	companion object {
		fun getDefaultConfig() = AudioConfig(
				codecName = CodecUtil.findAudioEncoderList(MediaFormat.MIMETYPE_AUDIO_AAC)[0].name,
				mimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
				bitrate = 128000,
				sampleRate = 44000,
				channelCount = 2,
				profile = CodecUtil.findVideoEncoderList(MediaFormat.MIMETYPE_AUDIO_AAC)[0]
						.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC)
						.profileLevels[0]
						.profile
		)
	}

	val codecName: String? get
	val mimeType: String get
	val bitrate: Int get
	val sampleRate: Int get
	val channelCount: Int get
	val profile: Int get

	val mediaFormat: MediaFormat get

	constructor(
			codecName: String?,
			mimeType: String,
			bitrate: Int,
			sampleRate: Int,
			channelCount: Int,
			profile: Int
	) {
		this.codecName = codecName
		this.mimeType = Objects.requireNonNull(mimeType)
		this.bitrate = bitrate
		this.sampleRate = sampleRate
		this.channelCount = channelCount
		this.profile = profile

		this.mediaFormat = createMediaFormat()
	}


	override fun toString(): String {
		return "AudioEncodeConfig{" +
				"codecName='" + codecName + '\''.toString() +
				", mimeType='" + mimeType + '\''.toString() +
				", bitRate=" + bitrate +
				", sampleRate=" + sampleRate +
				", channelCount=" + channelCount +
				", profile=" + profile +
				'}'.toString()
	}

	fun createMediaFormat(): MediaFormat =
			MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount).apply {
				setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
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