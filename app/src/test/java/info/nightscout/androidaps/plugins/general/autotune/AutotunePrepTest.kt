package info.nightscout.androidaps.plugins.general.autotune

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.data.TargetBlock
import info.nightscout.androidaps.database.entities.Bolus
import info.nightscout.androidaps.database.entities.Carbs
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.extensions.shiftBlock
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.File
import java.util.*

class AutotunePrepTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var autotuneFS: AutotuneFS
    @Mock lateinit var injector: HasAndroidInjector
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var repository: AppRepository
    private lateinit var autotunePrep: AutotunePrep
    private lateinit var autotuneIob: TestAutotuneIob
    private var ts = 0
    private var min5mCarbImpact = 0.0
    private var autotuneMin = 0.0
    private var autotuneMax = 0.0
    private var startDayTime = 0L

    @Before
    fun initData() {
        ts = T.msecs(TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()).hours().toInt() - 2
    }

    @Test
    fun autotunePrepTest1() { // Test if categorisation with standard treatments with carbs is Ok
        val inputIobJson = File("src/test/res/autotune/test1/oaps-iobCalc.2022-05-21.json").readText() //json files build with iob/activity calculated by OAPS
        val iobOapsCalculation = buildIobOaps(JSONArray(inputIobJson))
        autotuneIob = TestAutotuneIob(aapsLogger, repository, profileFunction, sp, dateUtil, activePlugin, autotuneFS, iobOapsCalculation)
        autotunePrep = AutotunePrep(aapsLogger, sp, dateUtil, autotuneFS, autotuneIob)
        val inputProfileJson = File("src/test/res/autotune/test1/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prepJson = File("src/test/res/autotune/test1/autotune.2022-05-21.json").readText()
        val oapsPreppedGlucose = PreppedGlucose(JSONObject(prepJson), dateUtil) //prep data calculated by OpenAPS autotune
        val oapsEntriesJson = File("src/test/res/autotune/test1/aaps-entries.2022-05-21.json").readText()
        autotuneIob.glucose = buildGlucose(JSONArray(oapsEntriesJson))
        val oapsTreatmentsJson = File("src/test/res/autotune/test1/aaps-treatments.2022-05-21.json").readText()
        autotuneIob.meals = buildMeals(JSONArray(oapsTreatmentsJson))  //Only meals is used in unit test, Insulin only used for iob calculation
        autotuneIob.boluses = buildBoluses(oapsPreppedGlucose) //Values from oapsPrepData because linked to iob calculation method for TBR
        `when`(sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, 3.0)).thenReturn(min5mCarbImpact)
        `when`(sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false)).thenReturn(false)
        val aapsPreppedGlucose = autotunePrep.categorizeBGDatums(inputProfile, inputProfile.localInsulin, false)
        try {
            aapsPreppedGlucose?.let {       // compare all categorization calculated by aaps plugin (aapsPreppedGlucose) with categorization calculated by OpenAPS (oapsPreppedGlucose)
                for (i in aapsPreppedGlucose.crData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.crData[i].equals(aapsPreppedGlucose.crData[i]))
                for (i in aapsPreppedGlucose.csfGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.csfGlucoseData[i].equals(aapsPreppedGlucose.csfGlucoseData[i]))
                oapsPreppedGlucose.isfGlucoseData = oapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
                aapsPreppedGlucose.isfGlucoseData = aapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
                for (i in aapsPreppedGlucose.isfGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.isfGlucoseData[i].equals(aapsPreppedGlucose.isfGlucoseData[i]))
                oapsPreppedGlucose.basalGlucoseData = oapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
                aapsPreppedGlucose.basalGlucoseData = aapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
                for (i in aapsPreppedGlucose.basalGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.basalGlucoseData[i].equals(aapsPreppedGlucose.basalGlucoseData[i]))
            }
                ?: Assert.fail()
        } catch (e: Exception) {
            Assert.fail()
        }
    }

    @Test
    fun autotunePrepTest2() { // Test if categorisation without carbs (full UAM) and categorize UAM as basal false is Ok
        val inputIobJson = File("src/test/res/autotune/test2/oaps-iobCalc.2022-05-21.json").readText() //json files build with iob/activity calculated by OAPS
        val iobOapsCalculation = buildIobOaps(JSONArray(inputIobJson))
        autotuneIob = TestAutotuneIob(aapsLogger, repository, profileFunction, sp, dateUtil, activePlugin, autotuneFS, iobOapsCalculation)
        autotunePrep = AutotunePrep(aapsLogger, sp, dateUtil, autotuneFS, autotuneIob)
        val inputProfileJson = File("src/test/res/autotune/test2/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prepJson = File("src/test/res/autotune/test2/autotune.2022-05-21.json").readText()
        val oapsPreppedGlucose = PreppedGlucose(JSONObject(prepJson), dateUtil) //prep data calculated by OpenAPS autotune
        val oapsEntriesJson = File("src/test/res/autotune/test2/aaps-entries.2022-05-21.json").readText()
        autotuneIob.glucose = buildGlucose(JSONArray(oapsEntriesJson))
        val oapsTreatmentsJson = File("src/test/res/autotune/test2/aaps-treatments.2022-05-21.json").readText()
        autotuneIob.meals = buildMeals(JSONArray(oapsTreatmentsJson))  //Only meals is used in unit test, Insulin only used for iob calculation
        autotuneIob.boluses = buildBoluses(oapsPreppedGlucose) //Values from oapsPrepData because linked to iob calculation method for TBR
        `when`(sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, 3.0)).thenReturn(min5mCarbImpact)
        `when`(sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false)).thenReturn(false)           // CategorizeUAM as Basal = False
        val aapsPreppedGlucose = autotunePrep.categorizeBGDatums(inputProfile, inputProfile.localInsulin, false)
        try {
            aapsPreppedGlucose?.let {       // compare all categorization calculated by aaps plugin (aapsPreppedGlucose) with categorization calculated by OpenAPS (oapsPreppedGlucose)
                for (i in aapsPreppedGlucose.crData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.crData[i].equals(aapsPreppedGlucose.crData[i]))
                for (i in aapsPreppedGlucose.csfGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.csfGlucoseData[i].equals(aapsPreppedGlucose.csfGlucoseData[i]))
                oapsPreppedGlucose.isfGlucoseData = oapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
                aapsPreppedGlucose.isfGlucoseData = aapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
                for (i in aapsPreppedGlucose.isfGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.isfGlucoseData[i].equals(aapsPreppedGlucose.isfGlucoseData[i]))
                oapsPreppedGlucose.basalGlucoseData = oapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
                aapsPreppedGlucose.basalGlucoseData = aapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
                for (i in aapsPreppedGlucose.basalGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.basalGlucoseData[i].equals(aapsPreppedGlucose.basalGlucoseData[i]))
            }
                ?: Assert.fail()
        } catch (e: Exception) {
            Assert.fail()
        }
    }

    @Test
    fun autotunePrepTest3() { // Test if categorisation without carbs (full UAM) and categorize UAM as basal true is Ok
        val inputIobJson = File("src/test/res/autotune/test3/oaps-iobCalc.2022-05-21.json").readText() //json files build with iob/activity calculated by OAPS
        val iobOapsCalculation = buildIobOaps(JSONArray(inputIobJson))
        autotuneIob = TestAutotuneIob(aapsLogger, repository, profileFunction, sp, dateUtil, activePlugin, autotuneFS, iobOapsCalculation)
        autotunePrep = AutotunePrep(aapsLogger, sp, dateUtil, autotuneFS, autotuneIob)
        val inputProfileJson = File("src/test/res/autotune/test3/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prepJson = File("src/test/res/autotune/test3/autotune.2022-05-21.json").readText()
        val oapsPreppedGlucose = PreppedGlucose(JSONObject(prepJson), dateUtil) //prep data calculated by OpenAPS autotune
        val oapsEntriesJson = File("src/test/res/autotune/test3/aaps-entries.2022-05-21.json").readText()
        autotuneIob.glucose = buildGlucose(JSONArray(oapsEntriesJson))
        val oapsTreatmentsJson = File("src/test/res/autotune/test3/aaps-treatments.2022-05-21.json").readText()
        autotuneIob.meals = buildMeals(JSONArray(oapsTreatmentsJson))  //Only meals is used in unit test, Insulin only used for iob calculation
        autotuneIob.boluses = buildBoluses(oapsPreppedGlucose) //Values from oapsPrepData because linked to iob calculation method for TBR
        `when`(sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, 3.0)).thenReturn(min5mCarbImpact)
        `when`(sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false)).thenReturn(true)           // CategorizeUAM as Basal = True
        val aapsPreppedGlucose = autotunePrep.categorizeBGDatums(inputProfile, inputProfile.localInsulin, false)
        try {
            aapsPreppedGlucose?.let {       // compare all categorization calculated by aaps plugin (aapsPreppedGlucose) with categorization calculated by OpenAPS (oapsPreppedGlucose)
                for (i in aapsPreppedGlucose.crData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.crData[i].equals(aapsPreppedGlucose.crData[i]))
                for (i in aapsPreppedGlucose.csfGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.csfGlucoseData[i].equals(aapsPreppedGlucose.csfGlucoseData[i]))
                oapsPreppedGlucose.isfGlucoseData = oapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
                aapsPreppedGlucose.isfGlucoseData = aapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
                for (i in aapsPreppedGlucose.isfGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.isfGlucoseData[i].equals(aapsPreppedGlucose.isfGlucoseData[i]))
                oapsPreppedGlucose.basalGlucoseData = oapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
                aapsPreppedGlucose.basalGlucoseData = aapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
                for (i in aapsPreppedGlucose.basalGlucoseData.indices)
                    Assert.assertTrue(oapsPreppedGlucose.basalGlucoseData[i].equals(aapsPreppedGlucose.basalGlucoseData[i]))
            }
                ?: Assert.fail()
        } catch (e: Exception) {
            Assert.fail()
        }
    }

    /*************************************************************************************************************************************************************************************
     * OpenAPS profile for Autotune only have one ISF value and one IC value
     */
    @Suppress("SpellCheckingInspection")
    private fun atProfileFromOapsJson(jsonObject: JSONObject, dateUtil: DateUtil, defaultUnits: String? = null): ATProfile? {
        try {
            min5mCarbImpact = JsonHelper.safeGetDoubleAllowNull(jsonObject, "min_5m_carbimpact") ?: return null
            autotuneMin = JsonHelper.safeGetDoubleAllowNull(jsonObject, "autosens_min") ?: return null
            autotuneMax = JsonHelper.safeGetDoubleAllowNull(jsonObject, "autosens_max") ?: return null
            val txtUnits = JsonHelper.safeGetStringAllowNull(jsonObject, "units", defaultUnits) ?: return null
            val units = GlucoseUnit.fromText(txtUnits)
            val dia = JsonHelper.safeGetDoubleAllowNull(jsonObject, "dia") ?: return null
            val peak = JsonHelper.safeGetIntAllowNull(jsonObject, "insulinPeakTime") ?: return null
            val localInsulin = LocalInsulin("insulin", peak, dia)
            val timezone = TimeZone.getTimeZone(JsonHelper.safeGetString(jsonObject, "timezone", "UTC"))
            val isfJson = jsonObject.getJSONObject("isfProfile")
            val isfBlocks = ArrayList<Block>(1).also {
                val isfJsonArray = isfJson.getJSONArray("sensitivities")
                val value = isfJsonArray.getJSONObject(0).getDouble("sensitivity")
                it.add(0, Block((T.hours(24).secs()) * 1000L, value))
            }
            val icBlocks = ArrayList<Block>(1).also {
                val value = jsonObject.getDouble("carb_ratio")
                it.add(0, Block((T.hours(24).secs()) * 1000L, value))
            }
            val basalBlocks = blockFromJsonArray(jsonObject.getJSONArray("basalprofile"))
                ?: return null
            val targetBlocks = ArrayList<TargetBlock>(1).also {
                it.add(0, TargetBlock((T.hours(24).secs()) * 1000L, 100.0, 100.0))
            }

            val pure = PureProfile(
                jsonObject = jsonObject,
                basalBlocks = basalBlocks.shiftBlock(1.0,ts),
                isfBlocks = isfBlocks,
                icBlocks = icBlocks,
                targetBlocks = targetBlocks,
                glucoseUnit = units,
                timeZone = timezone,
                dia = dia
            )
            return ATProfile(ProfileSealed.Pure(pure), localInsulin, profileInjector).also { it.dateUtil = dateUtil }
        } catch (ignored: Exception) {
            return null
        }
    }

    private fun blockFromJsonArray(jsonArray: JSONArray?): List<Block>? {
        val size = jsonArray?.length() ?: return null
        val ret = ArrayList<Block>(size)
        try {
            for (index in 0 until jsonArray.length() - 1) {
                val o = jsonArray.getJSONObject(index)
                val tas = o.getInt("minutes") * 60
                val next = jsonArray.getJSONObject(index + 1)
                val nextTas = next.getInt("minutes") * 60
                val value = o.getDouble("rate")
                if (tas % 3600 != 0) return null
                if (nextTas % 3600 != 0) return null
                ret.add(index, Block((nextTas - tas) * 1000L, value))
            }
            val last: JSONObject = jsonArray.getJSONObject(jsonArray.length() - 1)
            val lastTas = last.getInt("minutes") * 60
            val value = last.getDouble("rate")
            ret.add(jsonArray.length() - 1, Block((T.hours(24).secs() - lastTas) * 1000L, value))
        } catch (e: Exception) {
            return null
        }
        return ret
    }

    private fun buildBoluses(preppedGlucose: PreppedGlucose): ArrayList<Bolus> { //if categorization is correct then I return for dose function the crInsulin calculated in Oaps
        val boluses: ArrayList<Bolus> = ArrayList()
        for (i in preppedGlucose.crData.indices) {
            boluses.add(
                Bolus(
                    timestamp = preppedGlucose.crData[i].crEndTime,
                    amount = preppedGlucose.crData[i].crInsulin,
                    type = Bolus.Type.NORMAL
                )
            )
        }
        if (boluses.size == 0)  //Add at least one insulin treatment for tests to avoid return null in categorization
            boluses.add(
                Bolus(
                    timestamp = startDayTime,
                    amount = 1.0,
                    type = Bolus.Type.NORMAL
                )
            )
        return boluses
    }

    private fun buildMeals(jsonArray: JSONArray): ArrayList<Carbs> {
        val list: ArrayList<Carbs> = ArrayList()
        for (index in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(index)
            val value = JsonHelper.safeGetDouble(json, "carbs", 0.0)
            val timestamp = JsonHelper.safeGetLong(json, "date")
            if (value > 0.0 && timestamp > startDayTime) {
                list.add(Carbs(timestamp = timestamp, amount = value, duration = 0))
            }
        }
        return list
    }

    private fun buildGlucose(jsonArray: JSONArray): List<GlucoseValue> {
        val list: ArrayList<GlucoseValue> = ArrayList()
        for (index in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(index)
            val value = JsonHelper.safeGetDouble(json, "sgv")
            val timestamp = JsonHelper.safeGetLong(json, "date")
            list.add(GlucoseValue(raw = value, noise = 0.0, value = value, timestamp = timestamp, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT))
        }
        if (list.size > 0)
            startDayTime = list[list.size - 1].timestamp
        return list
    }

    private fun buildIobOaps(jsonArray: JSONArray): ArrayList<IobTotal> { //if categorization is correct then I return for dose function the crInsulin calculated in Oaps
        val list: ArrayList<IobTotal> = ArrayList()
        for (index in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(index)
            val time = JsonHelper.safeGetLong(json, "date")
            val iob = JsonHelper.safeGetDouble(json, "iob")
            val activity = JsonHelper.safeGetDouble(json, "activity")
            val iobTotal = IobTotal(time)
            iobTotal.iob = iob
            iobTotal.activity = activity
            list.add(iobTotal)
        }
        return list
    }

    class TestAutotuneIob(
        val aapsLogger: AAPSLogger,
        repository: AppRepository,
        val profileFunction: ProfileFunction,
        val sp: SP,
        val dateUtil: DateUtil,
        val activePlugin: ActivePlugin,
        autotuneFS: AutotuneFS,
        private val iobOapsCalculation: ArrayList<IobTotal>
    ) : AutotuneIob(
        aapsLogger,
        repository,
        profileFunction,
        sp,
        dateUtil,
        activePlugin,
        autotuneFS
    ) {

        override fun getIOB(time: Long, localInsulin: LocalInsulin): IobTotal {
            val bolusIob = IobTotal(time)
            iobOapsCalculation.forEach {
                if (it.time == time)
                    return it
            }
            return bolusIob
        }
    }
}
