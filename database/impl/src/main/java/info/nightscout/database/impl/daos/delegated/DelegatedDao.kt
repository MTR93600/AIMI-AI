package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.entities.interfaces.DBEntry

/**
 * A DAO that adds updated or inserted entries to a list
 */
internal abstract class DelegatedDao(protected val changes: MutableList<DBEntry>)