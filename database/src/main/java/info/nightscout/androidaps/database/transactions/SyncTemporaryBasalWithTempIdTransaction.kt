package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryBasal

/**
 * Creates or updates the TemporaryBasal from pump synchronization
 */
class SyncTemporaryBasalWithTempIdTransaction(
    private val bolus: TemporaryBasal,
    private val newType: TemporaryBasal.Type?
) : Transaction<SyncTemporaryBasalWithTempIdTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        bolus.interfaceIDs.temporaryId ?: bolus.interfaceIDs.pumpType
        ?: bolus.interfaceIDs.pumpSerial ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.temporaryBasalDao.findByPumpTempIds(bolus.interfaceIDs.temporaryId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
        if (current != null) {
            current.timestamp = bolus.timestamp
            current.rate = bolus.rate
            current.duration = bolus.duration
            current.isAbsolute = bolus.isAbsolute
            current.type = newType ?: current.type
            current.interfaceIDs.pumpId = bolus.interfaceIDs.pumpId
            database.temporaryBasalDao.updateExistingEntry(current)
            result.updated.add(current)
        }
        return result
    }

    class TransactionResult {

        val updated = mutableListOf<TemporaryBasal>()
    }
}