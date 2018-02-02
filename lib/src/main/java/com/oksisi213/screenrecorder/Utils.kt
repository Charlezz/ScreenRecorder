/*
 * Copyright (c) 2017 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oksisi213.screenrecorder

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.AsyncTask
import android.util.SparseArray
import java.lang.reflect.Modifier
import java.util.*

object Utils {


	var sAACProfiles = SparseArray<String>()
	var sAVCProfiles = SparseArray<String>()
	var sAVCLevels = SparseArray<String>()


	var sColorFormats = SparseArray<String>()

	interface Callback {
		fun onResult(infos: Array<MediaCodecInfo>)
	}

	internal class EncoderFinder(private val func: Callback) : AsyncTask<String, Void, Array<MediaCodecInfo>>() {

		override fun doInBackground(vararg mimeTypes: String): Array<MediaCodecInfo> {
			return findEncodersByType(mimeTypes[0])
		}

		override fun onPostExecute(mediaCodecInfos: Array<MediaCodecInfo>) {
			func.onResult(mediaCodecInfos)
		}
	}

	fun findEncodersByTypeAsync(mimeType: String, callback: Callback) {
		EncoderFinder(callback).execute(mimeType)
	}

	/**
	 * Find an encoder supported specified MIME type
	 *
	 * @return Returns empty array if not found any encoder supported specified MIME type
	 */
	fun findEncodersByType(mimeType: String): Array<MediaCodecInfo> {
		val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
		val infos = ArrayList<MediaCodecInfo>()
		for (info in codecList.codecInfos) {
			if (!info.isEncoder) {
				continue
			}
			try {
				val cap = info.getCapabilitiesForType(mimeType) ?: continue
			} catch (e: IllegalArgumentException) {
				// unsupported
				continue
			}

			infos.add(info)
		}

		return infos.toTypedArray()
	}


	/**
	 * @param avcProfileLevel AVC CodecProfileLevel
	 */
	fun avcProfileLevelToString(avcProfileLevel: MediaCodecInfo.CodecProfileLevel): String {
		if (sAVCProfiles.size() == 0 || sAVCLevels.size() == 0) {
			initProfileLevels()
		}
		var profile: String? = null
		var level: String? = null
		var i = sAVCProfiles.indexOfKey(avcProfileLevel.profile)
		if (i >= 0) {
			profile = sAVCProfiles.valueAt(i)
		}

		i = sAVCLevels.indexOfKey(avcProfileLevel.level)
		if (i >= 0) {
			level = sAVCLevels.valueAt(i)
		}

		if (profile == null) {
			profile = avcProfileLevel.profile.toString()
		}
		if (level == null) {
			level = avcProfileLevel.level.toString()
		}
		return profile + '-'.toString() + level
	}

	fun aacProfiles(): Array<String?> {
		if (sAACProfiles.size() == 0) {
			initProfileLevels()
		}
		val profiles = arrayOfNulls<String>(sAACProfiles.size())
		for (i in 0 until sAACProfiles.size()) {
			profiles[i] = sAACProfiles.valueAt(i)
		}
		return profiles
	}

	fun toProfileLevel(str: String): MediaCodecInfo.CodecProfileLevel? {
		if (sAVCProfiles.size() == 0 || sAVCLevels.size() == 0 || sAACProfiles.size() == 0) {
			initProfileLevels()
		}
		var profile = str
		var level: String? = null
		val i = str.indexOf('-')
		if (i > 0) { // AVC profile has level
			profile = str.substring(0, i)
			level = str.substring(i + 1)
		}

		val res = MediaCodecInfo.CodecProfileLevel()
		if (profile.startsWith("AVC")) {
			res.profile = keyOfValue(sAVCProfiles, profile)
		} else if (profile.startsWith("AAC")) {
			res.profile = keyOfValue(sAACProfiles, profile)
		} else {
			try {
				res.profile = Integer.parseInt(profile)
			} catch (e: NumberFormatException) {
				return null
			}

		}

		if (level != null) {
			if (level.startsWith("AVC")) {
				res.level = keyOfValue(sAVCLevels, level)
			} else {
				try {
					res.level = Integer.parseInt(level)
				} catch (e: NumberFormatException) {
					return null
				}

			}
		}

		return if (res.profile > 0 && res.level >= 0) res else null
	}

	private fun <T> keyOfValue(array: SparseArray<T>, value: T): Int {
		val size = array.size()
		for (i in 0 until size) {
			val t = array.valueAt(i)
			if (t === value || t == value) {
				return array.keyAt(i)
			}
		}
		return -1
	}

	private fun initProfileLevels() {
		val fields = MediaCodecInfo.CodecProfileLevel::class.java.fields
		for (f in fields) {
			if (f.modifiers and (Modifier.STATIC or Modifier.FINAL) == 0) {
				continue
			}
			val name = f.name
			val target: SparseArray<String>
			if (name.startsWith("VideoProfile")) {
				target = sAVCProfiles
			} else if (name.startsWith("AVCLevel")) {
				target = sAVCLevels
			} else if (name.startsWith("AACObject")) {
				target = sAACProfiles
			} else {
				continue
			}
			try {
				target.put(f.getInt(null), name)
			} catch (e: IllegalAccessException) {
				//ignored
			}

		}
	}

	fun toHumanReadable(colorFormat: Int): String {
		if (sColorFormats.size() == 0) {
			initColorFormatFields()
		}
		val i = sColorFormats.indexOfKey(colorFormat)
		return if (i >= 0) sColorFormats.valueAt(i) else "0x" + Integer.toHexString(colorFormat)
	}

	fun toColorFormat(str: String): Int {
		if (sColorFormats.size() == 0) {
			initColorFormatFields()
		}
		val color = keyOfValue(sColorFormats, str)
		if (color > 0) return color
		return if (str.startsWith("0x")) {
			Integer.parseInt(str.substring(2), 16)
		} else 0
	}

	private fun initColorFormatFields() {
		// COLOR_
		val fields = MediaCodecInfo.CodecCapabilities::class.java.fields
		for (f in fields) {
			if (f.modifiers and (Modifier.STATIC or Modifier.FINAL) == 0) {
				continue
			}
			val name = f.name
			if (name.startsWith("COLOR_")) {
				try {
					val value = f.getInt(null)
					sColorFormats.put(value, name)
				} catch (e: IllegalAccessException) {
					// ignored
				}

			}
		}

	}
}
