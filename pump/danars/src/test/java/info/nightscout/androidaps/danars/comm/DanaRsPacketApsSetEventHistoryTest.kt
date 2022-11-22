package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Test

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
        Assert.assertEquals(0.toByte(), testParams[8])
        // 5g carbs
        historyTest = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, now, 5, 0)
        testParams = historyTest.getRequestParams()
        Assert.assertEquals(5.toByte(), testParams[8])
        // 150g carbs
        historyTest = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, now, 150, 0)
        testParams = historyTest.getRequestParams()
        Assert.assertEquals(150.toByte(), testParams[8])
        // test message generation
        historyTest = DanaRSPacketAPSSetEventHistory(packetInjector, DanaPump.HistoryEntry.CARBS.value, now, 5, 0)
        testParams = historyTest.getRequestParams()
        Assert.assertEquals(5.toByte(), testParams[8])
        Assert.assertEquals(11, testParams.size)
        Assert.assertEquals(DanaPump.HistoryEntry.CARBS.value.toByte(), testParams[0])
        // test message decoding
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, historyTest.failed)
        historyTest.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, historyTest.failed)
        Assert.assertEquals("APS_SET_EVENT_HISTORY", historyTest.friendlyName)
    }
}