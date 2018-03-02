package com.oksisi213.screenrecorder

import android.util.Log
import java.nio.ByteBuffer

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 3. 2..
 */
class ByteBufferPool constructor(max: Int, bufferSize: Int) : CircularQueue<ByteBuffer>(max) {
	val TAG = ByteBufferPool::class.java.simpleName

	init {
		for (i in 0..max) {
			queueArray.add(ByteBuffer.allocate(bufferSize))
		}
	}

	// test case
	//1. inputBuffer 사이즈가 더 클떄
	//2. outputBuffer 사이즈가 더 클때

	fun read(inputBuffer: ByteBuffer): Int {
		val offset = inputBuffer.position()
		val bufferSize = inputBuffer.limit()



		while (true) {
			if (inputBuffer.remaining() > peek().remaining()) {
				val outputBuffer = remove()
				inputBuffer.put(outputBuffer)
				if (!inputBuffer.hasRemaining()) {//no more inputbuffer remaining space
					break
				}
				if (empty()) {//no output buffer
					break
				}
			} else {
				Log.e(TAG, "peek remaining1:${peek().remaining()}")
				peek().get(inputBuffer.array(), peek().position(), inputBuffer.limit())
				Log.e(TAG, "peek remaining2:${peek().remaining()}")
				//todo 계산 좀 더 해봐야함 휴우..
				if(peek().hasRemaining()){
					continue
				}else if(!empty()){

				}
			}
		}
		return inputBuffer.limit() - inputBuffer.remaining()
	}

}