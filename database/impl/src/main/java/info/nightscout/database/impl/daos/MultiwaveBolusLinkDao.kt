package info.nightscout.database.impl.daos

import androidx.room.Dao
import androidx.room.Query
import info.nightscout.database.entities.MultiwaveBolusLink
import info.nightscout.database.entities.TABLE_MULTIWAVE_BOLUS_LINKS

@Dao
internal interface MultiwaveBolusLinkDao : TraceableDao<MultiwaveBolusLink> {

    @Query("SELECT * FROM $TABLE_MULTIWAVE_BOLUS_LINKS WHERE id = :id")
    override fun findById(id: Long): MultiwaveBolusLink?

    @Query("DELETE FROM $TABLE_MULTIWAVE_BOLUS_LINKS")
    override fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_MULTIWAVE_BOLUS_LINKS WHERE dateCreated < :than")
    override fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_MULTIWAVE_BOLUS_LINKS WHERE referenceId IS NOT NULL")
    override fun deleteTrackedChanges(): Int

    @Query("SELECT * FROM $TABLE_MULTIWAVE_BOLUS_LINKS WHERE dateCreated > :since AND dateCreated <= :until LIMIT :limit OFFSET :offset")
    fun getNewEntriesSince(since: Long, until: Long, limit: Int, offset: Int): List<MultiwaveBolusLink>
}