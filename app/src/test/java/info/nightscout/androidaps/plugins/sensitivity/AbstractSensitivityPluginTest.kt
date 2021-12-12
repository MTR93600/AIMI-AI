package info.nightscout.androidaps.plugins.sensitivity

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.Sensitivity
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensDataStore
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock

class AbstractSensitivityPluginTest : TestBase() {

    @Mock lateinit var pluginDescription: PluginDescription
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP

    private inner class SensitivityTestClass(pluginDescription: PluginDescription, aapsLogger: AAPSLogger, rh: ResourceHelper, sp: SP) : AbstractSensitivityPlugin(pluginDescription, HasAndroidInjector { AndroidInjector { } }, aapsLogger, rh, sp) {

        override fun detectSensitivity(ads: AutosensDataStore, fromTime: Long, toTime: Long): AutosensResult {
            return AutosensResult()
        }

        override val id: Sensitivity.SensitivityType
            get() = Sensitivity.SensitivityType.UNKNOWN

        override fun configuration(): JSONObject = JSONObject()

        override fun applyConfiguration(configuration: JSONObject) {}
    }

    @Test
    fun fillResultTest() {
        val sut = SensitivityTestClass(pluginDescription, aapsLogger, rh, sp)
        var ar = sut.fillResult(1.0, 1.0, "1",
            "1.2", "1", 12, 0.7, 1.2)
        Assert.assertEquals(1.0, ar.ratio, 0.01)
        ar = sut.fillResult(1.2, 1.0, "1",
            "1.2", "1", 40, 0.7, 1.2)
        Assert.assertEquals(1.16, ar.ratio, 0.01)
        ar = sut.fillResult(1.2, 1.0, "1",
            "1.2", "1", 50, 0.7, 1.2)
        Assert.assertEquals(1.2, ar.ratio, 0.01)
        ar = sut.fillResult(1.2, 1.0, "1",
            "1.2", "1", 50, 0.7, 1.1)
        Assert.assertEquals(1.1, ar.ratio, 0.01)
    }
}