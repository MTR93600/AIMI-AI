package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.TemporaryBasal

/**
 * Creates or updates the TemporaryBasal from pump synchronization
 */
class InsertTemporaryBasalWithTempIdTransaction(private val temporaryBasal: TemporaryBasal
) : Transaction<InsertTemporaryBasalWithTempIdTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        temporaryBasal.interfaceIDs.temporaryId ?: temporaryBasal.interfaceIDs.pumpType
        ?: temporaryBasal.interfaceIDs.pumpSerial
        ?: throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.temporaryBasalDao.findByPumpTempIds(temporaryBasal.interfaceIDs.temporaryId!!, temporaryBasal.interfaceIDs.pumpType!!, temporaryBasal.interfaceIDs.pumpSerial!!)
        if (current == null) {
            temporaryBasal.id = database.temporaryBasalDao.insert(temporaryBasal)
            result.inserted.add(temporaryBasal)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<TemporaryBasal>()
    }
}