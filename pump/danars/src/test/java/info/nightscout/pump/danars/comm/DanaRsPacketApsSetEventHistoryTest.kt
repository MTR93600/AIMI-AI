package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketApsSetEventHistoryTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacket) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
            if (it is DanaRSPacketAPSSetEventHistory) {
                it.danaPump = danaPump
            }
        }
    }

    @Test fun runTest() { // test for negative carbs
        val now = dateUtil.now()
        var historyTest = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, now, -1, 0)
        var testParams = historyTest.getRequestParams()
        Assertions.assertEquals(0.toByte(), testParams[8])
        // 5g carbs
        historyTest = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, now, 5, 0)
        testParams = historyTest.getRequestParams()
        Assertions.assertEquals(5.toByte(), testParams[8])
        // 150g carbs
        historyTest = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, now, 150, 0)
        testParams = historyTest.getRequestParams()
        Assertions.assertEquals(150.toByte(), testParams[8])
        // test message generation
        historyTest = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, now, 5, 0)
        testParams = historyTest.getRequestParams()
        Assertions.assertEquals(5.toByte(), testParams[8])
        Assertions.assertEquals(11, testParams.size)
        Assertions.assertEquals(DanaPump.HistoryEntry.CARBS.value.toByte(), testParams[0])
        // test message decoding
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, historyTest.failed)
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(true, historyTest.failed)
        Assertions.assertEquals("APS_SET_EVENT_HISTORY", historyTest.friendlyName)
    }
}