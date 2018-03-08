package com.oksisi213.screenrecorder

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 3. 2..
 */
open class CircularQueue<T> {
	protected var front: Int = 0
	protected var rear: Int = 0
	protected val maxSize: Int
	protected val queueArray: ArrayList<T>

	constructor(maxSize: Int) {
		this.front = 0
		this.rear = -1
		this.maxSize = maxSize + 1
		this.queueArray = ArrayList()
	}

	fun empty(): Boolean {
		return (front == rear + 1) || (front + maxSize - 1 == rear)
	}

	fun full(): Boolean {
		return (rear == maxSize - 1) || (front + maxSize - 2 == rear)
	}

	fun insert(item: T) {
		if (full()) throw ArrayIndexOutOfBoundsException()
		if (rear == maxSize - 1) {
			rear = -1
		}
		queueArray[++rear] = item
	}

	fun peek(): T {
		if (empty()) throw  ArrayIndexOutOfBoundsException()
		return queueArray[front]
	}

	fun remove(): T {
		val item = peek()
		front++

		if (front == maxSize) {
			front = 0
		}
		return item
	}

	fun first(): T {
		return queueArray[this.front]
	}

	fun last(): T {
		return queueArray[this.rear]
	}

}

