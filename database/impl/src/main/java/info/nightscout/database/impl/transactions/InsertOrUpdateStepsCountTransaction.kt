package info.nightscout.database.impl.transactions

import info.nightscout.database.entities.StepsCount

class InsertOrUpdateStepsCountTransaction(private val stepsCount: StepsCount):
    Transaction<InsertOrUpdateStepsCountTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val existing = if (stepsCount.id == 0L) null else database.stepsCountDao.findById(stepsCount.id)
         return if (existing == null) {
            database.stepsCountDao.insertNewEntry(stepsCount).let {
                TransactionResult(listOf(stepsCount), emptyList()) }
        } else {
            database.stepsCountDao.updateExistingEntry(stepsCount)
            TransactionResult(emptyList(), listOf(stepsCount))
         }
    }

    data class TransactionResult(val inserted: List<StepsCount>, val updated: List<StepsCount>)
}
