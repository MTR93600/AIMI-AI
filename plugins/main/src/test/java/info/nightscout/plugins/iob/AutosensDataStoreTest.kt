package info.nightscout.plugins.iob

import android.content.Context
import info.nightscout.androidaps.TestBase
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AutosensDataStoreTest : TestBase() {

    @Mock lateinit var context: Context

    private lateinit var dateUtil: DateUtil

    private val autosensDataStore = info.nightscout.plugins.iob.iobCobCalculator.data.AutosensDataStoreObject()

    @BeforeEach
    fun mock() {
        dateUtil = DateUtil(context)
    }

    @Test
    fun isAbout5minDataTest() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))

        // too much shifted data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(9).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))

        // too much shifted and missing data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(9).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))

        // too much shifted and missing data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(83).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(78).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(73).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(68).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(63).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(58).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(53).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(43).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(38).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(33).plus(T.secs(1)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(28).plus(T.secs(0)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(23).plus(T.secs(0)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(16).plus(T.secs(36)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))

        // slightly shifted data should return true
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))

        // slightly shifted and missing data should return true
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))
    }

    @Test
    fun createBucketedData5minTest1() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(bgReadingList[0].timestamp, autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(bgReadingList[3].timestamp, autosensDataStore.bucketedData!![3].timestamp)
        Assertions.assertEquals(bgReadingList.size.toLong(), autosensDataStore.bucketedData!!.size.toLong())

        // Missing value should be replaced
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(bgReadingList[0].timestamp, autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(bgReadingList[2].timestamp, autosensDataStore.bucketedData!![3].timestamp)
        Assertions.assertEquals(bgReadingList.size + 1.toLong(), autosensDataStore.bucketedData!!.size.toLong())

        // drift should be cleared
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs() + T.secs(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs() + T.secs(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs() + T.secs(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(0).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(T.mins(15).msecs(), autosensDataStore.bucketedData!![1].timestamp)
        Assertions.assertEquals(T.mins(10).msecs(), autosensDataStore.bucketedData!![2].timestamp)
        Assertions.assertEquals(T.mins(5).msecs(), autosensDataStore.bucketedData!![3].timestamp)
        Assertions.assertEquals(bgReadingList.size.toLong(), autosensDataStore.bucketedData!!.size.toLong())

        // bucketed data should return null if not enough bg data
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(30).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(null, autosensDataStore.bucketedData)

        // data should be reconstructed
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(50).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(45).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(T.mins(50).msecs(), autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.bucketedData!![6].timestamp)
        Assertions.assertEquals(7, autosensDataStore.bucketedData!!.size.toLong())
        Assertions.assertEquals(100.0, autosensDataStore.bucketedData!![0].value, 1.0)
        Assertions.assertEquals(90.0, autosensDataStore.bucketedData!![1].value, 1.0)
        Assertions.assertEquals(50.0, autosensDataStore.bucketedData!![5].value, 1.0)
        Assertions.assertEquals(40.0, autosensDataStore.bucketedData!![6].value, 1.0)

        // non 5min data should be reconstructed
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(50).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 96.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(T.mins(50).msecs(), autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.bucketedData!![6].timestamp)
        Assertions.assertEquals(7, autosensDataStore.bucketedData!!.size.toLong())
        Assertions.assertEquals(100.0, autosensDataStore.bucketedData!![0].value, 1.0)
        Assertions.assertEquals(90.0, autosensDataStore.bucketedData!![1].value, 1.0)
        Assertions.assertEquals(50.0, autosensDataStore.bucketedData!![5].value, 1.0)
        Assertions.assertEquals(40.0, autosensDataStore.bucketedData!![6].value, 1.0)
    }

    @Test
    fun createBucketedData5minTest2() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        //bucketed data should be null if no bg data available
        autosensDataStore.bgReadings = ArrayList()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(null, autosensDataStore.bucketedData)

        // real data gap test
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:34:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:14:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:09:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:04:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:59:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:54:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:49:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:44:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:39:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:34:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:29:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:24:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:19:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:14:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:09:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:04:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T11:59:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )

        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:29:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:24:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:19:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:14:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:10:03Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:04:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:59:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:54:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:50:03Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:44:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        autosensDataStore.referenceTime = -1
        Assertions.assertEquals(true, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(dateUtil.fromISODateString("2018-09-05T13:34:57Z"), autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(dateUtil.fromISODateString("2018-09-05T03:44:57Z"), autosensDataStore.bucketedData!![autosensDataStore.bucketedData!!.size - 1].timestamp)

        // 5min 4sec data
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:33:40Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:28:36Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:23:32Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:18:28Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:13:24Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:08:19Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:03:16Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:58:11Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:53:07Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:48:03Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:42:58Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:37:54Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:32:51Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:27:46Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:22:42Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:17:38Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:12:33Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:07:29Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:02:26Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T04:57:21Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T04:52:17Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))
    }

    @Test
    fun createBucketedData5minTest3() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        // non 5min data not aligned to referenceTime should be recalculated to referenceTime
        autosensDataStore.referenceTime = T.mins(5).msecs()
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(T.mins(45).msecs(), autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(T.mins(35).msecs(), autosensDataStore.bucketedData!![2].timestamp)
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.bucketedData!![5].timestamp)
        Assertions.assertEquals(6, autosensDataStore.bucketedData!!.size.toLong())
        Assertions.assertEquals(99.0, autosensDataStore.bucketedData!![0].value, 1.0) // Recalculated data to 45min
        Assertions.assertEquals(90.0, autosensDataStore.bucketedData!![1].value, 1.0) // Recalculated data to 40min
        Assertions.assertEquals(67.0, autosensDataStore.bucketedData!![3].value, 1.0) // Recalculated data to 30min
        Assertions.assertEquals(45.0, autosensDataStore.bucketedData!![5].value, 1.0) // Recalculated data to 20min

        // non 5min data not aligned to referenceTime should be recalculated to referenceTime
        autosensDataStore.referenceTime = T.mins(5).msecs()
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(46).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(T.mins(45).msecs(), autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(T.mins(35).msecs(), autosensDataStore.bucketedData!![2].timestamp)
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.bucketedData!![5].timestamp)
        Assertions.assertEquals(6, autosensDataStore.bucketedData!!.size.toLong())
        Assertions.assertEquals(99.0, autosensDataStore.bucketedData!![0].value, 1.0) // Recalculated data to 45min
        Assertions.assertEquals(90.0, autosensDataStore.bucketedData!![1].value, 1.0) // Recalculated data to 40min
        Assertions.assertEquals(67.0, autosensDataStore.bucketedData!![3].value, 1.0) // Recalculated data to 30min
        Assertions.assertEquals(45.0, autosensDataStore.bucketedData!![5].value, 1.0) // Recalculated data to 20min

        // non 5min data without referenceTime set, should allign the data to the time of the last reading
        autosensDataStore.referenceTime = -1
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(false, autosensDataStore.isAbout5minData(aapsLogger))
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(T.mins(48).msecs(), autosensDataStore.bucketedData!![0].timestamp)
        Assertions.assertEquals(T.mins(43).msecs(), autosensDataStore.bucketedData!![1].timestamp)
        Assertions.assertEquals(T.mins(33).msecs(), autosensDataStore.bucketedData!![3].timestamp)
        Assertions.assertEquals(T.mins(18).msecs(), autosensDataStore.bucketedData!![6].timestamp)
        Assertions.assertEquals(7, autosensDataStore.bucketedData!!.size.toLong())
        Assertions.assertEquals(100.0, autosensDataStore.bucketedData!![0].value, 1.0) // Recalculated data to 48min
        Assertions.assertEquals(98.0, autosensDataStore.bucketedData!![1].value, 1.0)  // Recalculated data to 43min
        Assertions.assertEquals(74.0, autosensDataStore.bucketedData!![3].value, 1.0)  // Recalculated data to 33min
        Assertions.assertEquals(40.0, autosensDataStore.bucketedData!![6].value, 1.0)  // Recalculated data to 18min
    }

    @Test
    fun bgReadingsTest() {
        val bgReadingList: List<GlucoseValue> = ArrayList()
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(bgReadingList, autosensDataStore.bgReadings)
    }

    @Test
    fun roundUpTimeTest() {
        Assertions.assertEquals(T.mins(3).msecs(), autosensDataStore.roundUpTime(T.secs(155).msecs()))
    }

    @Test
    fun findNewerTest() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(T.mins(10).msecs(), autosensDataStore.findNewer(T.mins(8).msecs())!!.timestamp)
        Assertions.assertEquals(T.mins(5).msecs(), autosensDataStore.findNewer(T.mins(5).msecs())!!.timestamp)
        Assertions.assertEquals(T.mins(10).msecs(), autosensDataStore.findNewer(T.mins(10).msecs())!!.timestamp)
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.findNewer(T.mins(20).msecs())!!.timestamp)
        Assertions.assertEquals(null, autosensDataStore.findNewer(T.mins(22).msecs()))
    }

    @Test
    fun findOlderTest() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        Assertions.assertEquals(T.mins(5).msecs(), autosensDataStore.findOlder(T.mins(8).msecs())!!.timestamp)
        Assertions.assertEquals(T.mins(5).msecs(), autosensDataStore.findOlder(T.mins(5).msecs())!!.timestamp)
        Assertions.assertEquals(T.mins(10).msecs(), autosensDataStore.findOlder(T.mins(10).msecs())!!.timestamp)
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.findOlder(T.mins(20).msecs())!!.timestamp)
        Assertions.assertEquals(null, autosensDataStore.findOlder(T.mins(4).msecs()))
    }

    @Test
    fun findPreviousTimeFromBucketedDataTest() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()
        autosensDataStore.bgReadings = bgReadingList
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(null, autosensDataStore.findPreviousTimeFromBucketedData(1000))

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        Assertions.assertEquals(null, autosensDataStore.findPreviousTimeFromBucketedData(T.mins(4).msecs()))
        Assertions.assertEquals(T.mins(5).msecs(), autosensDataStore.findPreviousTimeFromBucketedData(T.mins(6).msecs()))
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.findPreviousTimeFromBucketedData(T.mins(20).msecs()))
        Assertions.assertEquals(T.mins(20).msecs(), autosensDataStore.findPreviousTimeFromBucketedData(T.mins(25).msecs()))
    }
}