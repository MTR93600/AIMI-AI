package info.nightscout.automation

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.automation.services.LocationServiceHelper
import info.nightscout.automation.triggers.Trigger
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.TimerUtil
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito

class CarbTimerImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var loop: Loop
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var config: Config
    @Mock lateinit var locationServiceHelper: LocationServiceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Trigger) {
                it.profileFunction = profileFunction
                it.rh = rh
            }
        }
    }
    private lateinit var dateUtil: DateUtil
    private lateinit var timerUtil: TimerUtil

    private lateinit var automationPlugin: AutomationPlugin

    @BeforeEach
    fun init() {
        Mockito.`when`(rh.gs(anyInt())).thenReturn("")
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        dateUtil = DateUtil(context)
        timerUtil = TimerUtil(context)
        automationPlugin = AutomationPlugin(injector, rh, context, sp, fabricPrivacy, loop, rxBus, constraintChecker, aapsLogger, aapsSchedulers, config, locationServiceHelper, dateUtil,
                                            activePlugin, timerUtil)
    }

    @Test
    fun doTest() {
        Assertions.assertEquals(0, automationPlugin.size())
        automationPlugin.scheduleAutomationEventEatReminder()
        Assertions.assertEquals(1, automationPlugin.size())
        automationPlugin.removeAutomationEventEatReminder()
        Assertions.assertEquals(0, automationPlugin.size())

        automationPlugin.scheduleTimeToEatReminder(1)
        Mockito.verify(context, Mockito.times(1)).startActivity(any())
    }
}