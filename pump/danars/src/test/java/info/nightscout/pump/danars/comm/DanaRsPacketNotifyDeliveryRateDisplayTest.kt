package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.TemporaryBasalStorage
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.pump.dana.database.DanaHistoryDatabase
import info.nightscout.pump.danars.DanaRSTestBase
import info.nightscout.rx.events.EventOverviewBolusProgress
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRsPacketNotifyDeliveryRateDisplayTest : DanaRSTestBase() {

    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase
    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var pumpSync: PumpSync

    private lateinit var danaRSPlugin: info.nightscout.pump.danars.DanaRSPlugin

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketNotifyDeliveryRateDisplay) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.rh = rh
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() {
        `when`(rh.gs(ArgumentMatchers.anyInt(), anyObject())).thenReturn("SomeString")
        // val packet = DanaRS_Packet_Notify_Delivery_Rate_Display(1.0, Treatment(treatmentInjector))
        val packet = DanaRSPacketNotifyDeliveryRateDisplay(packetInjector)
        // test params
        Assertions.assertEquals(0, packet.getRequestParams().size)
        // test message decoding
        // 0% delivered
        packet.handleMessage(createArray(17, 0.toByte()))
        Assertions.assertEquals(true, packet.failed)
        // 100 % delivered
        packet.handleMessage(createArray(17, 1.toByte()))
        Assertions.assertEquals(false, packet.failed)
        Assertions.assertEquals("NOTIFY__DELIVERY_RATE_DISPLAY", packet.friendlyName)
    }

    @BeforeEach
    fun mock() {
        danaRSPlugin =
            info.nightscout.pump.danars.DanaRSPlugin(
                packetInjector,
                aapsLogger,
                aapsSchedulers,
                rxBus,
                context,
                rh,
                constraintChecker,
                profileFunction,
                sp,
                commandQueue,
                danaPump,
                pumpSync,
                detailedBolusInfoStorage,
                temporaryBasalStorage,
                fabricPrivacy,
                dateUtil,
                uiInteraction,
                danaHistoryDatabase,
                decimalFormatter
            )
        danaPump.bolusingTreatment = EventOverviewBolusProgress.Treatment(0.0, 0, true, 0)
    }
}