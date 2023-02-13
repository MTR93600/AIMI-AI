package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.MedLinkDao
import info.nightscout.database.entities.MedLinkConfig
import info.nightscout.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.delegated.DelegatedDao

internal class DelegatedMedLinkDao(changes: MutableList<DBEntry>, private val dao: MedLinkDao) : DelegatedDao(changes), MedLinkDao by dao {

    override fun insertNewEntry(medLinkConfig: MedLinkConfig): Long {
        changes.add(medLinkConfig)
        return dao.insertNewEntry(medLinkConfig)
    }

    override fun updateExistingEntry(medLinkConfig: MedLinkConfig): Long {
        changes.add(medLinkConfig)
        return dao.updateExistingEntry(medLinkConfig)
    }
}