package com.maxst.screenrecorder

import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by Charles on 2018. 3. 6..
 */
open class ByteBufferPool constructor(count: Int, bufferSize: Int) {

	val TAG = ByteBufferPool::class.java.simpleName

	private val emptyBuffers = LinkedList<ByteBuffer>()
	private val buffers = LinkedList<ByteBuffer>()

	private val emptyBufLock = ReentrantLock()
	private val bufLock = ReentrantLock()


	init {
		for (i in 0 until count) {
			emptyBuffers.push(ByteBuffer.allocate(bufferSize))
		}
	}

	fun put(byteArray: ByteArray, length: Int) {
		emptyBufLock.lock()
		if (emptyBuffers.isNotEmpty()) {
			val buffer = emptyBuffers.pollLast()
			emptyBufLock.unlock()
			buffer.clear()
			buffer.put(byteArray, 0, length)
			bufLock.lock()
			buffers.push(buffer)
			bufLock.unlock()
		} else {
			emptyBufLock.unlock()
		}
	}

	fun get(): ByteBuffer? {
		bufLock.lock()
		return if (buffers.isNotEmpty()) {
			val buffer = buffers.pollLast()
			bufLock.unlock()
			buffer
		} else {
			bufLock.unlock()
			null
		}
	}

	fun release(buffer: ByteBuffer) {
		emptyBufLock.lock()
		emptyBuffers.push(buffer)
		emptyBufLock.unlock()
	}
}



