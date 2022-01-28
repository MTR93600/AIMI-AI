package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test

class InputDurationTest : TriggerTestBase() {

    @Test fun setValueTest() {
        var i = InputDuration( 5, InputDuration.TimeUnit.MINUTES)
        Assert.assertEquals(5, i.value)
        Assert.assertEquals(InputDuration.TimeUnit.MINUTES, i.unit)
        i = InputDuration(5, InputDuration.TimeUnit.HOURS)
        Assert.assertEquals(5, i.value)
        Assert.assertEquals(InputDuration.TimeUnit.HOURS, i.unit)
        Assert.assertEquals(5 * 60, i.getMinutes())
        i.setMinutes(60)
        Assert.assertEquals(1, i.value)
    }
}