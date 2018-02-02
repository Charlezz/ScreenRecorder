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

import java.io.IOException

/**
 * @author yrom
 * @version 2017/12/4
 */
interface Encoder {
	@Throws(IOException::class)
	fun prepare()

	fun stop()

	fun release()

	fun setCallback(callback: Callback)

	interface Callback {
		fun onError(encoder: Encoder, exception: Exception)
	}
}
