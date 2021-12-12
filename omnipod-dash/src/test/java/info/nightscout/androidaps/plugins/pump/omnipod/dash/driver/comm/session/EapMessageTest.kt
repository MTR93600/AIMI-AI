package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.extensions.toHex
import info.nightscout.shared.logging.AAPSLoggerTest
import org.junit.Assert
import org.junit.Test
import org.spongycastle.util.encoders.Hex

class EapMessageTest {

    @Test fun testParseAndBack() {
        val aapsLogger = AAPSLoggerTest()
        val payload =
            Hex.decode("01bd0038170100000205000000c55c78e8d3b9b9e935860a7259f6c001050000c2cd1248451103bd77a6c7ef88c441ba7e0200006cff5d18")
        val eapMsg = EapMessage.parse(aapsLogger, payload)
        val back = eapMsg.toByteArray()
        Assert.assertEquals(back.toHex(), payload.toHex())
    }
}
