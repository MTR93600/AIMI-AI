package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.Bolus

/**
 * Creates or updates the Bolus from pump synchronization
 */
class SyncBolusWithTempIdTransaction(
    private val bolus: Bolus,
    private val newType: Bolus.Type?
) : Transaction<SyncBolusWithTempIdTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        bolus.interfaceIDs.temporaryId ?: bolus.interfaceIDs.pumpType ?: bolus.interfaceIDs.pumpSerial ?:
            throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.bolusDao.findByPumpTempIds(bolus.interfaceIDs.temporaryId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
        if (current != null && !current.contentToBeAdded(bolus)) {
            current.timestamp = bolus.timestamp
            current.amount = bolus.amount
            current.type = if(current.isSMBorBasal()) current.type else  newType?: current.type
            current.interfaceIDs.pumpId = bolus.interfaceIDs.pumpId
            if (database.bolusDao.updateExistingEntry(current) >0 ) {
                result.updated.add(current)
            }
        } else if(current == null && bolus.interfaceIDs.pumpType == InterfaceIDs.PumpType.MEDLINK_MEDTRONIC_554_754_VEO) {
            database.bolusDao.insertNewEntry(bolus)
            result.inserted.add(bolus)
        }
        return result
    }

    class TransactionResult {
        val inserted = mutableListOf<Bolus>()
        val updated = mutableListOf<Bolus>()
    }
}