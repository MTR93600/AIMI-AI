package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.BolusCalculatorResult

/**
 * Sync the BolusCalculatorResult from NS
 */
class SyncNsBolusCalculatorResultTransaction(private val bolusCalculatorResult: BolusCalculatorResult) :
    Transaction<SyncNsBolusCalculatorResultTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val current: BolusCalculatorResult? =
            bolusCalculatorResult.interfaceIDs.nightscoutId?.let {
                database.bolusCalculatorResultDao.findByNSId(it)
            }

        if (current != null) {
            // nsId exists, allow only invalidation
            if (current.isValid && !bolusCalculatorResult.isValid) {
                current.isValid = false
                database.bolusCalculatorResultDao.updateExistingEntry(current)
                result.invalidated.add(current)
            }
            return result
        }

        // not known nsId
        val existing = database.bolusCalculatorResultDao.findByTimestamp(bolusCalculatorResult.timestamp)
        if (existing != null && existing.interfaceIDs.nightscoutId == null) {
            // the same record, update nsId only
            existing.interfaceIDs.nightscoutId = bolusCalculatorResult.interfaceIDs.nightscoutId
            existing.isValid = bolusCalculatorResult.isValid
            database.bolusCalculatorResultDao.updateExistingEntry(existing)
            result.updatedNsId.add(existing)
        } else {
            database.bolusCalculatorResultDao.insertNewEntry(bolusCalculatorResult)
            result.inserted.add(bolusCalculatorResult)
        }
        return result

    }

    class TransactionResult {

        val updatedNsId = mutableListOf<BolusCalculatorResult>()
        val inserted = mutableListOf<BolusCalculatorResult>()
        val invalidated = mutableListOf<BolusCalculatorResult>()
    }
}