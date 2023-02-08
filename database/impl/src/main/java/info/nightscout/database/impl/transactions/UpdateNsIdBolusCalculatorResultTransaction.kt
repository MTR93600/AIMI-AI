package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.BolusCalculatorResult

class UpdateNsIdBolusCalculatorResultTransaction(private val bolusCalculatorResults: List<BolusCalculatorResult>) : Transaction<UpdateNsIdBolusCalculatorResultTransaction.TransactionResult>() {

    val result = TransactionResult()
    override fun run(): TransactionResult {
        for (bolusCalculatorResult in bolusCalculatorResults) {
            val current = database.bolusCalculatorResultDao.findById(bolusCalculatorResult.id)
            if (current != null && current.interfaceIDs.nightscoutId != bolusCalculatorResult.interfaceIDs.nightscoutId) {
                current.interfaceIDs.nightscoutId = bolusCalculatorResult.interfaceIDs.nightscoutId
                database.bolusCalculatorResultDao.updateExistingEntry(current)
                result.updatedNsId.add(current)
            }
        }
        return result
    }

    class TransactionResult {

        val updatedNsId = mutableListOf<BolusCalculatorResult>()
    }
}