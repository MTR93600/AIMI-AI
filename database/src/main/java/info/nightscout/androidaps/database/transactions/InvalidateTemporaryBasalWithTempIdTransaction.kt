package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.TemporaryBasal

class InvalidateTemporaryBasalWithTempIdTransaction(val tempId: Long) : Transaction<InvalidateTemporaryBasalWithTempIdTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val temporaryBasal = database.temporaryBasalDao.findByTempId(tempId)
            ?: throw IllegalArgumentException("There is no such Temporary Basal with the specified temp ID.")
        temporaryBasal.isValid = false
        database.temporaryBasalDao.updateExistingEntry(temporaryBasal)
        result.invalidated.add(temporaryBasal)
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<TemporaryBasal>()
    }
}