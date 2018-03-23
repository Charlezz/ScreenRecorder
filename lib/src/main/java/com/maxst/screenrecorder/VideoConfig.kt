package com.maxst.screenrecorder

import android.app.Activity
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.text.TextUtils
import android.util.DisplayMetrics
import java.io.IOException

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by Charles on 2018. 2. 21..
 */
class VideoConfig {
	val TAG = VideoConfig::class.java.simpleName

	val width: Int get
	val height: Int get
	val bitrate: Int
	val frameRate: Int
	val iFrameInterval: Int
	val codecName: String?
	val mimeType: String
	val densityDpi: Int
	val mediaFormat: MediaFormat
		get

	companion object {
		fun getDefaultConfig(activity: Activity) = {
			var dm = DisplayMetrics()
			activity.windowManager.defaultDisplay.getRealMetrics(dm)
			VideoConfig(
					width = dm.widthPixels,
					height = dm.heightPixels,
					densityDpi = dm.densityDpi,
					bitrate = CodecUtil.VideoBitrate.HD,
					frameRate = CodecUtil.FrameRate.FAST,
					iFrameInterval = 1,
					codecName = null,
					mimeType = MediaFormat.MIMETYPE_VIDEO_AVC)
		}()


	}

	constructor(
			width: Int,
			height: Int,
			densityDpi: Int,
			bitrate: Int,
			frameRate: Int,
			iFrameInterval: Int,
			codecName: String?,
			mimeType: String
	) {
		this.width = width
		this.height = height
		this.densityDpi = densityDpi
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