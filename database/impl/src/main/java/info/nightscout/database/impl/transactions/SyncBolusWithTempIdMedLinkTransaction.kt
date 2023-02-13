package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.embedments.InterfaceIDs

/**
 * Creates or updates the Bolus from pump synchronization
 */
class SyncBolusWithTempIdMedLinkTransaction(
    private val bolus: Bolus,
    private val newType: Bolus.Type?
) : Transaction<SyncBolusWithTempIdMedLinkTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        bolus.interfaceIDs.temporaryId ?: bolus.interfaceIDs.pumpType ?: bolus.interfaceIDs.pumpSerial ?:
            throw IllegalStateException("Some pump ID is null")
        val result = TransactionResult()
        val current = database.bolusDao.findByPumpTempIds(bolus.interfaceIDs.temporaryId!!, bolus.interfaceIDs.pumpType!!, bolus.interfaceIDs.pumpSerial!!)
        if(current != null && current.type != Bolus.Type.NORMAL && bolus.type == Bolus.Type.NORMAL){
            bolus.type = current.type
        }
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