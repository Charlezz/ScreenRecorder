package com.oksisi213.screenrecorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.text.TextUtils
import java.io.IOException

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 21..
 */
class VideoConfig {
	val TAG = VideoConfig::class.java.simpleName

	val width: Int get
	val height: Int get
	private val bitrate: Int
	private val frameRate: Int
	private val iFrameInterval: Int
	private val codecName: String?
	private val mimeType: String
	val mediaFormat: MediaFormat
		get

	companion object {
		fun getDefaultConfig() = VideoConfig(
				width = CodecUtil.Resolution.HD.height,
				height = CodecUtil.Resolution.HD.width,
				bitrate = CodecUtil.VideoBitrate.HD,
				frameRate = CodecUtil.FrameRate.FAST,
				iFrameInterval = 1,
				codecName = null,
				mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
		)

	}

	constructor(
			width: Int,
			height: Int,
			bitrate: Int,
			frameRate: Int,
			iFrameInterval: Int,
			codecName: String?,
			mimeType: String
	) {
		this.width = width
		this.height = height
		this.bitrate = bitrate
		this.frameRate = frameRate
		this.iFrameInterval = iFrameInterval
		this.codecName = codecName
		this.mimeType = mimeType
		mediaFormat = createMediaFormat()
	}

	fun createMediaFormat(): MediaFormat =
			MediaFormat.createVideoFormat(mimeType, width, height).apply {
				setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
				setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
				setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
				setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
//				if (codecProfileLevel != null && codecProfileLevel.profile != 0 && codecProfileLevel.level != 0) {
//					setInteger(MediaFormat.KEY_PROFILE, codecProfileLevel.profile)
//					setInteger("level", codecProfileLevel.level)
//				}
			}

	fun createMediaCodec(): MediaCodec =
			try {
				if (!TextUtils.isEmpty(codecName)) {
					MediaCodec.createByCodecName(codecName)
				} else {
					throw IOException("No codec name, will create encorder by type")
				}
			} catch (e: IOException) {
				MediaCodec.createEncoderByType(mediaFormat.getString(MediaFormat.KEY_MIME))
			}
}