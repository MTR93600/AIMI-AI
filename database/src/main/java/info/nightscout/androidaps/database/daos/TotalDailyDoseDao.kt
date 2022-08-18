package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.androidaps.database.TABLE_TOTAL_DAILY_DOSES
import info.nightscout.androidaps.database.embedments.InterfaceIDs
import info.nightscout.androidaps.database.entities.TotalDailyDose
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Suppress("FunctionName")
@Dao
internal interface TotalDailyDoseDao : TraceableDao<TotalDailyDose> {

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE id = :id")
    override fun findById(id: Long): TotalDailyDose?

    @Query("DELETE FROM $TABLE_TOTAL_DAILY_DOSES")
    override fun deleteAllEntries()

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE pumpId = :pumpId AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpIds(pumpId: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TotalDailyDose?

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE timestamp = :timestamp AND pumpType = :pumpType AND pumpSerial = :pumpSerial AND referenceId IS NULL")
    fun findByPumpTimestamp(timestamp: Long, pumpType: InterfaceIDs.PumpType, pumpSerial: String): TotalDailyDose?

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE timestamp = :timestamp AND pumpType = :pumpType AND referenceId IS NULL")
    fun findByTimestamp(timestamp: Long, pumpType: InterfaceIDs.PumpType): Maybe<TotalDailyDose>

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE isValid = 1 AND referenceId IS NULL AND pumpType <> :exclude ORDER BY timestamp DESC LIMIT :count")
    fun getLastTotalDailyDoses(count: Int, exclude: InterfaceIDs.PumpType = InterfaceIDs.PumpType.CACHE): Single<List<TotalDailyDose>>

    @Query("SELECT * FROM $TABLE_TOTAL_DAILY_DOSES WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    suspend fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<TotalDailyDose>

    @Query("DELETE FROM $TABLE_TOTAL_DAILY_DOSES WHERE dateCreated >= :since AND pumpType = :pumpType")
    fun deleteNewerThan(since: Long, pumpType: InterfaceIDs.PumpType)

}