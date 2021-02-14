package info.nightscout.androidaps.database.daos

import androidx.room.Insert
import androidx.room.Update
import info.nightscout.androidaps.database.daos.workaround.TraceableDaoWorkaround
import info.nightscout.androidaps.database.interfaces.TraceableDBEntry
import io.reactivex.Single

internal interface TraceableDao<T : TraceableDBEntry> : TraceableDaoWorkaround<T> {

    fun findById(id: Long): T?

    fun deleteAllEntries()

    //fun getAllStartingFrom(id: Long): Single<List<T>>

    @Insert
    fun insert(entry: T): Long

    @Update
    fun update(entry: T)
}

/**
 * Inserts a new entry
 * @return The ID of the newly generated entry
 */
//@Transaction
internal fun <T : TraceableDBEntry> TraceableDao<T>.insertNewEntryImpl(entry: T): Long {
    if (entry.id != 0L) throw IllegalArgumentException("ID must be 0.")
    if (entry.version != 0) throw IllegalArgumentException("Version must be 0.")
    if (entry.referenceId != null) throw IllegalArgumentException("Reference ID must be null.")
    if (!entry.foreignKeysValid) throw IllegalArgumentException("One or more foreign keys are invalid (e.g. 0 value).")
    val lastModified = System.currentTimeMillis()
    entry.dateCreated = lastModified
    val id = insert(entry)
    entry.id = id
    return id
}

/**
 * Updates an existing entry
 * @return The ID of the newly generated HISTORIC entry
 */
//@Transaction
internal fun <T : TraceableDBEntry> TraceableDao<T>.updateExistingEntryImpl(entry: T): Long {
    if (entry.id == 0L) throw IllegalArgumentException("ID must not be 0.")
    if (entry.referenceId != null) throw IllegalArgumentException("Reference ID must be null.")
    if (!entry.foreignKeysValid) throw IllegalArgumentException("One or more foreign keys are invalid (e.g. 0 value).")
    val lastModified = System.currentTimeMillis()
    entry.dateCreated = lastModified
    val current = findById(entry.id)
            ?: throw IllegalArgumentException("The entry with the specified ID does not exist.")
    if (current.referenceId != null) throw IllegalArgumentException("The entry with the specified ID is historic and cannot be updated.")
    entry.version = current.version + 1
    update(entry)
    current.referenceId = entry.id
    current.id = 0
    return insert(current)
}