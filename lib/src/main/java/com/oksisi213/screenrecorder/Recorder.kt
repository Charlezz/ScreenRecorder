@file:Suppress("UNCHECKED_CAST")

package com.oksisi213.screenrecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.util.Log
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 5..
 */

open abstract class Recorder<out T> {

	protected var width = CodecUtil.Resolution.HD.width
	protected var height = CodecUtil.Resolution.HD.height
	protected var orientation = CodecUtil.Orientation.PORTRAIT
	protected var bitrate = CodecUtil.VideoBitrate.HD
	protected var frameRate = CodecUtil.FrameRate.FAST
	protected var iFrameInterval = 1
	protected var mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
	protected var videoCodecName: String? = null
	protected var videoCodecProfileLevel: MediaCodecInfo.CodecProfileLevel? = null
	protected var videoProfile: Int = 0
	protected var videoLevel: Int = 0

	protected var videoCodec: MediaCodec? = null
	protected var mediaProjection: MediaProjection? = null


	fun setResolution(width: Int, height: Int): T {
		this.width = width
		this.height = height
		return this as T
	}

	fun setVideoBitrate(bitrate: Int): T {
		this.bitrate = bitrate
		return this as T
	}

	fun setFrameRate(frameRate: Int): T {
		this.frameRate = frameRate
		return this as T
	}

	fun setIFrameInterval(iFrameInterval: Int): T {
		this.iFrameInterval = iFrameInterval
		return this as T
	}

	fun setVideoMimeType(mimeType: String): T {
		this.mimeType = mimeType
		return this as T
	}

	fun setVideoCodec(videoCodecInfo: MediaCodecInfo): T {
		this.videoCodecName = videoCodecInfo.name
		videoCodecInfo.getCapabilitiesForType(mimeType)
		return this as T
	}

	fun setVideoCodec(codecName: String): T {
		this.videoCodecName = codecName
		return this as T
	}

	fun setVideoProfile(profile: Int): T {
		this.videoProfile = profile
		return this as T
	}

	fun setVideoLevel(level: Int): T {
		this.videoLevel = level
		return this as T
	}

	abstract fun createRecorder(): T
}

class DefaultRecorderFatory private constructor(context: Context) : Recorder<DefaultRecorderFatory>() {
	override fun createRecorder(): DefaultRecorderFatory {
		return this
	}

}

class ScreenRecorder private constructor() : Recorder<ScreenRecorder>() {
	val TAG = Recorder::class.java.simpleName

	companion object {
		fun requestCaptureIntent(activity: Activity, requestCode: Int) {
			val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			activity.startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
		}
	}

	private var context: Context? = null
	private var data: Intent? = null

	var virtualDisplayName = TAG


	var dpi: Int = 1
	var flag = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
	var virtualDisplayCallback: VirtualDisplay.Callback? = null
	var handler: Handler? = null
	var virtualDisplay: VirtualDisplay? = null
	var muxer: MediaMuxer? = null

	constructor(context: Context, data: Intent) : this() {
		this.context = context
		this.data = data
	}


	override fun createRecorder(): ScreenRecorder {

		val dateFormat = SimpleDateFormat("yyMMdd_HHmmss");
		val date = dateFormat.format(Date())



		Log.e(TAG, "record: ")
		var videoMediaFormat = MediaFormat.createVideoFormat(mimeType, width, height)
		videoMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
		videoMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
		videoMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
		videoMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)



		if (videoProfile != 0 && videoLevel != 0) {
			videoMediaFormat.setInteger(MediaFormat.KEY_PROFILE, videoProfile)
			videoMediaFormat.setInteger(MediaFormat.KEY_LEVEL, videoLevel)
		}

		videoCodec = MediaCodec.createByCodecName(videoCodecName)


		videoCodec?.setCallback(object : MediaCodec.Callback() {
			override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo) {
				var buffer: ByteBuffer? = codec?.getOutputBuffer(index)
				if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
					Log.e(TAG, "BUFFER_FLAG_CODEC_CONFIG")
				}
				if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
					Log.e(TAG, "BUFFER_FLAG_END_OF_STREAM")
				}
				muxer?.writeSampleData(0, buffer, info)
				codec?.releaseOutputBuffer(index, false)

			}

			override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
				Log.e(TAG, "onInputBufferAvailable: ")
			}

			override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
				Log.e(TAG, "onOutputFormatChanged: ")
				muxer?.addTrack(format)
				muxer?.start()

			}

			override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
				Log.e(TAG, "onError: ")
			}
		})
		videoCodec?.configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

		val manager = context?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
		mediaProjection = manager.getMediaProjection(Activity.RESULT_OK, data)
		virtualDisplay = mediaProjection?.createVirtualDisplay(
				virtualDisplayName,
				width,
				height,
				dpi,
				flag,
				videoCodec?.createInputSurface(),
				null,
				null
		)

		val dstPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/record" + date
		muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
		videoCodec?.start()
		return this
	}

	fun stop() {
		Log.e(TAG, "stop")
		videoCodec?.stop()

		var eosBuffer = MediaCodec.BufferInfo()
		eosBuffer.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
		muxer?.writeSampleData(0, ByteBuffer.allocate(0), eosBuffer)

		virtualDisplay?.release()
		videoCodec?.setCallback(null)
		videoCodec?.release()
		mediaProjection?.stop()

		muxer?.stop()
		muxer?.release()

	}
}


