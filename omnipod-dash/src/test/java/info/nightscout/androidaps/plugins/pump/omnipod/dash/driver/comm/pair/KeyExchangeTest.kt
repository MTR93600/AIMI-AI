package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import info.nightscout.androidaps.extensions.toHex
import info.nightscout.shared.logging.AAPSLoggerTest
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.RandomByteGenerator
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.X25519KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.spongycastle.util.encoders.Hex

class KeyExchangeTest {

    val keyGenerator = X25519KeyGenerator()
    val keyGeneratorSpy = spy(keyGenerator)

    var randomByteGenerator: RandomByteGenerator = mock(RandomByteGenerator::class.java)

    @Test fun testLTK() {
        val aapsLogger = AAPSLoggerTest()

        Mockito.doReturn(Hex.decode("27ec94b71a201c5e92698d668806ae5ba00594c307cf5566e60c1fc53a6f6bb6"))
            .`when`(keyGeneratorSpy).generatePrivateKey()

        val pdmNonce = Hex.decode("edfdacb242c7f4e1d2bc4d93ca3c5706")

        Mockito.`when`(randomByteGenerator.nextBytes(anyInt())).thenReturn(pdmNonce)

        val ke = KeyExchange(
            aapsLogger,
            keyGeneratorSpy,
            randomByteGenerator
        )
        val podPublicKey = Hex.decode("2fe57da347cd62431528daac5fbb290730fff684afc4cfc2ed90995f58cb3b74")
        val podNonce = Hex.decode("00000000000000000000000000000000")
        ke.updatePodPublicData(podPublicKey + podNonce)
        assertEquals(ke.pdmPublic.toHex(), "f2b6940243aba536a66e19fb9a39e37f1e76a1cd50ab59b3e05313b4fc93975e")
        assertEquals(ke.pdmConf.toHex(), "5fc3b4da865e838ceaf1e9e8bb85d1ac")
        ke.validatePodConf(Hex.decode("af4f10db5f96e5d9cd6cfc1f54f4a92f"))
        assertEquals(ke.ltk.toHex(), "341e16d13f1cbf73b19d1c2964fee02b")
    }
}
