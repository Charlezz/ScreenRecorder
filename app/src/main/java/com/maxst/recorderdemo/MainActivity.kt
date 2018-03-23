package com.maxst.recorderdemo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.maxst.screenrecorder.*
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
	companion object {
		val TAG: String = MainActivity::class.java.simpleName
		const val PERMISSION_CODE_WRITE = 0
	}

	private val permissionList = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)
	private val dm = DisplayMetrics()
	private var recorder: ScreenRecorder? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		windowManager.defaultDisplay.getRealMetrics(dm)

		permissionList.forEach {
			if (ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, permissionList, PERMISSION_CODE_WRITE)
			}
			return@forEach
		}


		videoMimeTypeSpinner.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList<String>().apply {
			add(MediaFormat.MIMETYPE_VIDEO_AVC)
			add(MediaFormat.MIMETYPE_VIDEO_H263)
			add(MediaFormat.MIMETYPE_VIDEO_MPEG4)
		})

		videoMimeTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) {
			}

			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				initVideoCodecs()
			}

		}

		frameRateSpinner.adapter = ObjectArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<Int>().apply {
			add(CodecUtil.FrameRate.FAST)
			add(CodecUtil.FrameRate.SLOW)
		})

		iFrameIntervalSpinner.adapter = ObjectArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<Int>().apply {
			add(1)
		})


		orientationSpinner.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList<String>().apply {
			add(CodecUtil.Orientation.PORTRAIT)
			add(CodecUtil.Orientation.LANDSCAPE)
		})


		resolutionSpinner.adapter = ObjectArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<Size>().apply {
			add(Size(dm.widthPixels, dm.heightPixels))
			add(CodecUtil.Resolution.HD)
			add(CodecUtil.Resolution.SD_HIGH)
			add(CodecUtil.Resolution.SD_LOW)
		})

		audioMimeTypeSpinner.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList<String>().apply {
			add(MediaFormat.MIMETYPE_AUDIO_AAC)
			add(MediaFormat.MIMETYPE_AUDIO_AC3)
			add(MediaFormat.MIMETYPE_AUDIO_MPEG)
		})

		audioMimeTypeSpinner.onItemSelectedListener = object : OnItemSelectedAdapter() {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				initAudioEndcoder()
			}
		}

		audioChannelSpinner.adapter = ObjectArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<Int>().apply {
			add(CodecUtil.AudioChannel.MONO)
			add(CodecUtil.AudioChannel.STEREO)
		})

		record.setOnCheckedChangeListener { _, isChecked ->
			if (isChecked) {
				ScreenRecorder.requestCaptureIntent(this, 0)
			} else {
				recorder?.stop()
			}
		}
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		when (requestCode) {
			PERMISSION_CODE_WRITE -> {
				grantResults.forEach {
					if (it == PackageManager.PERMISSION_GRANTED) {
						Log.e(TAG, "${permissionList[it]} : Permissions have been granted")
					} else {
						finish()
					}
				}
			}
		}

	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		Log.e(TAG, "")
		if (requestCode == 0) {
			if (resultCode == Activity.RESULT_OK) {
				data?.let {
					recorder = ScreenRecorder(this, it)

					val size = resolutionSpinner.selectedItem as Size
					val density = dm.densityDpi
					val bitRate = videoBitrateSpinner.selectedItem as Int
					val frameRate = frameRateSpinner.selectedItem as Int
					val interval = iFrameIntervalSpinner.selectedItem as Int
					val codecName = (videoEncoderSpinner.selectedItem as MediaCodecInfo).name
					val videoMimeType = videoMimeTypeSpinner.selectedItem as String
					recorder?.videoConfig = VideoConfig(size.width, size.height, density, bitRate, frameRate, interval, codecName, videoMimeType)

					val audioCodecName = (audioCodecSpinner.selectedItem as MediaCodecInfo).name
					val audioMimeType = audioMimeTypeSpinner.selectedItem as String
					val audioBitrate = audioBitrateSpinner.selectedItem as Int
					val sampleRate = sampleRateSpinner.selectedItem as Int
					val channelCount = audioChannelSpinner.selectedItem as Int
					recorder?.audioConfig = AudioConfig(audioCodecName, audioMimeType, audioBitrate, sampleRate, channelCount)

					recorder?.usingMic = true
					recorder?.record()
				}
			} else {
				record.isChecked = false
			}
		}
	}

	private fun initVideoCodecs() {
		videoEncoderSpinner.adapter = MediaCodecArrayAdapter(this, android.R.layout.simple_list_item_1,
				ArrayList(CodecUtil.findVideoEncoderList(videoMimeTypeSpinner.selectedItem as String)))

		videoEncoderSpinner.onItemSelectedListener = object : OnItemSelectedAdapter() {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				initVideoProfiles()
				initVideoBitrates()
			}
		}
	}

	private fun initVideoBitrates() {
		videoBitrateSpinner.adapter = ArrayAdapter<Int>(this, android.R.layout.simple_list_item_1, ArrayList<Int>().apply {
			add(CodecUtil.VideoBitrate.HD)
			add(CodecUtil.VideoBitrate.SD_HIGH)
			add(CodecUtil.VideoBitrate.SD_LOW)
		})
	}

	private fun initVideoProfiles() {
		(videoEncoderSpinner.selectedItem as MediaCodecInfo).let {
			videoProfileSpinner.adapter = ObjectArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, ArrayList<VideoProfile>().apply {
				try {
					it.getCapabilitiesForType(videoMimeTypeSpinner.selectedItem as String).profileLevels.forEach {
						val name = "${CodecUtil.getAVCProfileName(it.profile)} / ${CodecUtil.getAVCProfileLevel(it.level)}"
						add(VideoProfile(name, it.profile, it.level))
					}
				} catch (e: IllegalArgumentException) {
					e.printStackTrace()
				}

			})
		}
	}


	private fun initAudioEndcoder() {
		audioCodecSpinner.adapter = MediaCodecArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1,
				ArrayList(CodecUtil.findAudioEncoderList(audioMimeTypeSpinner.selectedItem as String)))

		audioCodecSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) {
			}

			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				initAudioProfiles()
				initAudioSampleRates()
				initAudioBitRates()
			}
		}
	}

	private fun initAudioProfiles() {
		(audioCodecSpinner.selectedItem as MediaCodecInfo)
				.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC).let {
					audioProfileSpinner.adapter = ObjectArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, ArrayList<AudioProfile>().apply {
						it.profileLevels.forEach {
							val name = CodecUtil.getAACProfileName(it.profile)
							add(AudioProfile(name, it.profile))
						}
					})

				}
	}

	private fun initAudioSampleRates() {
		(audioCodecSpinner.selectedItem as MediaCodecInfo)
				.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC).let {
					sampleRateSpinner.adapter = ObjectArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1,
							ArrayList<Int>(it.audioCapabilities.supportedSampleRates.toMutableList().apply {
								sortDescending()
							})
					)
				}

	}

	private fun initAudioBitRates() {
		CodecUtil.getAudioBitrates(
				audioCodecSpinner.selectedItem as MediaCodecInfo
				, audioMimeTypeSpinner.selectedItem as String
		).let {
			audioBitrateSpinner.adapter = ObjectArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, it)
		}
	}


	inner class MediaCodecArrayAdapter(context: Context, resource: Int, objects: ArrayList<MediaCodecInfo>) : ArrayAdapter<MediaCodecInfo>(context, resource, objects) {
		override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View = super.getDropDownView(position, convertView, parent).also {
			(it as TextView).text = getItem(position).name
		}

		override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = super.getDropDownView(position, convertView, parent).also {
			(it as TextView).text = getItem(position).name
		}

	}

	inner class ObjectArrayAdapter<T>(context: Context, resource: Int, objects: ArrayList<T>) : ArrayAdapter<T>(context, resource, objects) {
		override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View = super.getDropDownView(position, convertView, parent).also {
			(it as TextView).text = getItem(position).toString()
		}

		override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = super.getDropDownView(position, convertView, parent).also {
			(it as TextView).text = getItem(position).toString()
		}

	}
}
