package com.scrctl.client.core.database.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
@Immutable
data class Device(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "group_id")
    var groupId: Long = -1,

    @ColumnInfo(name = "name")
    var name: String = "",

    @ColumnInfo(name = "device_ip")
    var deviceAddr: String = "",

    @ColumnInfo(name = "device_port")
    var devicePort: Int = 5555,

    @ColumnInfo(name = "model")
    var model: String = "",

    @ColumnInfo(name = "android_version")
    var androidVersion: String = "",

    @ColumnInfo(name = "api_level")
    var apiLevel: String = "",

    @ColumnInfo(name = "brand")
    var brand: String = "",

    @ColumnInfo(name = "manufacturer")
    var manufacturer: String = "",

    @ColumnInfo(name = "serial_number")
    var serialNumber: String = "",

    @ColumnInfo(name = "cpu_abi")
    var cpuAbi: String = "",

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = 0L,
)