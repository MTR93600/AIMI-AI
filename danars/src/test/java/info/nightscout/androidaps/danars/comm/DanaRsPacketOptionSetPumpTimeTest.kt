package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRsPacketOptionSetPumpTimeTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet) {
                it.aapsLogger = aapsLogger
                it.dateUtil = dateUtil
            }
        }
    }

    @Test fun runTest() {
        val date = DateTime()
        val packet = DanaRS_Packet_Option_Set_Pump_Time(packetInjector, date.millis)
        // test params
        val params = packet.requestParams
        Assert.assertEquals((date.year - 2000 and 0xff).toByte(), params[0]) // 2019 -> 19
        Assert.assertEquals((date.monthOfYear and 0xff).toByte(), params[1])
        Assert.assertEquals((date.dayOfMonth and 0xff).toByte(), params[2])
        Assert.assertEquals((date.hourOfDay and 0xff).toByte(), params[3])
        Assert.assertEquals((date.minuteOfHour and 0xff).toByte(), params[4])
        Assert.assertEquals((date.secondOfMinute and 0xff).toByte(), params[5])
        // test message decoding
        packet.handleMessage(createArray(3, 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        // everything ok :)
        packet.handleMessage(createArray(17, 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("OPTION__SET_PUMP_TIME", packet.friendlyName)
    }
}