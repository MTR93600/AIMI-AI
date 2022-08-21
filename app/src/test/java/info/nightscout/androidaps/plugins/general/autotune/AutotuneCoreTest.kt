package info.nightscout.androidaps.plugins.general.autotune

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.data.TargetBlock
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.androidaps.utils.T
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

class AutotuneCoreTest : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var autotuneFS: AutotuneFS
    @Mock lateinit var injector: HasAndroidInjector
    @Mock lateinit var activePlugin: ActivePlugin
    private lateinit var autotuneCore: AutotuneCore
    private var min5mCarbImpact = 0.0
    private var autotuneMin = 0.0
    private var autotuneMax = 0.0

    @Before
    fun initData() {
        autotuneCore = AutotuneCore(sp, autotuneFS)
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+2"))
    }

    @Suppress("SpellCheckingInspection")
    @Test
    fun autotuneCoreTest1() { // Test if AutotuneCore with input files of OpenAPS categorisation gives correct output profile
        val prepJson = File("src/test/res/autotune/test1/autotune.2022-05-21.json").readText()
        val inputProfileJson = File("src/test/res/autotune/test1/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prep = PreppedGlucose(JSONObject(prepJson), dateUtil)

        `when`(sp.getDouble(R.string.key_openapsama_autosens_max, 1.2)).thenReturn(autotuneMax)
        `when`(sp.getDouble(R.string.key_openapsama_autosens_min, 0.7)).thenReturn(autotuneMin)
        `when`(sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, 3.0)).thenReturn(min5mCarbImpact)
        val oapsOutputProfileJson = File("src/test/res/autotune/test1/aapsorefprofile.json").readText()
        val oapsOutputProfile = atProfileFromOapsJson(JSONObject(oapsOutputProfileJson), dateUtil)
        val outProfile = autotuneCore.tuneAllTheThings(prep, inputProfile, inputProfile)
        oapsOutputProfile?.let {
            Assert.assertEquals(oapsOutputProfile.isf, outProfile.isf, 0.0)
            Assert.assertEquals(oapsOutputProfile.ic, outProfile.ic, 0.0)
            for (i in 0..23)
                Assert.assertEquals(oapsOutputProfile.basal[i], outProfile.basal[i], 0.0)
        }
            ?: Assert.fail()
    }

    @Suppress("SpellCheckingInspection")
    @Test
    fun autotuneCoreTest4() { // Test if limiting modification with Min Max Autosens parameter works (18h on basal and on ISF value)
        val prepJson = File("src/test/res/autotune/test4/autotune.2022-05-30.json").readText()
        val inputProfileJson = File("src/test/res/autotune/test4/profile.2022-05-30.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val pumpProfileJson = File("src/test/res/autotune/test4/profile.pump.json").readText()
        val pumpProfile = atProfileFromOapsJson(JSONObject(pumpProfileJson), dateUtil)!!
        val prep = PreppedGlucose(JSONObject(prepJson), dateUtil)
        `when`(sp.getDouble(R.string.key_openapsama_autosens_max, 1.2)).thenReturn(autotuneMax)
        `when`(sp.getDouble(R.string.key_openapsama_autosens_min, 0.7)).thenReturn(autotuneMin)
        `when`(sp.getDouble(R.string.key_openapsama_min_5m_carbimpact, 3.0)).thenReturn(min5mCarbImpact)
        val oapsOutputProfileJson = File("src/test/res/autotune/test4/newprofile.2022-05-30.json").readText()
        val oapsOutputProfile = atProfileFromOapsJson(JSONObject(oapsOutputProfileJson), dateUtil)
        val outProfile = autotuneCore.tuneAllTheThings(prep, inputProfile, pumpProfile)
        oapsOutputProfile?.let {
            Assert.assertEquals(oapsOutputProfile.isf, outProfile.isf, 0.0)
            Assert.assertEquals(oapsOutputProfile.ic, outProfile.ic, 0.0)
            for (i in 0..23)
                Assert.assertEquals(oapsOutputProfile.basal[i], outProfile.basal[i], 0.0)
        }
            ?: Assert.fail()
    }

    /**
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
                basalBlocks = basalBlocks,
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
}
