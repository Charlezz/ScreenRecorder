package com.oksisi213.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by Charles on 22/01/2018.
 */
class ScreenRecorderManager private constructor() {


	companion object {
		val TAG = ScreenRecorderManager::class.java.simpleName
		val instance by lazy {
			ScreenRecorderManager()
		}

	}

	var isRecordingAudio = true
	var mediaProjectionManager: MediaProjectionManager? = null
	private var mRecorder: ScreenRecorder? = null

	init {
		Utils.findEncodersByTypeAsync(ScreenRecorder.VIDEO_AVC, object : Utils.Callback {
			override fun onResult(infos: Array<MediaCodecInfo>) {


			}
		})

		Utils.findEncodersByTypeAsync(ScreenRecorder.AUDIO_AAC, object : Utils.Callback {
			override fun onResult(infos: Array<MediaCodecInfo>) {
			}
		})
	}


	fun requestVirtualDisplay(activity: Activity, requestCode: Int) {
		mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?
		mediaProjectionManager?.createScreenCaptureIntent()?.let {
			activity.startActivityForResult(it, requestCode)
		} ?: throw NullPointerException("MediaProjection Manager is null")
	}

	fun stopRecording() {

	}

	fun startRecording(activity: Activity, resultCode: Int, data: Intent) {
		val mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
				?: throw IllegalArgumentException("MediaProjection is null, check the resultCode and data")

		val video = VideoEncodeConfig(
				640,
				360,
				25000,
				30,
				1,
				Utils.findEncodersByType(MediaFormat.MIMETYPE_VIDEO_AVC)[0].name,
				MediaFormat.MIMETYPE_VIDEO_AVC,
				Utils.toProfileLevel("Default"))

		val audio = AudioEncodeConfig(
				Utils.findEncodersByType(MediaFormat.MIMETYPE_AUDIO_AAC)[0].name,
				MediaFormat.MIMETYPE_AUDIO_AAC,
				80,
				44100,
				1,
				MediaCodecInfo.CodecProfileLevel.AACObjectMain)

		val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenCaptures")
		if (!dir.exists() && !dir.mkdirs()) {
			cancelRecorder(activity)
			return
		}

		val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
		val file = File(dir, "Screen-" + format.format(Date())
				+ "-" + video.width + "x" + video.height + ".mp4")
		Log.d("@@", "Create recorder with :$video \n $audio\n $file")
		mRecorder = newRecorder(activity, mediaProjection, video, audio, file)
		if (hasPermissions(activity)) {
			startRecorder(activity)
		} else {
			cancelRecorder(activity)
		}
	}

	private fun hasPermissions(context: Context): Boolean {
		val pm = context.packageManager
		val packageName = context.packageName
		val granted = (if (isRecordingAudio) pm.checkPermission(Manifest.permission.RECORD_AUDIO, packageName)
		else PackageManager.PERMISSION_GRANTED) or pm.checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, packageName)
		return granted == PackageManager.PERMISSION_GRANTED
	}

	private fun cancelRecorder(activity: Activity) {
		if (mRecorder == null) return
		Toast.makeText(activity, "Permission denied! Screen recorder is cancel", Toast.LENGTH_SHORT).show()
		stopRecorder(activity)
	}

	private fun startRecorder(activity: Activity) {
		if (mRecorder == null) return
		mRecorder?.start()
		activity.applicationContext.registerReceiver(mStopActionReceiver, IntentFilter(ScreenRecorderActivity.ACTION_STOP))
	}

	private fun newRecorder(activity: Activity, mediaProjection: MediaProjection, video: VideoEncodeConfig,
							audio: AudioEncodeConfig, output: File): ScreenRecorder {
		val r = ScreenRecorder(video, audio,
				1, mediaProjection, output.absolutePath)
		r.setCallback(object : ScreenRecorder.Callback {
			internal var startTime: Long = 0

			override fun onStop(error: Throwable) {
				activity.runOnUiThread { stopRecorder(activity) }
				if (error != null) {
					Toast.makeText(activity, "Recorder error ! See logcat for more details", Toast.LENGTH_SHORT).show()
					error.printStackTrace()
					output.delete()
				} else {
					val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
							.addCategory(Intent.CATEGORY_DEFAULT)
							.setData(Uri.fromFile(output))
					activity.sendBroadcast(intent)
				}
			}

			override fun onStart() {
//				mNotifications.recording(0)
			}

			override fun onRecording(presentationTimeUs: Long) {
				if (startTime <= 0) {
					startTime = presentationTimeUs
				}
				val time = (presentationTimeUs - startTime) / 1000
//				mNotifications.recording(time)
			}
		})

		return r
	}

	private fun stopRecorder(context: Context) {
//		mNotifications.clear()
		mRecorder?.quit()
		mRecorder = null
		try {
			context.applicationContext.unregisterReceiver(mStopActionReceiver)
		} catch (e: Exception) {
			e.printStackTrace()
		}

	}

	private val mStopActionReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val file = File(mRecorder?.savedPath)
			if (ScreenRecorderActivity.ACTION_STOP == intent.action) {
				stopRecorder(context)
			}
			Toast.makeText(context, "Recorder stopped!\n Saved file $file", Toast.LENGTH_LONG).show()
			val vmPolicy = StrictMode.getVmPolicy()
			try {
				// disable detecting FileUriExposure on public file
				StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
				viewResult(context, file)
			} finally {
				StrictMode.setVmPolicy(vmPolicy)
			}
		}

		private fun viewResult(context: Context, file: File) {
			val view = Intent(Intent.ACTION_VIEW)
			view.addCategory(Intent.CATEGORY_DEFAULT)
			view.setDataAndType(Uri.fromFile(file), ScreenRecorder.VIDEO_AVC)
			view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			try {
				context.startActivity(view)
			} catch (e: ActivityNotFoundException) {
				// no activity can open this video
			}

		}
	}
}
