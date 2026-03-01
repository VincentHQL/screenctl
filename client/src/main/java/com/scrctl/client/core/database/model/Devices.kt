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
)