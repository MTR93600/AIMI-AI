package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.BolusCalculatorResult

/**
 * Creates or updates the BolusCalculatorResult
 */
class InsertOrUpdateBolusCalculatorResultTransaction(
    private val bolusCalculatorResult: BolusCalculatorResult
) : Transaction<InsertOrUpdateBolusCalculatorResultTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val current = database.bolusCalculatorResultDao.findById(bolusCalculatorResult.id)
        if (current == null) {
            database.bolusCalculatorResultDao.insertNewEntry(bolusCalculatorResult)
            result.inserted.add(bolusCalculatorResult)
        } else {
            database.bolusCalculatorResultDao.updateExistingEntry(bolusCalculatorResult)
            result.updated.add(bolusCalculatorResult)
        }
        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<BolusCalculatorResult>()
        val updated = mutableListOf<BolusCalculatorResult>()
    }
}