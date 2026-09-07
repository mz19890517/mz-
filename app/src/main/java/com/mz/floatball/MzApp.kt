package com.mz.floatball

import android.app.Application

class MzApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        MzShizuku.init(this)
    }
    companion object {
        lateinit var instance: MzApp
            private set
    }
}
