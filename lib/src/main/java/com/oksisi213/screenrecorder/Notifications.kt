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

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION_CODES.O
import android.os.SystemClock
import android.text.format.DateUtils

/**
 * @author yrom
 * @version 2017/12/1
 */
internal class Notifications(context: Context) : ContextWrapper(context) {

	private var mLastFiredTime: Long = 0
	private var mManager: NotificationManager? = null
	private var mStopAction: Notification.Action? = null
	private var mBuilder: Notification.Builder? = null

	private val builder: Notification.Builder?
		get() {
			if (mBuilder == null) {
				val builder = Notification.Builder(this)
						.setContentTitle("Recording...")
						.setOngoing(true)
						.setLocalOnly(true)
						.setOnlyAlertOnce(true)
						.addAction(stopAction())
						.setWhen(System.currentTimeMillis())
						.setSmallIcon(R.drawable.ic_stat_recording)
				if (Build.VERSION.SDK_INT >= O) {
					builder.setChannelId(CHANNEL_ID)
							.setUsesChronometer(true)
				}
				mBuilder = builder
			}
			return mBuilder
		}

	val notificationManager: NotificationManager
		get() {
			if (mManager == null) {
				mManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			}
			return mManager as NotificationManager
		}

	init {
		if (Build.VERSION.SDK_INT >= O) {
			createNotificationChannel()
		}
	}

	fun recording(timeMs: Long) {
		if (SystemClock.elapsedRealtime() - mLastFiredTime < 1000) {
			return
		}
		val notification = builder
				?.setContentText("Length: " + DateUtils.formatElapsedTime(timeMs / 1000))
				?.build()
		notificationManager.notify(id, notification)
		mLastFiredTime = SystemClock.elapsedRealtime()
	}

	@TargetApi(O)
	private fun createNotificationChannel() {
		val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
		channel.setShowBadge(false)
		notificationManager.createNotificationChannel(channel)
	}

	private fun stopAction(): Notification.Action {
		if (mStopAction == null) {
			val intent = Intent(ScreenRecorderActivity.ACTION_STOP).setPackage(packageName)
			val pendingIntent = PendingIntent.getBroadcast(this, 1,
					intent, PendingIntent.FLAG_ONE_SHOT)
			mStopAction = Notification.Action(android.R.drawable.ic_media_pause, "Stop", pendingIntent)
		}
		return mStopAction as Notification.Action
	}

	fun clear() {
		mLastFiredTime = 0
		mBuilder = null
		mStopAction = null
		notificationManager.cancelAll()
	}

	companion object {
		private val id = 0x1fff
		private val CHANNEL_ID = "Recording"
		private val CHANNEL_NAME = "Screen Recorder Notifications"
	}
}
