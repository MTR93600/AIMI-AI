package info.nightscout.androidaps.plugins.pump.danaR.comm

import info.nightscout.androidaps.danar.comm.MessageHashTableR
import info.nightscout.androidaps.interfaces.Constraint
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito

class MessageHashTableRTest : DanaRTestBase() {

    @Test fun runTest() {
        Mockito.`when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(Constraint(0.0))
        val messageHashTable = MessageHashTableR(injector)
        val testMessage = messageHashTable.findMessage(0x41f2)
        Assert.assertEquals("CMD_HISTORY_ALL", testMessage.messageName)
    }
}