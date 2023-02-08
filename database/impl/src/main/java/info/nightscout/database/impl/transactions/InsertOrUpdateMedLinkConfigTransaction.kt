package info.nightscout.androidaps.database.transactions

import info.nightscout.database.entities.MedLinkConfig
import info.nightscout.database.impl.transactions.Transaction

class InsertOrUpdateMedLinkConfigTransaction(private val medLinkConfig: MedLinkConfig) : Transaction<InsertOrUpdateMedLinkConfigTransaction.TransactionResult>() {

    class TransactionResult {

        val inserted = mutableListOf<MedLinkConfig>()
        val updated = mutableListOf<MedLinkConfig>()
    }

    override fun run(): TransactionResult {
        val result = InsertOrUpdateMedLinkConfigTransaction.TransactionResult()
        val current = database.medLinkConfigDao.findById(medLinkConfig.id)
        if (current == null) {
            database.medLinkConfigDao.insertNewEntry(medLinkConfig)
            result.inserted.add(medLinkConfig)
        } else {
            database.medLinkConfigDao.updateExistingEntry(medLinkConfig)
            result.updated.add(medLinkConfig)
        }
        return result
    }
}