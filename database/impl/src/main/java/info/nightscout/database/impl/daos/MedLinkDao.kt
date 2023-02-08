package info.nightscout.androidaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.database.entities.MedLinkConfig
import info.nightscout.database.entities.TABLE_MEDLINK_CONFIG
import info.nightscout.database.entities.TABLE_TEMPORARY_BASALS
import info.nightscout.database.impl.daos.TraceableDao
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Suppress("FunctionName")
@Dao
internal interface MedLinkDao : TraceableDao<MedLinkConfig> {

    @Query("SELECT * FROM $TABLE_MEDLINK_CONFIG WHERE id = :id")
    override fun findById(id: Long): MedLinkConfig?

    @Query("DELETE FROM $TABLE_MEDLINK_CONFIG")
    override fun deleteAllEntries()

    @Query("SELECT id FROM $TABLE_MEDLINK_CONFIG ORDER BY id DESC limit 1")
    fun getLastId(): Maybe<Long>

    @Query("SELECT * FROM $TABLE_MEDLINK_CONFIG WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC")
    fun getMedLinkConfigData(): Single<List<MedLinkConfig>>

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_MEDLINK_CONFIG WHERE id > :id AND referenceId IS NULL OR id IN (SELECT DISTINCT referenceId FROM $TABLE_MEDLINK_CONFIG WHERE id > :id) ORDER BY id ASC")
    fun getModifiedFrom(id: Long): List<MedLinkConfig>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_MEDLINK_CONFIG WHERE id > :id ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<MedLinkConfig>

    @Query("SELECT * FROM $TABLE_MEDLINK_CONFIG WHERE id = :referenceId")
    fun getCurrentFromHistoric(referenceId: Long): Maybe<MedLinkConfig>

    @Query("SELECT * FROM $TABLE_MEDLINK_CONFIG ORDER BY id DESC limit 1")
    fun getCurrentConfig(): Maybe<MedLinkConfig>

    @Query("SELECT currentFrequency FROM $TABLE_MEDLINK_CONFIG group by currentFrequency order by count(0) desc limit 2")
    fun getMostCommonFrequencies() : Maybe<List<Int>>


    @Query("DELETE FROM $TABLE_MEDLINK_CONFIG WHERE timestamp < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_MEDLINK_CONFIG WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

}