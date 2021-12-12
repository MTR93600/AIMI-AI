package info.nightscout.androidaps.data

import android.content.Context
import dagger.android.AndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.extensions.pureProfileFromJson
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.TestAapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import java.util.*

/**
 * Created by mike on 18.03.2018.
 */
@Suppress("SpellCheckingInspection")
class ProfileTest : TestBase() {

    @Mock lateinit var activePluginProvider: ActivePlugin
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var config: Config
    @Mock lateinit var sp: SP
    @Mock lateinit var repository: AppRepository

    private lateinit var rxBus: RxBus
    private lateinit var dateUtil: DateUtil
    private lateinit var testPumpPlugin: TestPumpPlugin
    private lateinit var hardLimits: HardLimits

    private var okProfile = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}]," +
        "\"sens\":[{\"time\":\"00:00\",\"value\":\"6\"},{\"time\":\"2:00\",\"value\":\"6.2\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    private var belowLimitValidProfile = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.001\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    private var notAlignedBasalValidProfile = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:30\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    private var notStartingAtZeroValidProfile = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:30\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
    private var noUnitsValidProfile = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\"}"
    private var wrongProfile = "{\"dia\":\"5\",\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"100\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"

    //String profileStore = "{\"defaultProfile\":\"Default\",\"store\":{\"Default\":" + validProfile + "}}";

    @Before
    fun prepare() {
        testPumpPlugin = TestPumpPlugin { AndroidInjector { } }
        dateUtil = DateUtil(context)
        rxBus = RxBus(TestAapsSchedulers(), aapsLogger)
        hardLimits = HardLimits(aapsLogger, rxBus, sp, rh, context, repository)
        `when`(activePluginProvider.activePump).thenReturn(testPumpPlugin)
        `when`(rh.gs(R.string.profile_per_unit)).thenReturn("/U")
        `when`(rh.gs(R.string.profile_carbs_per_unit)).thenReturn("g/U")
        `when`(rh.gs(R.string.profile_ins_units_per_hour)).thenReturn("U/h")
        `when`(rh.gs(anyInt(), anyString())).thenReturn("")
    }

    @Test
    fun doTests() {

        // Test valid profile
        var p = ProfileSealed.Pure(pureProfileFromJson(JSONObject(okProfile), dateUtil)!!)
        Assert.assertEquals(true, p.isValid("Test", testPumpPlugin, config, rh, rxBus, hardLimits, false).isValid)
//        Assert.assertEquals(true, p.log().contains("NS units: mmol"))
//        JSONAssert.assertEquals(JSONObject(okProfile), p.toPureNsJson(dateUtil), false)
        Assert.assertEquals(5.0, p.dia, 0.01)
//        Assert.assertEquals(TimeZone.getTimeZone("UTC"), p.timeZone)
        Assert.assertEquals("00:30", dateUtil.format_HH_MM(30 * 60))
        val c = Calendar.getInstance()
        c[Calendar.HOUR_OF_DAY] = 1
        c[Calendar.MINUTE] = 0
        c[Calendar.SECOND] = 0
        c[Calendar.MILLISECOND] = 0
        Assert.assertEquals(108.0, p.getIsfMgdl(c.timeInMillis), 0.01)
        c[Calendar.HOUR_OF_DAY] = 2
        Assert.assertEquals(111.6, p.getIsfMgdl(c.timeInMillis), 0.01)
//        Assert.assertEquals(110.0, p.getIsfTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("""
    00:00    6,0 mmol/U
    02:00    6,2 mmol/U
    """.trimIndent(), p.getIsfList(rh, dateUtil).replace(".", ","))
        Assert.assertEquals(30.0, p.getIc(c.timeInMillis), 0.01)
        Assert.assertEquals(30.0, p.getIcTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("00:00    30,0 g/U", p.getIcList(rh, dateUtil).replace(".", ","))
        Assert.assertEquals(0.1, p.getBasal(c.timeInMillis), 0.01)
        Assert.assertEquals(0.1, p.getBasalTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("00:00    0,10 U/h", p.getBasalList(rh, dateUtil).replace(".", ","))
        Assert.assertEquals(0.1, p.getBasalValues()[0].value, 0.01)
        Assert.assertEquals(0.1, p.getMaxDailyBasal(), 0.01)
        Assert.assertEquals(2.4, p.percentageBasalSum(), 0.01)
        Assert.assertEquals(2.4, p.baseBasalSum(), 0.01)
//        Assert.assertEquals(81.0, p.getTargetMgdl(2 * 60 * 60), 0.01)
        Assert.assertEquals(90.0, p.getTargetLowMgdl(c.timeInMillis), 0.01)
//        Assert.assertEquals(4.0, p.getTargetLowTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals(90.0, p.getTargetHighMgdl(c.timeInMillis), 0.01)
//        Assert.assertEquals(5.0, p.getTargetHighTimeFromMidnight(2 * 60 * 60), 0.01)
        Assert.assertEquals("00:00    5,0 - 5,0 mmol", p.getTargetList(rh, dateUtil).replace(".", ","))
        Assert.assertEquals(100, p.percentage)
        Assert.assertEquals(0, p.timeshift)
        Assert.assertEquals(0.1, Profile.toMgdl(0.1, GlucoseUnit.MGDL), 0.01)
        Assert.assertEquals(18.0, Profile.toMgdl(1.0, GlucoseUnit.MMOL), 0.01)
        Assert.assertEquals(1.0, Profile.toMmol(18.0, GlucoseUnit.MGDL), 0.01)
        Assert.assertEquals(18.0, Profile.toMmol(18.0, GlucoseUnit.MMOL), 0.01)
        Assert.assertEquals(18.0, Profile.fromMgdlToUnits(18.0, GlucoseUnit.MGDL), 0.01)
        Assert.assertEquals(1.0, Profile.fromMgdlToUnits(18.0, GlucoseUnit.MMOL), 0.01)
        Assert.assertEquals(18.0, Profile.toUnits(18.0, 1.0, GlucoseUnit.MGDL), 0.01)
        Assert.assertEquals(1.0, Profile.toUnits(18.0, 1.0, GlucoseUnit.MMOL), 0.01)
        Assert.assertEquals("18", Profile.toUnitsString(18.0, 1.0, GlucoseUnit.MGDL))
        Assert.assertEquals("1,0", Profile.toUnitsString(18.0, 1.0, GlucoseUnit.MMOL).replace(".", ","))
        Assert.assertEquals("5 - 6", Profile.toTargetRangeString(5.0, 6.0, GlucoseUnit.MGDL, GlucoseUnit.MGDL))
        Assert.assertEquals("4", Profile.toTargetRangeString(4.0, 4.0, GlucoseUnit.MGDL, GlucoseUnit.MGDL))

        //Test basal profile below limit
        p = ProfileSealed.Pure(pureProfileFromJson(JSONObject(belowLimitValidProfile), dateUtil)!!)
        p.isValid("Test", testPumpPlugin, config, rh, rxBus, hardLimits, false)

        // Test profile w/o units
        Assert.assertNull(pureProfileFromJson(JSONObject(noUnitsValidProfile), dateUtil))

        //Test profile not starting at midnight
        p = ProfileSealed.Pure(pureProfileFromJson(JSONObject(notStartingAtZeroValidProfile), dateUtil)!!)
        Assert.assertEquals(30.0, p.getIc(0), 0.01)

        // Test wrong profile
        Assert.assertNull(pureProfileFromJson(JSONObject(wrongProfile), dateUtil))

        // Test percentage functionality
        p = ProfileSealed.Pure(pureProfileFromJson(JSONObject(okProfile), dateUtil)!!)
        p.pct = 50
        Assert.assertEquals(0.05, p.getBasal(c.timeInMillis), 0.01)
        Assert.assertEquals(1.2, p.percentageBasalSum(), 0.01)
        Assert.assertEquals(60.0, p.getIc(c.timeInMillis), 0.01)
        Assert.assertEquals(223.2, p.getIsfMgdl(c.timeInMillis), 0.01)

        // Test timeshift functionality
        p = ProfileSealed.Pure(pureProfileFromJson(JSONObject(okProfile), dateUtil)!!)
        p.ts = 1
        Assert.assertEquals(
            """
                00:00    6.2 mmol/U
                01:00    6.0 mmol/U
                03:00    6.2 mmol/U
                """.trimIndent(), p.getIsfList(rh, dateUtil))

        // Test hour alignment
        testPumpPlugin.pumpDescription.is30minBasalRatesCapable = false
        p = ProfileSealed.Pure(pureProfileFromJson(JSONObject(notAlignedBasalValidProfile), dateUtil)!!)
        p.isValid("Test", testPumpPlugin, config, rh, rxBus, hardLimits, false)
    }
}