package com.oksisi213.screenrecorder

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import android.widget.Toast
import com.oksisi213.screenrecorder.ScreenRecorder.Companion.AUDIO_AAC
import com.oksisi213.screenrecorder.ScreenRecorder.Companion.VIDEO_AVC
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by Charles on 19/01/2018.
 */


class ScreenRecorderActivity : Activity() {


	companion object {
		val TAG = ScreenRecorderActivity::class.java.simpleName
		val ACTION_STOP = "net.yrom.screenrecorder.action.STOP"

		val REQUEST_MEDIA_PROJECTION = 1
		val REQUEST_PERMISSIONS = 2
	}

	var isRecordingAudio = true

	val mediaProjectionManager: MediaProjectionManager? by lazy {
		getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
	}

	private var mAvcCodecInfos: Array<MediaCodecInfo>? = null // avc codecs
	private var mAacCodecInfos: Array<MediaCodecInfo>? = null // aac codecs

	private var mRecorder: ScreenRecorder? = null
	private val mNotifications by lazy {
		Notifications(applicationContext)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		Log.e(TAG, "onCreate")
		super.onCreate(savedInstanceState)

		Utils.findEncodersByTypeAsync(ScreenRecorder.VIDEO_AVC, object : Utils.Callback {
			override fun onResult(infos: Array<MediaCodecInfo>) {
				logCodecInfos(infos, ScreenRecorder.VIDEO_AVC)
				mAvcCodecInfos = infos
			}
		})

		Utils.findEncodersByTypeAsync(ScreenRecorder.AUDIO_AAC, object : Utils.Callback {
			override fun onResult(infos: Array<MediaCodecInfo>) {
				logCodecInfos(infos, ScreenRecorder.AUDIO_AAC)
				mAacCodecInfos = infos
			}
		})

		val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
		startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)

	}

	override fun onDestroy() {
		super.onDestroy()
		Log.e(TAG, "onDestroy")
		stopRecorder()
	}

	override fun onConfigurationChanged(newConfig: Configuration?) {
		super.onConfigurationChanged(newConfig)
		if (newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE) {

		} else {

		}

	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQUEST_MEDIA_PROJECTION) {
			if (mediaProjectionManager == null) {
				Log.e(TAG, "mediaProjectionManager is null")
			}
			val mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
			if (mediaProjection == null) {
				Log.e(TAG, "MediaProjection is null")
				return
			}
			val video = VideoEncodeConfig(
					640,
					360,
					25000,
					30,
					1,
					mAvcCodecInfos!!.get(0).name,
					MediaFormat.MIMETYPE_VIDEO_AVC,
					Utils.toProfileLevel("Default")
			)

			val audio = AudioEncodeConfig(
					mAacCodecInfos!!.get(0).name,
					AUDIO_AAC,
					80,
					44100,
					1,
					MediaCodecInfo.CodecProfileLevel.AACObjectMain)

			val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScreenCaptures")
			if (!dir.exists() && !dir.mkdirs()) {
				cancelRecorder()
				return
			}

			val format = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
			val file = File(dir, "Screen-" + format.format(Date())
					+ "-" + video.width + "x" + video.height + ".mp4")
			Log.d("@@", "Create recorder with :$video \n $audio\n $file")
			mRecorder = newRecorder(mediaProjection, video, audio, file)
			if (hasPermissions()) {
				startRecorder()
			} else {
				cancelRecorder()
			}
		}
	}

	private fun hasPermissions(): Boolean {
		val pm = packageManager
		val packageName = packageName
		val granted = (if (isRecordingAudio) pm.checkPermission(RECORD_AUDIO, packageName)
		else PackageManager.PERMISSION_GRANTED) or pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName)
		return granted == PackageManager.PERMISSION_GRANTED
	}

	private fun stopRecorder() {
		mNotifications.clear()
		mRecorder?.quit()
		mRecorder = null
		try {
			unregisterReceiver(mStopActionReceiver)
		} catch (e: Exception) {
			e.printStackTrace()
		}

	}

	private fun cancelRecorder() {
		if (mRecorder == null) return
		Toast.makeText(this, "Permission denied! Screen recorder is cancel", Toast.LENGTH_SHORT).show()
		stopRecorder()
	}

	private fun startRecorder() {
		if (mRecorder == null) return
		mRecorder?.start()
		registerReceiver(mStopActionReceiver, IntentFilter(ACTION_STOP))
		moveTaskToBack(true)
	}


	private fun logCodecInfos(codecInfos: Array<MediaCodecInfo>, mimeType: String) {
		for (info in codecInfos) {
			val builder = StringBuilder(512)
			val caps = info.getCapabilitiesForType(mimeType)
			builder.append("Encoder '").append(info.name).append('\'')
					.append("\n  supported : ")
					.append(Arrays.toString(info.supportedTypes))
			val videoCaps = caps.videoCapabilities
			if (videoCaps != null) {
				builder.append("\n  Video capabilities:")
						.append("\n  Widths: ").append(videoCaps.supportedWidths)
						.append("\n  Heights: ").append(videoCaps.supportedHeights)
						.append("\n  Frame Rates: ").append(videoCaps.supportedFrameRates)
						.append("\n  Bitrate: ").append(videoCaps.bitrateRange)
				if (VIDEO_AVC.equals(mimeType)) {
					val levels = caps.profileLevels

					builder.append("\n  Profile-levels: ")
					for (level in levels) {
						builder.append("\n  ").append(Utils.avcProfileLevelToString(level))
					}
				}
				builder.append("\n  Color-formats: ")
				for (c in caps.colorFormats) {
					builder.append("\n  ").append(Utils.toHumanReadable(c))
				}
			}
			val audioCaps = caps.audioCapabilities
			if (audioCaps != null) {
				builder.append("\n Audio capabilities:")
						.append("\n Sample Rates: ").append(Arrays.toString(audioCaps.supportedSampleRates))
						.append("\n Bit Rates: ").append(audioCaps.bitrateRange)
						.append("\n Max channels: ").append(audioCaps.maxInputChannelCount)
			}
			Log.i("@@@", builder.toString())
		}
	}

	private val mStopActionReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val file = File(mRecorder?.savedPath)
			if (ACTION_STOP == intent.action) {
				stopRecorder()
			}
			Toast.makeText(context, "Recorder stopped!\n Saved file $file", Toast.LENGTH_LONG).show()
			val vmPolicy = StrictMode.getVmPolicy()
			try {
				// disable detecting FileUriExposure on public file
				StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
				viewResult(file)
			} finally {
				StrictMode.setVmPolicy(vmPolicy)
			}
		}

		private fun viewResult(file: File) {
			val view = Intent(Intent.ACTION_VIEW)
			view.addCategory(Intent.CATEGORY_DEFAULT)
			view.setDataAndType(Uri.fromFile(file), VIDEO_AVC)
			view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			try {
				startActivity(view)
			} catch (e: ActivityNotFoundException) {
				// no activity can open this video
			}

		}
	}

	private fun newRecorder(mediaProjection: MediaProjection, video: VideoEncodeConfig,
							audio: AudioEncodeConfig, output: File): ScreenRecorder {
		val r = ScreenRecorder(video, audio,
				1, mediaProjection, output.absolutePath)
		r.setCallback(object : ScreenRecorder.Callback {
			internal var startTime: Long = 0

			override fun onStop(error: Throwable) {
				runOnUiThread { stopRecorder() }
				if (error != null) {
					Toast.makeText(this@ScreenRecorderActivity, "Recorder error ! See logcat for more details", Toast.LENGTH_SHORT).show()
					error.printStackTrace()
					output.delete()
				} else {
					val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
							.addCategory(Intent.CATEGORY_DEFAULT)
							.setData(Uri.fromFile(output))
					sendBroadcast(intent)
				}
			}

			override fun onStart() {
				mNotifications.recording(0)
			}

			override fun onRecording(presentationTimeUs: Long) {
				if (startTime <= 0) {
					startTime = presentationTimeUs
				}
				val time = (presentationTimeUs - startTime) / 1000
				mNotifications.recording(time)
			}
		})

		return r
	}


	@TargetApi(Build.VERSION_CODES.M)
	private fun requestPermissions() {
		val permissions = arrayOf(WRITE_EXTERNAL_STORAGE, RECORD_AUDIO)
		var showRationale = false
		for (perm in permissions) {
			showRationale = showRationale or shouldShowRequestPermissionRationale(perm)
		}
		if (!showRationale) {
			requestPermissions(permissions, REQUEST_PERMISSIONS)
			return
		}
		AlertDialog.Builder(this)
				.setMessage("Using your mic to record audio and your sd card to save video file")
				.setCancelable(false)
				.setPositiveButton(android.R.string.ok) { dialog, which -> requestPermissions(permissions, REQUEST_PERMISSIONS) }
				.setNegativeButton(android.R.string.cancel, null)
				.create()
				.show()
	}

}