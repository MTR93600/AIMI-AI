package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_EFFECTIVE_PROFILE_SWITCHES
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import io.reactivex.Maybe
import io.reactivex.Single

@Suppress("FunctionName")
@Dao
internal interface EffectiveProfileSwitchDao : TraceableDao<EffectiveProfileSwitch> {

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id = :id")
    override fun findById(id: Long): EffectiveProfileSwitch?

    @Query("DELETE FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES")
    override fun deleteAllEntries()

    @Query("SELECT id FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE isValid = 1 AND referenceId IS NULL ORDER BY id ASC LIMIT 1")
    fun getOldestEffectiveProfileSwitchRecord(): EffectiveProfileSwitch?

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE :timestamp >= timestamp AND referenceId IS NULL AND isValid = 1 ORDER BY timestamp DESC LIMIT 1")
    fun getEffectiveProfileSwitchActiveAt(timestamp: Long): Maybe<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getEffectiveProfileSwitchDataFromTime(timestamp: Long): Single<List<EffectiveProfileSwitch>>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp BETWEEN :start AND :end AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getEffectiveProfileSwitchDataFromTimeToTime(start: Long, end: Long): Single<List<EffectiveProfileSwitch>>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getEffectiveProfileSwitchDataIncludingInvalidFromTime(timestamp: Long): Single<List<EffectiveProfileSwitch>>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getEffectiveProfileSwitchData(): Single<List<EffectiveProfileSwitch>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<EffectiveProfileSwitch>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<EffectiveProfileSwitch>

    @Query("SELECT * FROM $TABLE_EFFECTIVE_PROFILE_SWITCHES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<EffectiveProfileSwitch>

}