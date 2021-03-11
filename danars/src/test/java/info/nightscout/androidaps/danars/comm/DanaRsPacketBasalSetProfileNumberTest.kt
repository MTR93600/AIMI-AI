package info.nightscout.androidaps.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.DanaRSTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DanaRsPacketBasalSetProfileNumberTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRS_Packet_Basal_Set_Profile_Number) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRS_Packet_Basal_Set_Profile_Number(packetInjector, 1)
        // test params
        val testParams = packet.requestParams
        // is profile 1
        Assert.assertEquals(1.toByte(), testParams[0])
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assert.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assert.assertEquals(true, packet.failed)
        Assert.assertEquals("BASAL__SET_PROFILE_NUMBER", packet.friendlyName)
    }
}