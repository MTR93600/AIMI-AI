package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test

class ComparatorConnectTest : TriggerTestBase() {

    @Test fun labelsTest() {
        Assert.assertEquals(2, ComparatorConnect.Compare.labels(rh).size)
    }

    @Test fun setValueTest() {
        val c = ComparatorConnect(rh)
        c.value = ComparatorConnect.Compare.ON_DISCONNECT
        Assert.assertEquals(ComparatorConnect.Compare.ON_DISCONNECT, c.value)
    }
}