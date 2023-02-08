package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.DeviceStatus

class UpdateNsIdDeviceStatusTransaction(private val deviceStatuses: List<DeviceStatus>) : Transaction<UpdateNsIdDeviceStatusTransaction.TransactionResult>() {

    val result = TransactionResult()
    override fun run(): TransactionResult {
        for (deviceStatus in deviceStatuses) {
            val current = database.deviceStatusDao.findById(deviceStatus.id)
            if (current != null && current.interfaceIDs.nightscoutId != deviceStatus.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = deviceStatus.interfaceIDs.nightscoutId
                database.deviceStatusDao.update(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<DeviceStatus>()
    }
}