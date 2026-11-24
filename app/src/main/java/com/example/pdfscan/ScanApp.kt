package com.example.pdfscan

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ScanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
