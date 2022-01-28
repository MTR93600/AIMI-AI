package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.ExtendedBolus

class InvalidateExtendedBolusTransaction(val id: Long) : Transaction<InvalidateExtendedBolusTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()
        val extendedBolus = database.extendedBolusDao.findById(id)
            ?: throw IllegalArgumentException("There is no such Extended Bolus with the specified ID.")
        extendedBolus.isValid = false
        database.extendedBolusDao.updateExistingEntry(extendedBolus)
        result.invalidated.add(extendedBolus)
        return result
    }

    class TransactionResult {

        val invalidated = mutableListOf<ExtendedBolus>()
    }
}