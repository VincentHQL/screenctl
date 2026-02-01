package com.scrctl.client.di

import com.scrctl.client.core.devicemanager.DeviceManager
import com.scrctl.client.core.devicemanager.DeviceManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceManagerModule {

    @Binds
    @Singleton
    abstract fun bindDeviceManager(impl: DeviceManagerImpl): DeviceManager
}
