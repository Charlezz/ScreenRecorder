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

import android.media.MediaFormat
import java.util.*

/**
 * @author yrom
 * @version 2017/12/3
 */
class AudioEncodeConfig(val codecName: String, mimeType: String,
						val bitRate: Int, val sampleRate: Int, val channelCount: Int, val profile: Int) {
	val mimeType: String

	init {
		this.mimeType = Objects.requireNonNull(mimeType)
	}

	fun toFormat(): MediaFormat {
		val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
		//format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096 * 4);
		return format
	}

	override fun toString(): String {
		return "AudioEncodeConfig{" +
				"codecName='" + codecName + '\''.toString() +
				", mimeType='" + mimeType + '\''.toString() +
				", bitRate=" + bitRate +
				", sampleRate=" + sampleRate +
				", channelCount=" + channelCount +
				", profile=" + profile +
				'}'.toString()
	}
}
