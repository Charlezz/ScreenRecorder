package com.oksisi213.servicetest

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		start_second.setOnClickListener {
			startActivity(Intent(this@MainActivity, SecondActivity::class.java))
		}

		start_service.setOnClickListener {
			startService(Intent(this@MainActivity, MyService::class.java))
		}

		stop_service.setOnClickListener {
			stopService(Intent(this@MainActivity, MyService::class.java))
		}
	}
}
