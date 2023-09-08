package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import info.nightscout.database.impl.transactions.Transaction
import info.nightscout.interfaces.queue.Callback
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionStopTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStopTempTarget

    @BeforeEach
    fun setup() {
        `when`(rh.gs(info.nightscout.core.ui.R.string.stoptemptarget)).thenReturn("Stop temp target")

        sut = ActionStopTempTarget(injector)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(info.nightscout.core.ui.R.string.stoptemptarget, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assertions.assertEquals("Stop temp target", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(R.drawable.ic_stop_24dp, sut.icon())
    }

    @Test fun doActionTest() {
        val inserted = mutableListOf<TemporaryTarget>().apply {
            // insert all inserted TTs
        }
        val updated = mutableListOf<TemporaryTarget>().apply {
            // add(TemporaryTarget(id = 0, version = 0, dateCreated = 0, isValid = false, referenceId = null, interfaceIDs_backing = null, timestamp = 0, utcOffset = 0, reason =, highTarget = 0.0, lowTarget = 0.0, duration = 0))
            // insert all updated TTs
        }
        `when`(
            repository.runTransactionForResult(anyObject<Transaction<CancelCurrentTemporaryTargetIfAnyTransaction.TransactionResult>>())
        ).thenReturn(Single.just(CancelCurrentTemporaryTargetIfAnyTransaction.TransactionResult().apply {
            inserted.addAll(inserted)
            updated.addAll(updated)
        }))

        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
            }
        })
        Mockito.verify(repository, Mockito.times(1)).runTransactionForResult((anyObject<Transaction<CancelCurrentTemporaryTargetIfAnyTransaction.TransactionResult>>()))
    }

    @Test fun hasDialogTest() {
        Assertions.assertFalse(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        Assertions.assertEquals("{\"type\":\"ActionStopTempTarget\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"reason\":\"Test\"}")
        Assertions.assertNotNull(sut)
    }
}