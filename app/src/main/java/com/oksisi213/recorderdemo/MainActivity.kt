package com.oksisi213.recorderdemo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.HandlerThread
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.oksisi213.screenrecorder.*
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.locks.ReentrantLock


class MainActivity : AppCompatActivity() {


	companion object {
		val TAG = MainActivity::class.java.simpleName
	}

	val spinnerItemId = android.R.layout.simple_list_item_1
	var PERMISSION_CODE_WRITE = 0
	val PERMISSION_LIST = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)

	lateinit var pcmThread: HandlerThread
	lateinit var pcmHandler: WeakRefHandler

	lateinit var workerThread: HandlerThread
	lateinit var workerHandler: WeakRefHandler

	val lock: ReentrantLock = ReentrantLock()
	val bufferPool = ByteBufferPool(5, 1024 * 4)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		val bufferPool = ByteBufferPool(5, 4)

		val pcmThread = HandlerThread("PCM")
		val pcmHandler = WeakRefHandler(pcmThread.looper, object : WeakRefHandler.IMessageListener {
			override fun handleMessage(message: Message) {
				var count = 0
				while (true) {
					Thread.sleep(0)
					val byteArray = ByteArray(Random().nextInt(4048) + 4048) { position ->
						count++.let {
							if (count == Int.MAX_VALUE) {
								count = 0
							}
							it.toByte()
						}
					}
					val byteBuffer = ByteBuffer.wrap(byteArray)
					lock.lock()
					bufferPool.insert(byteBuffer)
					lock.unlock()
				}
			}
		})


		workerThread = HandlerThread("worker")
		workerHandler = WeakRefHandler(workerThread.looper, object : WeakRefHandler.IMessageListener {
			override fun handleMessage(message: Message) {
				while (true) {
					Thread.sleep(0)
					lock.lock()
					if(!bufferPool.empty()){
						val buffer = ByteBuffer.allocate(1024*4)
						val bufferCount:Int = bufferPool.read(buffer)
					}
					lock.unlock()


//						inputBuffer.array().forEach {
//							Log.e(TAG, "${it.toInt()}")
//						}
				}
			}

		})



		pcmThread.start()
		workerThread.start()

		pcmHandler.sendEmptyMessage(0)




		PERMISSION_LIST.forEach {
			Log.e(TAG, "onCreate: $it")
			if (ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, PERMISSION_LIST, PERMISSION_CODE_WRITE)
			}
			return@forEach
		}


		videoMimeTypeSpinner.adapter = ArrayAdapter<String>(this, spinnerItemId, ArrayList<String>().apply {
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

		frameRateSpinner.adapter = ObjectArrayAdapter(this, spinnerItemId, ArrayList<Int>().apply {
			add(CodecUtil.FrameRate.FAST)
			add(CodecUtil.FrameRate.SLOW)
		})

		iFrameIntervalSpinner.adapter = ObjectArrayAdapter(this, spinnerItemId, ArrayList<Int>().apply {
			add(1)
		})


		orientationSpinner.adapter = ArrayAdapter<String>(this, spinnerItemId, ArrayList<String>().apply {
			add(CodecUtil.Orientation.PORTRAIT)
			add(CodecUtil.Orientation.LANDSCAPE)
		})


		resolutionSpinner.adapter = ObjectArrayAdapter(this, spinnerItemId, ArrayList<Size>().apply {
			add(CodecUtil.Resolution.HD)
			add(CodecUtil.Resolution.SD_HIGH)
			add(CodecUtil.Resolution.SD_LOW)
		})

		audioMimeTypeSpinner.adapter = ArrayAdapter<String>(this, spinnerItemId, ArrayList<String>().apply {
			add(MediaFormat.MIMETYPE_AUDIO_AAC)
			add(MediaFormat.MIMETYPE_AUDIO_AC3)
			add(MediaFormat.MIMETYPE_AUDIO_MPEG)
		})
		audioMimeTypeSpinner.onItemSelectedListener = object : OnItemSelectedAdapter() {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				initAudioEndcoder()
			}
		}

		audioChannelSpinner.adapter = ObjectArrayAdapter(this, spinnerItemId, ArrayList<Int>().apply {
			add(CodecUtil.AudioChannel.STEREO)
			add(CodecUtil.AudioChannel.MONO)
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
						Log.e(TAG, "${PERMISSION_LIST[it]} : Permissions have been granted")
					} else {
						finish()
					}
				}
			}
		}

	}

	var recorder: ScreenRecorder? = null
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == 0) {
			if (resultCode == Activity.RESULT_OK) {
				data?.let {
					recorder = ScreenRecorder(this, it)
							.setVideoConfig(VideoConfig.getDefaultConfig())
							.setAudioConfig(null)
							.record()
				}


			} else {
				record.isChecked = false
			}
		}
	}

	private fun initVideoCodecs() {
		videoEncoderSpinner.adapter = MediaCodecArrayAdapter(this, spinnerItemId,
				ArrayList(CodecUtil.findVideoEncoderList(videoMimeTypeSpinner.selectedItem as String)))

		videoEncoderSpinner.onItemSelectedListener = object : OnItemSelectedAdapter() {
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				initVideoProfiles()
				initVideoBitrates()
			}
		}


	}

	private fun initVideoBitrates() {
		videoBitrateSpinner.adapter = ArrayAdapter<Int>(this, spinnerItemId, ArrayList<Int>().apply {
			add(CodecUtil.VideoBitrate.HD)
			add(CodecUtil.VideoBitrate.SD_HIGH)
			add(CodecUtil.VideoBitrate.SD_LOW)
		})
	}

	private fun initVideoProfiles() {
		(videoEncoderSpinner.selectedItem as MediaCodecInfo).let {
			videoProfileSpinner.adapter = ObjectArrayAdapter(this@MainActivity, spinnerItemId, ArrayList<VideoProfile>().apply {
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
		audioCodecSpinner.adapter = MediaCodecArrayAdapter(this@MainActivity, spinnerItemId,
				ArrayList(CodecUtil.findAudioEncoderList(audioMimeTypeSpinner.selectedItem as String)))

		audioCodecSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) {
			}

			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				initAudioProfiles()
				initAudioSampleRates()
				initAudioBitrates()
			}
		}
	}

	private fun initAudioProfiles() {
		(audioCodecSpinner.selectedItem as MediaCodecInfo)
				.getCapabilitiesForType(MediaFormat.MIMETYPE_AUDIO_AAC).let {
					audioProfileSpinner.adapter = ObjectArrayAdapter(this@MainActivity, spinnerItemId, ArrayList<AudioProfile>().apply {
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
					sampleRateSpinner.adapter = ObjectArrayAdapter(this@MainActivity, spinnerItemId,
							ArrayList<Int>(it.audioCapabilities.supportedSampleRates.toMutableList().apply {
								sortDescending()
							})
					)
				}

	}

	private fun initAudioBitrates() {
		CodecUtil.getAudioBitrates(
				audioCodecSpinner.selectedItem as MediaCodecInfo
				, audioMimeTypeSpinner.selectedItem as String
		).let {
			audioBitrateSpinner.adapter = ObjectArrayAdapter(this@MainActivity, spinnerItemId, it)
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
