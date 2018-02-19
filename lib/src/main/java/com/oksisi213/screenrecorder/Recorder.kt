@file:Suppress("UNCHECKED_CAST")

package com.oksisi213.screenrecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.util.SparseLongArray
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 5..
 */

open abstract class Recorder<out T> {

	//video
	protected var width = CodecUtil.Resolution.HD.width
	protected var height = CodecUtil.Resolution.HD.height
	protected var orientation = CodecUtil.Orientation.PORTRAIT
	protected var videoBitrate = CodecUtil.VideoBitrate.HD
	protected var frameRate = CodecUtil.FrameRate.FAST
	protected var iFrameInterval = 1
	protected var videoMimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC
	protected var videoCodecName: String? = null
	protected var videoCodecProfileLevel: MediaCodecInfo.CodecProfileLevel? = null
	protected var videoProfile: Int = 0
	protected var videoLevel: Int = 0
	protected var videoCodec: MediaCodec? = null
	protected var mediaProjection: MediaProjection? = null
	protected var videoTrackIndex: Int = -1

	//audio
	protected var isMicRecording: Boolean = false
	protected var audioMimeType: String = MediaFormat.MIMETYPE_AUDIO_AAC
	protected var audioCodecName: String? = null
	protected var audioCodec: MediaCodec? = null
	protected var audioProfile: Int = 0
	protected var sampleRate: Int = 0
	protected var audioBitrate: Int = 0
	protected var audioChannelCount: Int = CodecUtil.AudioChannel.STEREO
	protected var audioTrackIndex: Int = -1

	protected var micRecord: AudioRecord? = null
	protected var minBytes: Int = 0


	fun setResolution(width: Int, height: Int): T {
		this.width = width
		this.height = height
		return this as T
	}

	fun setVideoBitrate(bitrate: Int): T {
		this.videoBitrate = bitrate
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
		this.videoMimeType = mimeType
		return this as T
	}

	fun setVideoCodec(videoCodecInfo: MediaCodecInfo): T {
		this.videoCodecName = videoCodecInfo.name
//		videoCodecInfo.getCapabilitiesForType(videoMimeType)
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

	/////////
	fun setMicRecording(enabled: Boolean): T {
		isMicRecording = enabled
		return this as T

	}

	fun setAudioMimeType(mimeType: String): T {
		audioMimeType = mimeType
		return this as T
	}

	fun setAudioCodec(audioCodecInfo: MediaCodecInfo): T {
		this.audioCodecName = audioCodecInfo.name
//		audioCodecInfo.getCapabilitiesForType(audioMimeType)
		return this as T
	}

	fun setAudioProfile(audioProfile: Int): T {
		this.audioProfile = audioProfile
		return this as T
	}

	fun setAudioSampleRate(sampleRate: Int): T {
		this.sampleRate = sampleRate
		return this as T
	}

	fun setAudioBitrate(bitrate: Int): T {
		audioBitrate = bitrate
		return this as T
	}

	fun setAudioChannel(count: Int): T {
		audioChannelCount = count
		return this as T
	}

	abstract fun createRecorder(): T
}

//class DefaultRecorderFatory private constructor(context: Context) : Recorder<DefaultRecorderFatory>() {
//	override fun createRecorder(): DefaultRecorderFatory {
//		return this
//	}
//
//}

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

		val dateFormat = SimpleDateFormat("yyMMdd_HHmmss")
		val date = dateFormat.format(Date())
		val dstPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/record" + date
		muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

		var videoMediaFormat = MediaFormat.createVideoFormat(videoMimeType, width, height)
		videoMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
		videoMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
		videoMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
		videoMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)


		if (videoProfile != 0 && videoLevel != 0) {
			videoMediaFormat.setInteger(MediaFormat.KEY_PROFILE, videoProfile)
//			videoMediaFormat.setInteger(MediaFormat.KEY_LEVEL, videoLevel)
			videoMediaFormat.setInteger("level", videoLevel)
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
				Log.e(TAG, "onOutputFormatChanged: video")
				muxer?.let {
					videoTrackIndex = it.addTrack(format)
				}
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


		var audioMediaFormat = MediaFormat.createAudioFormat(audioMimeType, sampleRate, audioChannelCount)
		audioMediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, audioProfile)
		audioMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)

		audioCodec = MediaCodec.createByCodecName(audioCodecName)

		audioCodec?.setCallback(object : MediaCodec.Callback() {
			override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
				val buffer = codec?.getOutputBuffer(index)
				muxer?.writeSampleData(audioTrackIndex, buffer, info)
				codec?.releaseOutputBuffer(index, false)
			}

			override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
				Log.e(TAG, "onInputBufferAvailable: ")
				val buffer = codec?.getInputBuffer(index)
				val byteCount = micRecord?.read(buffer, buffer!!.limit())
				val pstTs = calculateFrameTimestamp(byteCount!! shl 3)
				codec?.queueInputBuffer(index, 0, minBytes, calculateFrameTimestamp(pstTs.toInt()), MediaCodec.BUFFER_FLAG_KEY_FRAME)
			}

			override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
				Log.e(TAG, "onOutputFormatChanged: audio")
				muxer?.let {
					audioTrackIndex = it.addTrack(format)
				}
			}

			override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
				e?.printStackTrace()
			}
		})
		audioCodec?.configure(audioMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
		if (isMicRecording) {
			var minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
			if (minBytes <= 0) {
				Log.e(TAG, String.format(Locale.US, "Bad arguments: getMinBufferSize(%d, %d, %d)",
						sampleRate, audioChannelCount, AudioFormat.ENCODING_PCM_16BIT))
				return this
			}

			micRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
					sampleRate,
					AudioFormat.CHANNEL_IN_STEREO,
					AudioFormat.ENCODING_PCM_16BIT,
					minBytes * 2)

			micRecord?.let {
				if (it.state == AudioRecord.STATE_UNINITIALIZED) {
					Log.e(TAG, "bad arguments ${sampleRate}")
				}
			}
		}
		return this
	}


	fun start() {
		micRecord?.startRecording()
		videoCodec?.start()
		audioCodec?.start()
	}

	fun stop() {
		Log.e(TAG, "stop")
		videoCodec?.stop()
		audioCodec?.stop()

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

	private val LAST_FRAME_ID = -1
	private val mFramesUsCache = SparseLongArray(2)
	private fun calculateFrameTimestamp(totalBits: Int): Long {
		val samples = totalBits shr 4
		var frameUs = mFramesUsCache.get(samples, -1)
		if (frameUs == -1L) {
			frameUs = (samples * 1000000 / sampleRate).toLong()
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
		Log.i(TAG, "count samples pts: $currentUs, time pts: $timeUs, samples: $samples")
		// maybe too late to acquire sample data
		if (timeUs - currentUs >= frameUs shl 1) {
			// reset
			currentUs = timeUs
		}
		mFramesUsCache.put(LAST_FRAME_ID, currentUs + frameUs)
		return currentUs
	}

}


