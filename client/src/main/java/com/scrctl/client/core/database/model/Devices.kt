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
    
    @ColumnInfo(name = "connect_mode")
    var connectMode: Int = 1,

    @ColumnInfo(name = "device_ip")
    var deviceAddr: String = "",

    @ColumnInfo(name = "device_port")
    var devicePort: Int = 5555,

    @ColumnInfo(name = "adb_ip")
    var adbIp: String = "",

    @ColumnInfo(name = "adb_port")
    var adbPort: Int = 5037,

    @ColumnInfo(name = "name")
    var name: String = "",

    @ColumnInfo(name = "connection_state")
    var connectionState: String = "DISCONNECTED",

    @ColumnInfo(name = "connection_error")
    var connectionError: String = "",

    @ColumnInfo(name = "connection_updated_at")
    var connectionUpdatedAt: Long = 0L,
)