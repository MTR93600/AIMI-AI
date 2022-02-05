package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.GlucoseValue

/**
 * Invalidates the GlucoseValue with the specified id
 */
class InvalidateGlucoseValueTransaction(val id: Long) : Transaction<InvalidateGlucoseValueTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val glucoseValue = database.glucoseValueDao.findById(id)
            ?: throw IllegalArgumentException("There is no such GlucoseValue with the specified ID.")
        glucoseValue.isValid = false
        database.glucoseValueDao.updateExistingEntry(glucoseValue)
        result.invalidated.add(glucoseValue)
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<GlucoseValue>()
    }
}