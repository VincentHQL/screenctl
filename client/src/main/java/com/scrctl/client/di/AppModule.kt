package com.scrctl.client.di

import android.content.Context
import androidx.room.Room
import com.scrctl.client.core.Dispatcher
import com.scrctl.client.core.ScrctlDispatchers
import com.scrctl.client.core.database.ScrctlDatabase
import com.scrctl.client.core.database.dao.DeviceDao
import com.scrctl.client.core.database.dao.GroupDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.scrctl.client.BuildConfig
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ScrctlDatabase {
        return Room.databaseBuilder(ctx, ScrctlDatabase::class.java, "scrctl.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideGroupDao(db: ScrctlDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideDeviceDao(db: ScrctlDatabase): DeviceDao = db.deviceDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Dispatcher(ScrctlDispatchers.IO)
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
}