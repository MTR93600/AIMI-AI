package info.nightscout.database.impl.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import info.nightscout.database.entities.TABLE_USER_ENTRY
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Sources
import io.reactivex.rxjava3.core.Single

@Dao
interface UserEntryDao {

    @Insert
    fun insert(userEntry: UserEntry)

    @Query("DELETE FROM $TABLE_USER_ENTRY WHERE timestamp < :than")
    fun deleteOlderThan(than: Long): Int

    @Query("SELECT * FROM $TABLE_USER_ENTRY ORDER BY id DESC")
    fun getAll(): Single<List<UserEntry>>

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE timestamp >= :timestamp ORDER BY id DESC")
    fun getUserEntryDataFromTime(timestamp: Long): Single<List<UserEntry>>

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE timestamp >= :timestamp AND source != :excludeSource ORDER BY id DESC")
    fun getUserEntryFilteredDataFromTime(excludeSource: Sources, timestamp: Long): Single<List<UserEntry>>

    @Query("SELECT * FROM $TABLE_USER_ENTRY WHERE timestamp >= :timestamp and note is not null ORDER BY id DESC")
    fun getUserEntryDataWithNotesFromTime(timestamp: Long): Single<List<UserEntry>>
}