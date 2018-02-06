package com.oksisi213.screenrecorder

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaFormat

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 5..
 */
class MyScreenRecorder private constructor() {

	class Builder(context: Context) {
		private var width = CodecUtil.Resolution.HD.width
		private var height = CodecUtil.Resolution.HD.height
		private var orientation = CodecUtil.Orientation.PORTRAIT
		private var bitrate = CodecUtil.VideoBitrate.HD
		private var framerate = CodecUtil.FrameRate.FAST
		private var iFrameInterval = 1
		private var mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
		private var videoCodecInfo: String? = CodecUtil.findVideoEncoderList(MediaFormat.MIMETYPE_VIDEO_AVC).let {
			if (it.isEmpty()) null else it[0].name
		}
		private var videoCodecProfileLevel: MediaCodecInfo.CodecProfileLevel = MediaCodecInfo.CodecProfileLevel().apply {
		}

		fun setResolution(width: Int, height: Int): Builder {
			this.width = width
			this.height = height
			return this
		}

		fun setVideoBitrate(bitrate: Int): Builder {
			this.bitrate = bitrate
			return this
		}

		fun setFramerate(framerate: Int): Builder {
			this.framerate = framerate
			return this
		}

		fun setIFrameInterval(iFrameInterval: Int): Builder {
			this.iFrameInterval = iFrameInterval
			return this
		}

		fun setVideoMimeType(mimeType: String): Builder {
			this.mimeType = mimeType
			return this
		}

		fun setVideoCodec(videoCodecInfo: MediaCodecInfo): Builder {
			this.videoCodecInfo = videoCodecInfo.name
			videoCodecInfo.getCapabilitiesForType(mimeType)
			return this
		}

		fun setVideoCodec(codecName: String): Builder {
			this.videoCodecInfo = codecName
			return this
		}


		fun build() {
			MediaFormat.createVideoFormat(mimeType, width, height).apply {
				setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
				setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
				setInteger(MediaFormat.KEY_FRAME_RATE, framerate)
				setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
				setInteger()
			}

		}
	}

}