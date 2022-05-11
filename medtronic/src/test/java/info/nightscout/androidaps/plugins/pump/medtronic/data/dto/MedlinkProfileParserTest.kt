package info.nightscout.androidaps.plugins.pump.medtronic.data.dto

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedLinkMedtronicHistoryData
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedLinkMedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import org.joda.time.LocalDateTime
import org.joda.time.chrono.ISOChronology
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import java.util.*

/**
 * Created by Dirceu on 03/02/21.
 */
//@RunWith(PowerMockRunner.class)
//@PrepareForTest({AAPSLogger.class})
class MedlinkProfileParserTest {

    @Mock
    var injector: HasAndroidInjector? = null

    @Mock
    var inj: AndroidInjector<*>? = null

    @Mock
    var aapsLogger: AAPSLogger? = null

    //
    private val validProfile =
        "{\"dia\":\"6\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"10\"},{\"time\":\"2:00\",\"value\":\"11\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"

    //
    @Mock
    var rxBus: RxBus? = null

    @Mock
    var context: Context? = null

    @Mock
    var resourceHelper: ResourceHelper? = null

    @Mock
    var activePlugin: ActivePlugin? = null

    @Mock
    var sp: SP? = null

    @Mock
    var commandQueue: CommandQueue? = null

    @Mock
    var medtronicUtil: MedLinkMedtronicUtil? = null

    @Mock
    var medtronicPumpStatus: MedLinkMedtronicPumpStatus? = null

    @Mock
    var medtronicHistoryData: MedLinkMedtronicHistoryData? = null

    @Mock
    var rileyLinkServiceData: MedLinkServiceData? = null

    @Mock
    var dateUtil: DateUtil? = null

    @Mock
    var serviceTaskExecutor: ServiceTaskExecutor? = null

    @Mock
    var profile: Profile? = null

    @Mock
    var iso: ISOChronology? = null

    @Rule
    var rule = MockitoJUnit.rule()

    //
    //    @InjectMocks MedLinkMedtronicPumpPlugin plugin;
    @Mock
    var result: PumpEnactResult? = null

    @Mock
    var receiverStatusStore: ReceiverStatusStore? = null

       @Mock lateinit var medLinkPumpPlugin: MedLinkMedtronicPumpPlugin
    private fun buildTime(): LocalDateTime {
        return LocalDateTime(2020, 8, 10, 1, 0)
    }

    // private fun buildPlugin(): MedLinkMedtronicPumpPlugin {
    //     val currentTime = buildTime()
    //     DateTimeUtils.setCurrentMillisFixed(1597064400000L)
    //     val plugin: MedLinkMedtronicPumpPlugin = object : MedLinkMedtronicPumpPlugin(
    //         injector, aapsLogger!!,
    //         rxBus, context, resourceHelper, activePlugin, sp!!, commandQueue, null, medtronicUtil!!,
    //         medtronicPumpStatus!!, medtronicHistoryData!!, rileyLinkServiceData!!, serviceTaskExecutor!!,
    //         receiverStatusStore!!, dateUtil
    //     ) {
    //         override fun buildPumpEnactResult(): PumpEnactResult {
    //             return result!!
    //         }
    //     }
    //     PowerMockito.`when`(result.toString()).thenReturn("Mocked enactresult")
    //     Assert.assertNotNull("Plugin is null", plugin)
    //     return plugin
    // }

    @Test @Throws(Exception::class) fun testTempBasalIncreaseOnePeriod() {
        `when`(injector).thenReturn(injector)
        val parser = MedLinkProfileParser(injector, aapsLogger, medLinkPumpPlugin)
        val profile = """
            f command confirmed
            bolus wizard settings:
            max. bolus:  8.0u
            easy bolus step: 0.1u
            carb ratios:
            rate 1: 2.500 u/ex from 00:00
            rate 2: 3.000 u/ex from 06:00
            rate 3: 2.500 u/ex from 09:00
            rate 4: 2.000 u/ex from 11:00
            rate 5: 2.000 u/ex from 15:00
            rate 6: 2.000 u/ex from 18:00
            insulin sensitivities:
            rate 1:  54mg/dl from 00:00
            rate 2:  37mg/dl from 06:00
            rate 3:  54mg/dl from 22:30
            bg targets:
            rate 1: 80-120 from 00:00
            ready
            """.trimIndent()
        val basalProfile = BasalProfile(aapsLogger!!)
        val list: MutableList<BasalProfileEntry> = ArrayList()
        list.add(MedLinkBasalProfileEntry(0.3, 0, 30))
        list.add(MedLinkBasalProfileEntry(0.5, 10, 30))
        basalProfile.listEntries = list
        parser.parseProfile { Arrays.stream(profile.lowercase(Locale.getDefault()).split("\\n".toRegex()).toTypedArray()) }
    } //TODO build test of performing TEMP BASAL 235% 30 min
}