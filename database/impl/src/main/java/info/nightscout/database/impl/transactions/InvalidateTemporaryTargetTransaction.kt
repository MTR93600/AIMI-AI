package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.TemporaryTarget

class InvalidateTemporaryTargetTransaction(val id: Long) : Transaction<InvalidateTemporaryTargetTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val temporaryTarget = database.temporaryTargetDao.findById(id)
            ?: throw IllegalArgumentException("There is no such TemporaryTarget with the specified ID.")
        if (temporaryTarget.isValid) {
            temporaryTarget.isValid = false
            database.temporaryTargetDao.updateExistingEntry(temporaryTarget)
            result.invalidated.add(temporaryTarget)
        }
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<TemporaryTarget>()
    }
}