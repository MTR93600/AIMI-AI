package info.nightscout.androidaps.plugins.general.automation

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.actions.ActionLoopEnable
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnectorTest
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock

class AutomationEventTest : TestBase() {

    @Mock lateinit var loopPlugin: Loop
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var configBuilder: ConfigBuilder

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AutomationEvent) {
                it.aapsLogger = aapsLogger
            }
            if (it is Action) {
                it.aapsLogger = aapsLogger
            }
            if (it is ActionLoopEnable) {
                it.loopPlugin = loopPlugin
                it.rh = rh
                it.configBuilder = configBuilder
                it.rxBus = RxBus(aapsSchedulers, aapsLogger)
            }
        }
    }

    @Test
    fun testCloneEvent() {
        // create test object
        val event = AutomationEvent(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest.oneItem)) as TriggerConnector
        event.addAction(ActionLoopEnable(injector))

        // export to json
        val eventJsonExpected =
            "{\"userAction\":false,\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"data\\\\\\\":{\\\\\\\"connectorType\\\\\\\":\\\\\\\"AND\\\\\\\",\\\\\\\"triggerList\\\\\\\":[]},\\\\\\\"type\\\\\\\":\\\\\\\"TriggerConnector\\\\\\\"}\\\"]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Test\",\"systemAction\":false,\"actions\":[\"{\\\"type\\\":\\\"ActionLoopEnable\\\"}\"],\"enabled\":true}"
        Assert.assertEquals(eventJsonExpected, event.toJSON())

        // clone
        val clone = AutomationEvent(injector).fromJSON(eventJsonExpected)

        // check title
        Assert.assertEquals(event.title, clone.title)

        // check trigger
        Assert.assertNotNull(clone.trigger)
        Assert.assertFalse(event.trigger === clone.trigger) // not the same object reference
        Assert.assertEquals(event.trigger.javaClass, clone.trigger.javaClass)
        Assert.assertEquals(event.trigger.toJSON(), clone.trigger.toJSON())

        // check action
        Assert.assertEquals(1, clone.actions.size)
        Assert.assertFalse(event.actions === clone.actions) // not the same object reference
        Assert.assertEquals(clone.toJSON(), clone.toJSON())
    }
}