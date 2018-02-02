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

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.util.*

/**
 * @author yrom
 * @version 2017/12/3
 */
class VideoEncoder(private val mConfig: VideoEncodeConfig) : BaseEncoder(mConfig.codecName) {
	private var mSurface: Surface? = null

	/**
	 * @throws NullPointerException if prepare() not call
	 */
	val inputSurface: Surface
		get() = Objects.requireNonNull<Surface>(mSurface, "doesn't prepare()")

	override fun onEncoderConfigured(encoder: MediaCodec) {
		mSurface = encoder.createInputSurface()
		if (VERBOSE) Log.i("@@", "VideoEncoder create input surface: " + mSurface!!)
	}

	override fun createMediaFormat(): MediaFormat {
		return mConfig.toFormat()
	}

	override fun release() {
		if (mSurface != null) {
			mSurface!!.release()
			mSurface = null
		}
		super.release()
	}

	companion object {
		private val VERBOSE = false
	}


}
