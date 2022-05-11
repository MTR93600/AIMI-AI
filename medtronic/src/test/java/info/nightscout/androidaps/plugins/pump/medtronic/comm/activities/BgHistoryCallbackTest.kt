package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.argThat
import org.mockito.Mockito.times
import java.util.function.Supplier

class BgHistoryCallbackTest : TestBase() {

    @Mock lateinit var plugin: MedLinkMedtronicPumpPlugin
    private val rxBus = RxBus(aapsSchedulers, aapsLogger)
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var config: Config


    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is MedLinkMedtronicPumpPlugin) {
                it.sp = sp
                it.rxBus = RxBus(aapsSchedulers, aapsLogger)
            }
        }
    }
    val cCommandString = """c command confirmed
cgm history page number is:  01
cgm history:
---end of data---
bg:106  08:15  30-04-2022
bg:110  08:10  30-04-2022
bg:114  08:05  30-04-2022
bg:114  08:00  30-04-2022
bg:114  07:55  30-04-2022
bg:116  07:50  30-04-2022
bg:116  07:45  30-04-2022
bg:116  07:40  30-04-2022
bg:114  07:35  30-04-2022
bg:114  07:30  30-04-2022
bg:114  07:25  30-04-2022
bg:114  07:20  30-04-2022
bg:116  07:15  30-04-2022
bg:116  07:10  30-04-2022
bg:116  07:05  30-04-2022
bg:112  07:00  30-04-2022
bg:114  06:55  30-04-2022
bg:116  06:50  30-04-2022
bg:118  06:45  30-04-2022
bg:118  06:40  30-04-2022
bg:118  06:35  30-04-2022
bg:114  06:30  30-04-2022
bg:114  06:25  30-04-2022
bg:112  06:20  30-04-2022
bg:110  06:15  30-04-2022
bg:110  06:10  30-04-2022
bg:108  06:05  30-04-2022
bg:108  06:00  30-04-2022
bg:106  05:55  30-04-2022
bg:106  05:50  30-04-2022
bg:104  05:45  30-04-2022
bg:106  05:40  30-04-2022
bg:102  05:35  30-04-2022
bg:100  05:30  30-04-2022
bg:102  05:25  30-04-2022
bg:106  05:20  30-04-2022
bg:110  05:15  30-04-2022
bg:112  05:10  30-04-2022
bg:112  05:05  30-04-2022
bg:110  05:00  30-04-2022
bg:110  04:55  30-04-2022
bg:110  04:50  30-04-2022
bg:108  04:45  30-04-2022
bg:106  04:40  30-04-2022
---beginning of data---

ready""".lowercase()


    @Test
    fun testLowMemAddressParsing() {
        val callback = BGHistoryCallback(injector,plugin,aapsLogger, true)
        Mockito.`when`(plugin.sp).thenReturn(sp)
        Mockito.`when`(sp.getBoolean(R.bool.key_medlink_change_cannula, true)).thenReturn(false)
        callback.apply(Supplier{cCommandString.split("\n").stream()})
        Mockito.verify(plugin,times(1)).handleNewCareportalEvent(
            argThat{ it.get().count() ==2L })
        // Mockito.verify(pumpSync, times(2)).insertTherapyEventIfNewWithTimestamp(anyLong(),
        //                                                                         anyObject(), anyString(), anyLong(), anyObject(), anyString())
    }

}