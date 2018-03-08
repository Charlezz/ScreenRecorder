package com.charles.streamtest

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.*

class MainActivity : AppCompatActivity() {
	companion object {
		val TAG = MainActivity::class.java.simpleName
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)


		val baos = PipedOutputStream()
		val bais = PipedInputStream(baos)

		var char: Int = 0
		while ({ char = bais.read();char }() != -1) {
			Log.e(TAG, "char=$char")

		}

		val number = 15

		if (!(number >= 1 && number <= 16)) {
			Log.e(TAG, "부적합한 숫자")
			return
		}

		val arr1 = IntArray(number * number) { index ->
			Random(1).nextInt()
		}

		val arr2 = IntArray(number * number) { index ->
			Random(1).nextInt()
		}


		val arr3 = IntArray(number * number)
		for (i in 0 until number * number) {
			var num = arr1[i] or arr2[i]
			arr3[i] = num
		}

		val sb = StringBuilder()
		for (i in 0 until number * number) {

			if (i % number == 0) {

			}

		}


	}
}
