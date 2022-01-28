package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_PROFILE_SWITCHES
import info.nightscout.androidaps.database.daos.workaround.ProfileSwitchDaoWorkaround
import info.nightscout.androidaps.database.data.checkSanity
import info.nightscout.androidaps.database.entities.ProfileSwitch
import io.reactivex.Maybe
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface ProfileSwitchDao : ProfileSwitchDaoWorkaround {

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id = :id")
    override fun findById(id: Long): ProfileSwitch?

    @Query("DELETE FROM $TABLE_PROFILE_SWITCHES")
    override fun deleteAllEntries()

    @Query("SELECT id FROM $TABLE_PROFILE_SWITCHES ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): ProfileSwitch?

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE timestamp <= :timestamp AND (timestamp + duration) > :timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getTemporaryProfileSwitchActiveAt(timestamp: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE timestamp <= :timestamp AND  duration = 0 AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getPermanentProfileSwitchActiveAt(timestamp: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getAllProfileSwitches(): Single<List<ProfileSwitch>>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getProfileSwitchDataIncludingInvalidFromTime(timestamp: Long): Single<List<ProfileSwitch>>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getProfileSwitchDataFromTime(timestamp: Long): Single<List<ProfileSwitch>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_PROFILE_SWITCHES WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<ProfileSwitch>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<ProfileSwitch>

    @Query("SELECT * FROM $TABLE_PROFILE_SWITCHES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<ProfileSwitch>
}

internal fun ProfileSwitchDao.insertNewEntryImpl(entry: ProfileSwitch): Long {
    if (!entry.basalBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for basal blocks.")
    if (!entry.icBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for IC blocks.")
    if (!entry.isfBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for ISF blocks.")
    if (!entry.targetBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for target blocks.")
    return (this as TraceableDao<ProfileSwitch>).insertNewEntryImpl(entry)
}

internal fun ProfileSwitchDao.updateExistingEntryImpl(entry: ProfileSwitch): Long {
    if (!entry.basalBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for basal blocks.")
    if (!entry.icBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for IC blocks.")
    if (!entry.isfBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for ISF blocks.")
    if (!entry.targetBlocks.checkSanity()) throw IllegalArgumentException("Sanity check failed for target blocks.")
    return (this as TraceableDao<ProfileSwitch>).updateExistingEntryImpl(entry)
}