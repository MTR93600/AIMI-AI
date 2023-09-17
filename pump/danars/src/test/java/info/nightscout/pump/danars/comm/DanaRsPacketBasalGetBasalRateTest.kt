package info.nightscout.pump.danars.comm

import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.pump.danars.DanaRSTestBase
import org.junit.jupiter.api.Test

class DanaRsPacketBasalGetBasalRateTest : DanaRSTestBase() {

    private val packetInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is DanaRSPacketBasalGetBasalRate) {
                it.aapsLogger = aapsLogger
                it.rxBus = rxBus
                it.rh = rh
                it.danaPump = danaPump
                it.uiInteraction = uiInteraction
            }
        }
    }

    @Test fun runTest() {
        val packet = DanaRSPacketBasalGetBasalRate(packetInjector)
        // test message decoding
        // rate is 0.01
        val array = ByteArray(100)
        putIntToArray(array, 0, (1.0 * 100).toInt())
        putByteToArray(array, 2, (0.05 * 100).toInt().toByte())
        packet.handleMessage(array)
        assertThat(danaPump.maxBasal).isWithin(0.001).of(1.0)
        assertThat(danaPump.basalStep).isWithin(0.001).of(0.05)
        assertThat(packet.failed).isTrue()
        assertThat(packet.friendlyName).isEqualTo("BASAL__GET_BASAL_RATE")
    }
}
