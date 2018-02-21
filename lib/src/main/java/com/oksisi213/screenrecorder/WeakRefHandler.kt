package com.oksisi213.screenrecorder

import android.os.Handler
import android.os.Looper
import android.os.Message
import java.lang.ref.WeakReference

/**
 * Copyright 2017 Maxst, Inc. All Rights Reserved.
 * Created by charles on 2018. 2. 20..
 */
class WeakRefHandler : Handler {

	private var iMessageListener: IMessageListener? = null

	private var reference: WeakReference<IMessageListener>? = null

	constructor(listener: IMessageListener) : super() {
		reference = WeakReference(listener)
	}

	constructor(looper: Looper, listener: IMessageListener) : super(looper) {
		reference = WeakReference(listener)
	}

	override fun handleMessage(msg: Message) {
		super.handleMessage(msg)
		reference?.get()?.let {
			it.handleMessage(msg)
		}
	}

	interface IMessageListener {
		fun handleMessage(message: Message)
	}

}