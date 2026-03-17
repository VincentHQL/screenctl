package com.scrctl.client.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.scrctl.client.core.database.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * [Room] DAO for [Device] related operations.
 */
@Dao
abstract class DeviceDao : BaseDao<Device> {

    @Transaction
    @Query("SELECT * FROM devices ORDER BY id DESC")
    abstract fun getAll(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE id = :id")
    abstract suspend fun getById(id: Long): Device?

    @Query("SELECT * FROM devices WHERE group_id = :groupId ORDER BY id DESC")
    abstract fun getByGroupId(groupId: Long): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE id = :id")
    abstract fun observeById(id: Long): Flow<Device?>

    @Query("DELETE FROM devices WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int


    @Query("DELETE FROM devices WHERE group_id = :groupId")
    abstract suspend fun deleteByGroupId(groupId: Long): Int

}
