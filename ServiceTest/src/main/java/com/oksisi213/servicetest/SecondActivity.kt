package com.oksisi213.servicetest

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_second.*

class SecondActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_second)

		close.setOnClickListener {
			finish()
		}

		start_service.setOnClickListener {
			startService(Intent(this@SecondActivity, MyService::class.java))
		}

		stop_service.setOnClickListener {
			stopService(Intent(this@SecondActivity, MyService::class.java))
		}
	}
}
