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
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.argThat
import org.mockito.Mockito.times
import java.util.function.Supplier

class BolusHistoryCallbackTest : TestBase() {

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
    val changeReservoirString = """H command confirmed 
Bolus history: 
‑‑‑End of data‑‑‑ 


Bolus: 
Time:  13:24  07-04-2022 
Given BL:  1.000 U 
Set BL:  1.000 U 
Time PD:  0.0h  IOB:  6.300 U 
 
Reservoir change 
Time:  13:22  07-04-2022 
 
Reservoir rewind 
Time:  13:20  07-04-2022 
 
Bolus: 
Time:  13:17  07-04-2022 
Given BL:  4.400 U 
Set BL:  4.400 U 
Time PD:  0.0h  IOB:  5.450 U 
‑‑‑Beginning of data‑‑‑ """.lowercase()

    val changeCannulaString = """H command confirmed 
Bolus history: 
‑‑‑End of data‑‑‑ 


Bolus: 
Time:  13:24  07-04-2022 
Given BL:  1.000 U 
Set BL:  1.000 U 
Time PD:  0.0h  IOB:  6.300 U 
 
Reservoir change 
Time:  13:22  07-04-2022 

Bolus: 
Time:  13:17  07-04-2022 
Given BL:  4.400 U 
Set BL:  4.400 U 
Time PD:  0.0h  IOB:  5.450 U 
‑‑‑Beginning of data‑‑‑ """.lowercase()


    @Test
    fun testReservoirChange() {
        val callback = BolusHistoryCallback(aapsLogger,
        plugin)
        Mockito.`when`(plugin.sp).thenReturn(sp)
        Mockito.`when`(sp.getBoolean(R.bool.key_medlink_change_cannula, true)).thenReturn(false)
        callback.apply(Supplier{changeReservoirString.split("\n").stream()})
        Mockito.verify(plugin,times(1)).handleNewCareportalEvent(
            argThat{ it.get().count() ==2L })
        // Mockito.verify(pumpSync, times(2)).insertTherapyEventIfNewWithTimestamp(anyLong(),
        //                                                                         anyObject(), anyString(), anyLong(), anyObject(), anyString())
    }

    @Test
    fun testReservoirCannulaChange() {
        val callback = BolusHistoryCallback(aapsLogger,
                                            plugin)
        Mockito.`when`(plugin.sp).thenReturn(sp)
        Mockito.`when`(sp.getBoolean(R.bool.key_medlink_change_cannula, true)).thenReturn(true)
        callback.apply(Supplier{changeReservoirString.split("\n").stream()})

        Mockito.verify(plugin,times(1)).handleNewCareportalEvent(
            anyObject())
        // Mockito.verify(pumpSync, times(2)).insertTherapyEventIfNewWithTimestamp(anyLong(),
        //                                                                         anyObject(), anyString(), anyLong(), anyObject(), anyString())
    }

    @Test
    fun testCannulaChangeOptEnabled() {
        val callback = BolusHistoryCallback(aapsLogger,
                                            plugin)
        Mockito.`when`(plugin.sp).thenReturn(sp)
        Mockito.`when`(sp.getBoolean(R.bool.key_medlink_change_cannula, true)).thenReturn(true)
        callback.apply(Supplier{changeCannulaString.split("\n").stream()})

        Mockito.verify(plugin,times(1)).handleNewCareportalEvent(
            anyObject())
        // Mockito.verify(pumpSync, times(2)).insertTherapyEventIfNewWithTimestamp(anyLong(),
        //                                                                         anyObject(), anyString(), anyLong(), anyObject(), anyString())
    }

    @Test
    fun testCannulaChangeOptDisabled() {
        val callback = BolusHistoryCallback(aapsLogger,
                                            plugin)
        Mockito.`when`(plugin.sp).thenReturn(sp)
        Mockito.`when`(sp.getBoolean(R.bool.key_medlink_change_cannula, true)).thenReturn(false)
        callback.apply(Supplier{changeCannulaString.split("\n").stream()})

        Mockito.verify(plugin,times(1)).handleNewCareportalEvent(
            anyObject())
        // Mockito.verify(pumpSync, times(2)).insertTherapyEventIfNewWithTimestamp(anyLong(),
        //                                                                         anyObject(), anyString(), anyLong(), anyObject(), anyString())
    }
}