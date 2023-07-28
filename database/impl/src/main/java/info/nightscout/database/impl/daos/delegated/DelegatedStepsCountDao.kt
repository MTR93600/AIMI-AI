package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.entities.StepsCount
import info.nightscout.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.stepsCountDao

internal class DelegatedStepsCountDao(
    changes: MutableList<DBEntry>,
    private val dao: stepsCountDao): DelegatedDao(changes), stepsCountDao by dao {

    override fun insertNewEntry(entry: StepsCount): Long {
        changes.add(entry)
        return dao.insertNewEntry(entry)
    }

    override fun updateExistingEntry(entry: StepsCount): Long {
        changes.add(entry)
        return dao.updateExistingEntry(entry)
    }
}
