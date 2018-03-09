package com.oksisi213.servicetest

import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.IBinder
import android.util.Log

class MyService : Service() {

	override fun onBind(intent: Intent): IBinder? = null


	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.e(TAG, "onStartCommand")
		return Service.START_STICKY
	}

	override fun onCreate() {
		super.onCreate()
		Log.e(TAG, "onCreate")
	}

	override fun onDestroy() {
		super.onDestroy()
		Log.e(TAG, "onDestory")
	}
}
