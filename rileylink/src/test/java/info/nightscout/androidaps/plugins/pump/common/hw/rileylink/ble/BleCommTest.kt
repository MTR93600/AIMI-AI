package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class BleCommTest {

    private var bleBolusCommand: BleBolusCommand? = null

    @Mock
    private val medlinkServiceData: MedLinkServiceData? = null

    @Mock
    private val aapsLogger: AAPSLogger? = null
    private fun initBleBolusCommand(): MedLinkServiceData? {
        bleBolusCommand = BleBolusCommand(aapsLogger, medlinkServiceData)
        return medlinkServiceData
    }

    @Test
    fun testBolus(){

    }
}