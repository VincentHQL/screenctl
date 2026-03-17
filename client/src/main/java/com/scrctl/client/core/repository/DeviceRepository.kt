package com.scrctl.client.core.repository

import com.scrctl.client.core.database.dao.DeviceDao
import com.scrctl.client.core.database.model.Device
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao
) {
    fun getAllDevices(): Flow<List<Device>> = deviceDao.getAll()

    fun getDevicesByGroupId(groupId: Long): Flow<List<Device>> = deviceDao.getByGroupId(groupId)

    suspend fun getDeviceById(id: Long): Device? = deviceDao.getById(id)

    fun observeDeviceById(id: Long): Flow<Device?> = deviceDao.observeById(id)

    suspend fun insertDevice(device: Device): Long = deviceDao.insert(device)

    suspend fun updateDevice(device: Device) {
        deviceDao.update(device)
    }

    suspend fun deleteDeviceById(id: Long): Int = deviceDao.deleteById(id)

    suspend fun deleteDevicesByGroupId(groupId: Long): Int = deviceDao.deleteByGroupId(groupId)
}
