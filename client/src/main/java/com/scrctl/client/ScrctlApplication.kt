package com.scrctl.client

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ScrctlApplication: Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .build()
    }
}