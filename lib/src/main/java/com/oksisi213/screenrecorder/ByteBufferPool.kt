package com.oksisi213.screenrecorder

import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 3. 6..
 */
open class ByteBufferPool constructor(count: Int, bufferSize: Int) {
	val TAG = ByteBufferPool::class.java.simpleName
	private val buffers = ArrayList<ByteBuffer>()

	private val reentrantLock = ReentrantLock()

	init {
		for (i in 0 until count) {
			buffers.add(ByteBuffer.allocate(bufferSize))
		}
	}

	fun put(byteArray: ByteArray) {
		reentrantLock.lock()
		if (buffers.isNotEmpty()) {
			val buffer = buffers[buffers.size - 1]
			buffer.clear()
			buffer.put(byteArray)
			buffers.add(0, buffer)
		}
		reentrantLock.unlock()
	}

	fun get(): ByteBuffer? {
		reentrantLock.lock()
		return if (buffers.isNotEmpty()) {
			buffers[0]
		} else {
			null
		}
		reentrantLock.unlock()
	}


}



