package info.nightscout.androidaps.plugins.pump.omnipod.eros.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import info.nightscout.androidaps.plugins.pump.omnipod.eros.definition.PodHistoryEntryType
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ErosHistoryTest {

    private lateinit var dao: ErosHistoryRecordDao
    private lateinit var database: ErosHistoryDatabase
    private lateinit var erosHistory: ErosHistory

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ErosHistoryDatabase::class.java
        ).build()
        dao = database.historyRecordDao()
        erosHistory = ErosHistory(dao)
    }

    @Test
    fun testInsertionAndRetrieval() {
        var history = erosHistory.getAllErosHistoryRecordsFromTimestamp(0L);
        assert(history.isEmpty())

        val type = PodHistoryEntryType.SET_BOLUS.code.toLong()
        val entity = ErosHistoryRecordEntity(1000L, type)
        erosHistory.create(entity)
        erosHistory.create(ErosHistoryRecordEntity(3000L, PodHistoryEntryType.CANCEL_BOLUS.code.toLong()))

        history = erosHistory.getAllErosHistoryRecordsFromTimestamp(0L);
        assert(history.size == 2)
        assert(type.equals(history.first().podEntryTypeCode))

        val returnedEntity = erosHistory.findErosHistoryRecordByPumpId(entity.pumpId)
        assertNotNull(returnedEntity)
        assert(type.equals(returnedEntity.podEntryTypeCode))
    }

    @After
    fun tearDown() {
        database.close()
    }
}
