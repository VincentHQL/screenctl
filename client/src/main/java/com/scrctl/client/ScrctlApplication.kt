package com.scrctl.client

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.scrctl.client.core.devicemanager.DeviceManager
import javax.inject.Inject

@HiltAndroidApp
class ScrctlApplication: Application(), ImageLoaderFactory {

    @Inject
    lateinit var deviceManager: DeviceManager

    override fun onCreate() {
        super.onCreate()
        deviceManager.start()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .build()
    }
}