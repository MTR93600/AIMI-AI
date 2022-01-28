package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.Bolus

/**
 * Creates or updates the Bolus from pump synchronization
 */
class InsertBolusWithTempIdTransaction(
    private val bolus: Bolus
) : Transaction<InsertBolusWithTempIdTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        bolus.interfaceIDs.temporaryId ?: bolus.interfaceIDs.pumpType ?: bolus.interfaceIDs.pumpSerial ?:
            throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.bolusDao.findByPumpTempIds(bolus.interfaceIDs.temporaryId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
        if (current == null) {
            database.bolusDao.insert(bolus)
            result.inserted.add(bolus)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<Bolus>()
    }
}