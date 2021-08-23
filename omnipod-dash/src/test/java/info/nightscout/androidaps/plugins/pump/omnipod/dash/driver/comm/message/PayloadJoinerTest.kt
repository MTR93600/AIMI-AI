package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

import com.google.crypto.tink.subtle.Hex
import info.nightscout.androidaps.extensions.toHex
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.PayloadJoiner
import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadJoinerTest {

    @Test fun testJoiner() {
        val f1 = Hex.decode("00,01,54,57,10,23,03,00,00,c0,ff,ff,ff,fe,08,20,2e,a8,50,30".replace(",", ""))
        val f2 = Hex.decode("01,04,bc,20,1f,f6,3d,00,01,a5,ff,ff,ff,fe,08,20,2e,a8,50,30".replace(",", ""))
        val payload = "54,57,10,23,03,00,00,c0,ff,ff,ff,fe,08,20,2e,a8,50,30,3d,00,01,a5".replace(",", "")
        val joiner = PayloadJoiner(f1)
        joiner.accumulate(f2)
        val actual = joiner.finalize()
        assertEquals(payload, actual.toHex())
    }
}
