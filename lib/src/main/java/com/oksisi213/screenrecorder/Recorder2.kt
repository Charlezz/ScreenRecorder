package com.oksisi213.screenrecorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 21..
 */
open abstract class Recorder2<out T> {

	private val dstPath by lazy {
		val dateFormat = SimpleDateFormat("yyMMdd_HHmmss")
		val date = dateFormat.format(Date())
		Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/record" + date
	}
	protected val muxer: MediaMuxer by lazy {
		MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
	}

	protected var videoConfig: VideoConfig = VideoConfig.getDefaultConfig()
	protected var audioConfig: AudioConfig = AudioConfig.getDefaultConfig()

	fun setVideoConfig(config: VideoConfig): T {
		this.videoConfig = config
		return this as T
	}

	fun setAudioConfig(config: AudioConfig): T {
		this.audioConfig = config
		return this as T
	}
}


class ScreenRecorder2 constructor(context: Context, data: Intent) : Recorder2<ScreenRecorder2>(), WeakRefHandler.IMessageListener {
	val TAG = ScreenRecorder2::class.java.simpleName

	companion object {
		fun requestCaptureIntent(activity: Activity, requestCode: Int) {
			val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			activity.startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
		}
	}

	private val MSG_START = 0
	private val mediaProjectionManager: MediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
	private val mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)

	val worker = HandlerThread("worker").apply {
		start()
	}
	val handler = WeakRefHandler(worker.looper, this)

	override fun handleMessage(message: Message) {
		when (message.what) {
			MSG_START -> {
				val videoCodec: MediaCodec? = videoConfig?.createMediaCodec()
				videoCodec?.configure(videoConfig?.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				videoCodec?.setCallback(object : MediaCodec.Callback() {
					override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
						Log.e(TAG, "onOutputBufferAvailable")
						codec?.releaseOutputBuffer(index, false)
					}

					override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
						Log.e(TAG, "onInputBufferAvailable")
					}

					override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
						Log.e(TAG, "onOutputFormatChanged")
					}

					override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
						Log.e(TAG, "onError")
					}

				})

				mediaProjection?.createVirtualDisplay(
						TAG,
						videoConfig?.width!!,
						videoConfig?.height!!,
						1,
						DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
						videoCodec?.createInputSurface(),
						object : VirtualDisplay.Callback() {
							override fun onResumed() {
								super.onResumed()
								Log.e(TAG, "virtual display onResumed")
							}

							override fun onPaused() {
								super.onPaused()
								Log.e(TAG, "virtual display onPaused")
							}

							override fun onStopped() {
								super.onStopped()
								Log.e(TAG, "virtual display onStopped")
							}
						},
						null
				)
				videoCodec?.start()


				///audio

				val audioCodec: MediaCodec? = audioConfig.createMediaCodec()
				Log.e(TAG, audioConfig.toString())
				audioCodec?.configure(audioConfig.mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
				audioCodec?.setCallback(object : MediaCodec.Callback() {
					override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
						Log.e(TAG, "audio onOutputBufferAvailable")
					}

					override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
						Log.e(TAG, "audio onInputBufferAvailable")
					}

					override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
						Log.e(TAG, "audio onOutputFormatChanged")
					}

					override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
						Log.e(TAG, "audio onError")
					}
				})

				var minBytes = AudioRecord.getMinBufferSize(audioConfig.sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
				if (minBytes <= 0) {
					Log.e(TAG, String.format(Locale.US, "Bad arguments: getMinBufferSize(%d, %d, %d)",
							audioConfig.sampleRate, audioConfig.channelCount, AudioFormat.ENCODING_PCM_16BIT))
					return
				}

				var micRecord = AudioRecord(MediaRecorder.AudioSource.MIC,
						audioConfig.sampleRate,
						AudioFormat.CHANNEL_IN_STEREO,
						AudioFormat.ENCODING_PCM_16BIT,
						minBytes * 2)

				if (micRecord.state == AudioRecord.STATE_UNINITIALIZED) {
					Log.e(TAG, "bad arguments")
				}
//				micRecord.startRecording()
				audioCodec?.start()


			}
		}
	}

	fun record() {
		handler.sendEmptyMessage(MSG_START)
	}

}