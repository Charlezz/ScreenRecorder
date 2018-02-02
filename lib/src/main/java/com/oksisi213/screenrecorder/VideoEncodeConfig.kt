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
import android.media.MediaFormat
import java.util.*

/**
 * @author yrom
 * @version 2017/12/3
 */
class VideoEncodeConfig
/**
 * @param codecName         selected codec name, maybe null
 * @param mimeType          video MIME type, cannot be null
 * @param codecProfileLevel profile level for video encoder nullable
 */
(val width: Int, val height: Int, val bitrate: Int,
 val framerate: Int, val iframeInterval: Int,
 val codecName: String, mimeType: String,
 val codecProfileLevel: MediaCodecInfo.CodecProfileLevel?) {
	val mimeType: String

	init {
		this.mimeType = Objects.requireNonNull(mimeType)
	}

	fun toFormat(): MediaFormat {
		val format = MediaFormat.createVideoFormat(mimeType, width, height)
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
		format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
		format.setInteger(MediaFormat.KEY_FRAME_RATE, framerate)
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval)
		if (codecProfileLevel != null && codecProfileLevel.profile != 0 && codecProfileLevel.level != 0) {
			format.setInteger(MediaFormat.KEY_PROFILE, codecProfileLevel.profile)
			format.setInteger("level", codecProfileLevel.level)
		}
		// maybe useful
		// format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 10_000_000);
		return format
	}

	override fun toString(): String {
		return "VideoEncodeConfig{" +
				"width=" + width +
				", height=" + height +
				", bitrate=" + bitrate +
				", framerate=" + framerate +
				", iframeInterval=" + iframeInterval +
				", codecName='" + codecName + '\''.toString() +
				", mimeType='" + mimeType + '\''.toString() +
				", codecProfileLevel=" + (if (codecProfileLevel == null) "" else Utils.avcProfileLevelToString(codecProfileLevel)) +
				'}'.toString()
	}
}
