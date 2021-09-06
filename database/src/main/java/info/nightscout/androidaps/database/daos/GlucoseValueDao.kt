package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_CARBS
import info.nightscout.androidaps.database.TABLE_GLUCOSE_VALUES
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.GlucoseValue
import io.reactivex.Maybe
import io.reactivex.Single

@Dao
internal interface GlucoseValueDao : TraceableDao<GlucoseValue> {

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :id")
    override fun findById(id: Long): GlucoseValue?

    @Query("DELETE FROM $TABLE_GLUCOSE_VALUES")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC limit 1")
    fun getLast(): Maybe<GlucoseValue>

    @Query("SELECT id FROM $TABLE_GLUCOSE_VALUES ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE nightscoutId = :nsId AND referenceId IS NULL")
    fun findByNSIdMaybe(nsId: String): Maybe<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp = :timestamp AND sourceSensor = :sourceSensor AND referenceId IS NULL")
    fun findByTimestampAndSensor(timestamp: Long, sourceSensor: GlucoseValue.SourceSensor): GlucoseValue?

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp >= :timestamp AND isValid = 1 AND referenceId IS NULL AND value >= 39 ORDER BY timestamp ASC")
    fun compatGetBgReadingsDataFromTime(timestamp: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE timestamp BETWEEN :start AND :end AND isValid = 1 AND referenceId IS NULL AND value >= 39 ORDER BY timestamp ASC")
    fun compatGetBgReadingsDataFromTime(start: Long, end: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :lastId AND referenceId IS NULL ORDER BY timestamp ASC")
    fun getDataFromId(lastId: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id >= :id")
    fun getAllStartingFrom(id: Long): Single<List<GlucoseValue>>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE referenceId = :id ORDER BY id DESC LIMIT 1")
    fun getLastHistoryRecord(id: Long): GlucoseValue?

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_GLUCOSE_VALUES WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<GlucoseValue>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<GlucoseValue>

    @Query("SELECT * FROM $TABLE_GLUCOSE_VALUES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<GlucoseValue>
}