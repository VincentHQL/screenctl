package com.scrctl.client.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.scrctl.client.core.database.model.Group
import kotlinx.coroutines.flow.Flow

/**
 * [Room] DAO for [Group] related operations.
 */
@Dao
abstract class GroupDao : BaseDao<Group> {

    @Transaction
    @Query("SELECT * FROM `groups` ORDER BY id DESC")
    abstract fun getAll(): Flow<List<Group>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    abstract suspend fun getById(id: Long): Group?

    @Query("DELETE FROM `groups` WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int
}
