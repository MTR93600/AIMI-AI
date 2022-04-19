package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.command.BleBolusCommand
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.service.MedLinkServiceData
import info.nightscout.shared.logging.AAPSLogger
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock

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