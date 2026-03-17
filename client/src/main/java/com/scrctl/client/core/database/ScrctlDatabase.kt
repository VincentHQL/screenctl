package com.scrctl.client.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.scrctl.client.core.database.dao.DeviceDao
import com.scrctl.client.core.database.dao.GroupDao
import com.scrctl.client.core.database.model.Device
import com.scrctl.client.core.database.model.Group

@Database(entities = [Group::class, Device::class], version = 2, exportSchema = false)
abstract class ScrctlDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun deviceDao(): DeviceDao
}