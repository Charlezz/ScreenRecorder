package com.oksisi213.screenrecorder

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Size

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by Charles on 31/01/2018.
 */

object CodecUtil {
	val TAG = CodecUtil::class.java.simpleName

	fun findVideoEncoderList(mimeType: String): List<MediaCodecInfo> = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter {
		if (it.isEncoder) {
			for (type in it.supportedTypes) {
				if (type.equals(mimeType, true)) {
					return@filter true
				}
			}
		}
		false

	}

	fun findAudioEncoderList(mimeType: String): List<MediaCodecInfo> = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter {
		if (it.isEncoder) {
			for (type in it.supportedTypes) {
				if (type.equals(mimeType, true)) {
					return@filter true
				}
			}
		}
		false
	}

	fun getVideoBitrates(mediaCodecInfo: MediaCodecInfo, mimeType: String, maxCount: Int): ArrayList<Int> {
		//warning: out of memory
		val bitrateRange = mediaCodecInfo.getCapabilitiesForType(mimeType).videoCapabilities.bitrateRange

		val count = bitrateRange.upper / bitrateRange.lower
		val bitrateList = ArrayList<Int>()
		if (count > maxCount) {
			val properStep = (bitrateRange.upper - bitrateRange.lower) / maxCount
			bitrateList += bitrateRange.upper downTo bitrateRange.lower step properStep
			bitrateList += bitrateRange.lower
		} else {
			bitrateList += bitrateRange.upper downTo bitrateRange.lower step bitrateRange.lower
			bitrateList += bitrateRange.lower
		}

		return bitrateList
	}

	fun getAudioBitrates(mediaCodecInfo: MediaCodecInfo, mimeType: String): ArrayList<Int> {
		val bitrateRange = mediaCodecInfo.getCapabilitiesForType(mimeType).audioCapabilities.bitrateRange
		val bitrateList = ArrayList<Int>()
		bitrateList += bitrateRange.upper downTo bitrateRange.lower step bitrateRange.lower
		bitrateList += bitrateRange.lower
		return bitrateList
	}

	fun getAudioSampleRates(mediaCodecInfo: MediaCodecInfo) {
		mediaCodecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC)
	}

	fun getAVCProfileName(profile: Int) =
			when (profile) {
				MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "AVCProfileMain"
				MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "AVCProfileBaseline"
				MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "AVCProfileExtended"
				MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "AVCProfileHigh"
				MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "AVCProfileHigh10"
				MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "AVCProfileHigh422"
				MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "AVCProfileHigh444"
				else -> "Profile:${profile.toString()}"
			}

	fun getAVCProfileLevel(level: Int) =
			when (level) {
				MediaCodecInfo.CodecProfileLevel.AVCLevel1 -> "AVCLevel1"
				MediaCodecInfo.CodecProfileLevel.AVCLevel2 -> "AVCLevel2"
				MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> "AVCLevel3"
				MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> "AVCLevel4"
				MediaCodecInfo.CodecProfileLevel.AVCLevel21 -> "AVCLevel21"
				MediaCodecInfo.CodecProfileLevel.AVCLevel22 -> "AVCLevel22"
				MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> "AVCLevel3"
				MediaCodecInfo.CodecProfileLevel.AVCLevel31 -> "AVCLevel31"
				MediaCodecInfo.CodecProfileLevel.AVCLevel32 -> "AVCLevel32"
				MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> "AVCLevel4"
				MediaCodecInfo.CodecProfileLevel.AVCLevel41 -> "AVCLevel41"
				MediaCodecInfo.CodecProfileLevel.AVCLevel42 -> "AVCLevel42"
				MediaCodecInfo.CodecProfileLevel.AVCLevel5 -> "AVCLevel5"
				MediaCodecInfo.CodecProfileLevel.AVCLevel51 -> "AVCLevel51"
				MediaCodecInfo.CodecProfileLevel.AVCLevel52 -> "AVCLevel52"
				else -> "Level:${level}"
			}

	fun getAACProfileName(profile: Int) =
			when (profile) {
				MediaCodecInfo.CodecProfileLevel.AACObjectMain -> "AACObjectMain"
				MediaCodecInfo.CodecProfileLevel.AACObjectELD -> "AACObjectELD"
				MediaCodecInfo.CodecProfileLevel.AACObjectERLC -> "AACObjectERLC"
				MediaCodecInfo.CodecProfileLevel.AACObjectERScalable -> "AACObjectERScalable"
				MediaCodecInfo.CodecProfileLevel.AACObjectHE -> "AACObjectHE"
				MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS -> "AACObjectHE_PS"
				MediaCodecInfo.CodecProfileLevel.AACObjectLC -> "AACObjectLC"
				MediaCodecInfo.CodecProfileLevel.AACObjectLD -> "AACObjectLD"
				MediaCodecInfo.CodecProfileLevel.AACObjectLTP -> "AACObjectLTP"
				MediaCodecInfo.CodecProfileLevel.AACObjectSSR -> "AACObjectSSR"
				MediaCodecInfo.CodecProfileLevel.AACObjectScalable -> "AACObjectScalable"
				else -> "Profile:${profile}"
			}

	object VideoBitrate {
		val HD by lazy { 20000 }
		val SD_HIGH by lazy { 500 }
		val SD_LOW by lazy { 56 }
	}

	object Resolution {
		val HD by lazy { Size(1280, 720) }
		val SD_HIGH by lazy { Size(480, 360) }
		val SD_LOW by lazy { Size(176, 144) }
	}

	object FrameRate {
		val FAST by lazy { 30 }
		val SLOW by lazy { 15 }
	}

	object Orientation {
		val PORTRAIT by lazy { "Portrait" }
		val LANDSCAPE by lazy { "Landscape" }
	}

	object AudioChannel {
		val STEREO by lazy { 2 }
		val MONO by lazy { 1 }
	}


}

data class VideoProfile(val name: String, val profile: Int, val level: Int) {
	override fun toString(): String {
		return name
	}
}

data class AudioProfile(val name: String, val profile: Int) {
	override fun toString(): String {
		return name
	}
}


