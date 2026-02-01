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

    @Transaction
    @Query("SELECT * FROM devices WHERE group_id = :groupId ORDER BY id DESC")
    abstract fun getByGroupId(groupId: Long): Flow<List<Device>>
    
    @Query("SELECT * FROM devices WHERE id = :id")
    abstract suspend fun getById(id: Long): Device?

    @Transaction
    @Query("SELECT * FROM devices WHERE id = :id")
    abstract fun observeById(id: Long): Flow<Device?>

    @Query(
        """
        UPDATE devices
        SET connection_state = :state,
            connection_error = :error,
            connection_updated_at = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun updateConnectionState(
        id: Long,
        state: String,
        error: String,
        updatedAt: Long,
    ): Int
    
    @Query("DELETE FROM devices WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query(
        """
        UPDATE devices
        SET connection_state = :state,
            connection_error = :error,
            connection_updated_at = :updatedAt
        WHERE group_id = :groupId
        """
    )
    abstract suspend fun markGroupDevicesConnectionState(
        groupId: Long,
        state: String,
        error: String,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM devices WHERE group_id = :groupId")
    abstract suspend fun deleteByGroupId(groupId: Long): Int

    @Transaction
    open suspend fun deleteByIdWithDisconnectMark(id: Long): Int {
        updateConnectionState(
            id = id,
            state = "DISCONNECTED",
            error = "",
            updatedAt = System.currentTimeMillis(),
        )
        return deleteById(id)
    }

    @Transaction
    open suspend fun deleteByGroupIdWithDisconnectMark(groupId: Long): Int {
        markGroupDevicesConnectionState(
            groupId = groupId,
            state = "DISCONNECTED",
            error = "",
            updatedAt = System.currentTimeMillis(),
        )
        return deleteByGroupId(groupId)
    }
}
