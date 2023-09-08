package info.nightscout.pump.danars.comm

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DanaRsPacketApsBasalSetTemporaryBasalTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketAPSBasalSetTemporaryBasal) {
                it.aapsLogger = aapsLogger
            }
        }
    }

    @ExperimentalUnsignedTypes
    @Test fun runTest() {

        // under 100% should last 30 min
        var packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 0)
        Assertions.assertEquals(0, packet.temporaryBasalRatio)
        Assertions.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM30MIN, packet.temporaryBasalDuration)
        //constructor with param
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 10)
        Assertions.assertEquals(10, packet.temporaryBasalRatio)
        Assertions.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM30MIN, packet.temporaryBasalDuration)
        // over 100% should last 15 min
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 150)
        Assertions.assertEquals(150, packet.temporaryBasalRatio)
        Assertions.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM15MIN, packet.temporaryBasalDuration)
        // test low hard limit
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, -1)
        Assertions.assertEquals(0, packet.temporaryBasalRatio)
        Assertions.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM30MIN, packet.temporaryBasalDuration)
        // test high hard limit
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 550)
        Assertions.assertEquals(500, packet.temporaryBasalRatio)
        Assertions.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM15MIN, packet.temporaryBasalDuration)
        // test message generation
        packet = DanaRSPacketAPSBasalSetTemporaryBasal(packetInjector, 260)
        val generatedCode = packet.getRequestParams()
        Assertions.assertEquals(3, generatedCode.size.toLong())
        Assertions.assertEquals(4.toByte(), generatedCode[0])
        Assertions.assertEquals(1.toByte(), generatedCode[1])
        Assertions.assertEquals(DanaRSPacketAPSBasalSetTemporaryBasal.PARAM15MIN.toUByte(), generatedCode[2].toUByte())
        // test message decoding
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 0.toByte()))
        Assertions.assertEquals(false, packet.failed)
        packet.handleMessage(byteArrayOf(0.toByte(), 0.toByte(), 1.toByte()))
        Assertions.assertEquals(true, packet.failed)
        Assertions.assertEquals("BASAL__APS_SET_TEMPORARY_BASAL", packet.friendlyName)
    }
}