package com.scrctl.client.core.repository

import com.scrctl.client.core.database.dao.GroupDao
import com.scrctl.client.core.database.model.Group
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: GroupDao
) {
    fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAll()
    }
    
    suspend fun getGroupById(id: Long): Group? {
        return groupDao.getById(id)
    }
    
    suspend fun insertGroup(group: Group): Long {
        return groupDao.insert(group)
    }
    
    suspend fun updateGroup(group: Group) {
        groupDao.update(group)
    }
    
    suspend fun deleteGroup(group: Group): Int {
        return groupDao.delete(group)
    }
    
    suspend fun deleteGroupById(id: Long): Int {
        return groupDao.deleteById(id)
    }
}
