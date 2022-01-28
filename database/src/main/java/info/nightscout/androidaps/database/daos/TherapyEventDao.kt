package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_TEMPORARY_TARGETS
import info.nightscout.androidaps.database.TABLE_THERAPY_EVENTS
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.TherapyEvent
import io.reactivex.Maybe
import io.reactivex.Single

@Dao
internal interface TherapyEventDao : TraceableDao<TherapyEvent> {

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id = :id")
    override fun findById(id: Long): TherapyEvent?

    @Query("DELETE FROM $TABLE_THERAPY_EVENTS")
    override fun deleteAllEntries()

    @Query("SELECT id FROM $TABLE_THERAPY_EVENTS ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND timestamp = :timestamp AND referenceId IS NULL")
    fun findByTimestamp(type: TherapyEvent.Type, timestamp: Long): TherapyEvent?

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND referenceId IS NULL")
    fun getValidByType(type: TherapyEvent.Type): List<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSId(nsId: String): TherapyEvent?

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTherapyEventDataFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTherapyEventDataFromTime(timestamp: Long, type: TherapyEvent.Type): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :timestamp AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getTherapyEventDataIncludingInvalidFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE type = :type AND isValid = 1 AND timestamp <= :now ORDER BY id DESC LIMIT 1")
    fun getLastTherapyRecord(type: TherapyEvent.Type, now: Long): Maybe<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun compatGetTherapyEventDataFromTime(timestamp: Long): Single<List<TherapyEvent>>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE timestamp >= :from AND timestamp <= :to AND isValid = 1 AND referenceId IS NULL ORDER BY timestamp ASC")
    fun compatGetTherapyEventDataFromToTime(from: Long, to: Long): Single<List<TherapyEvent>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_THERAPY_EVENTS WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<TherapyEvent>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<TherapyEvent>

    @Query("SELECT * FROM $TABLE_THERAPY_EVENTS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TherapyEvent>
}