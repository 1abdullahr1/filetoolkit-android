package com.filetoolkit.app

import android.app.Application
import android.util.Log

class FileToolkitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("FileToolkitApp", "FileToolkit Application initialized")
    }
}
