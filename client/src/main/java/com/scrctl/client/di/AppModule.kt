package com.scrctl.client.di

import android.content.Context
import androidx.room.Room
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.database.ScrctlDatabase
import com.scrctl.client.core.database.dao.DeviceDao
import com.scrctl.client.core.database.dao.GroupDao
import com.scrctl.client.core.repository.DeviceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import com.scrctl.client.core.devicemanager.DeviceManager
import com.scrctl.client.core.devicemanager.DeviceManagerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ScrctlDatabase {
        return Room.databaseBuilder(ctx, ScrctlDatabase::class.java, "scrctl.db")
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

    @Provides
    fun provideGroupDao(db: ScrctlDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideDeviceDao(db: ScrctlDatabase): DeviceDao = db.deviceDao()

    @Provides
    @Dispatcher(ScrctlDispatchers.IO)
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideDeviceManager(
        @ApplicationContext ctx: Context,
        deviceDao: DeviceDao,
        @Dispatcher(ScrctlDispatchers.IO) io: CoroutineDispatcher
    ): DeviceManager {
        return DeviceManagerImpl(ctx, deviceDao, io)
    }
}
