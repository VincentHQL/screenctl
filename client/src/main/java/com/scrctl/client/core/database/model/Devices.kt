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

    @ColumnInfo(name = "stream_video_enabled")
    var streamVideoEnabled: Boolean = true,

    @ColumnInfo(name = "stream_audio_enabled")
    var streamAudioEnabled: Boolean = true,

    @ColumnInfo(name = "stream_require_audio")
    var streamRequireAudio: Boolean = false,

    @ColumnInfo(name = "stream_video_bitrate")
    var streamVideoBitRate: Int = 8_000_000,

    @ColumnInfo(name = "stream_audio_bitrate")
    var streamAudioBitRate: Int = 128_000,

    @ColumnInfo(name = "stream_max_size")
    var streamMaxSize: Int = 0,

    @ColumnInfo(name = "stream_video_codec")
    var streamVideoCodec: String = "h264",

    @ColumnInfo(name = "stream_audio_codec")
    var streamAudioCodec: String = "aac",
)