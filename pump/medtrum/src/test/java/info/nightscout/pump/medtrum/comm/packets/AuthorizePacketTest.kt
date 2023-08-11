package info.nightscout.pump.medtrum.comm.packets

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.MedtrumTestBase
import info.nightscout.pump.medtrum.extension.toByteArray
import org.junit.jupiter.api.Test
import org.junit.Assert.*

class AuthorizePacketTest : MedtrumTestBase() {

    /** Test packet specific behavoir */

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AuthorizePacket) {
                it.aapsLogger = aapsLogger
                it.medtrumPump = medtrumPump
            }
        }
    }

    @Test fun getRequestGivenPacketAndSNWhenCalledThenReturnAuthorizePacket() {
        // Inputs
        val opCode = 5
        val _pumpSN = MedtrumPump::class.java.getDeclaredField("_pumpSN")
        _pumpSN.isAccessible = true
        _pumpSN.setLong(medtrumPump, 2859923929)
        medtrumPump.patchSessionToken = 667

        // Call
        val packet = AuthorizePacket(packetInjector)
        val result = packet.getRequest()

        // Expected values
        val key = 3364239851
        val type = 2
        val expectedByteArray = byteArrayOf(opCode.toByte()) + type.toByte() + medtrumPump.patchSessionToken.toByteArray(4) + key.toByteArray(4)
        assertEquals(10, result.size)
        assertEquals(expectedByteArray.contentToString(), result.contentToString())
    }

    @Test fun handleResponseGivenResponseWhenMessageIsCorrectLengthThenResultTrue() {
        // Inputs
        val opCode = 5
        val responseCode = 0
        val deviceType = 80
        val swVerX = 12
        val swVerY = 1
        val swVerZ = 3

        // Call
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2) + 0.toByte() + deviceType.toByte() + swVerX.toByte() + swVerY.toByte() + swVerZ.toByte()
        val packet = AuthorizePacket(packetInjector)
        val result = packet.handleResponse(response)

        // Expected values
        val swString = "$swVerX.$swVerY.$swVerZ"
        assertTrue(result)
        assertFalse(packet.failed)
        assertEquals(deviceType, medtrumPump.deviceType)
        assertEquals(swString, medtrumPump.swVersion)
    }

    @Test fun handleResponseGivenResponseWhenMessageTooShortThenResultFalse() {
        // Inputs
        val opCode = 5
        val responseCode = 0

        // Call
        val response = byteArrayOf(0) + opCode.toByte() + 0.toByteArray(2) + responseCode.toByteArray(2)
        val packet = AuthorizePacket(packetInjector)
        packet.opCode = opCode.toByte()
        val result = packet.handleResponse(response)

        // Expected values
        assertFalse(result)
        assertTrue(packet.failed)
    }
}
