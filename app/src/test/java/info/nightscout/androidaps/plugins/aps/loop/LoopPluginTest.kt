package info.nightscout.androidaps.plugins.aps.loop

import android.app.NotificationManager
import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.RunningConfiguration
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.MedLinkMedtronicPumpPlugin

import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

class LoopPluginTest : TestBase() {

    @Mock lateinit var sp: SP
    private val rxBus: RxBus = RxBus(aapsSchedulers, aapsLogger)
    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var context: Context
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var medLinkPumpPlugin: MedLinkMedtronicPumpPlugin
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var receiverStatusStore: ReceiverStatusStore
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var uel:UserEntryLogger
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var runningConfiguration: RunningConfiguration
    @Mock lateinit var config: Config

    lateinit var loopPlugin: LoopPlugin
    lateinit var medLinkLoopPlugin: LoopPlugin

    val injector = HasAndroidInjector { AndroidInjector { } }
    @Before fun prepareMock() {

        loopPlugin = LoopPlugin(injector, aapsLogger, aapsSchedulers, rxBus, sp, config,
                                constraintChecker, rh, profileFunction, context, commandQueue, activePlugin, virtualPumpPlugin, iobCobCalculator, receiverStatusStore, fabricPrivacy, dateUtil, uel, repository, runningConfiguration)
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)

        medLinkLoopPlugin = LoopPlugin(injector, aapsLogger, rxBus, sp, Config(), constraintChecker, resourceHelper, profileFunction, context, commandQueue, activePlugin, treatmentsPlugin, virtualPumpPlugin, actionStringHandler, iobCobCalculatorPlugin, receiverStatusStore, fabricPrivacy, nsUpload, hardLimits)
        `when`(activePlugin.activePump).thenReturn(medLinkPumpPlugin)
        `when`(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
    }


    @Test
    fun testPluginInterface() {
        `when`(rh.gs(R.string.loop)).thenReturn("Loop")
        `when`(rh.gs(R.string.loop_shortname)).thenReturn("LOOP")
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        val pumpDescription = PumpDescription()
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        Assert.assertEquals(LoopFragment::class.java.name, loopPlugin.pluginDescription.fragmentClass)
        Assert.assertEquals(PluginType.LOOP, loopPlugin.getType())
        Assert.assertEquals("Loop", loopPlugin.name)
        Assert.assertEquals("LOOP", loopPlugin.nameShort)
        Assert.assertEquals(true, loopPlugin.hasFragment())
        Assert.assertEquals(true, loopPlugin.showInList(PluginType.LOOP))
        Assert.assertEquals(R.xml.pref_loop.toLong(), loopPlugin.preferencesId.toLong())

        // Plugin is disabled by default
        Assert.assertEquals(false, loopPlugin.isEnabled())
        loopPlugin.setPluginEnabled(PluginType.LOOP, true)
        Assert.assertEquals(true, loopPlugin.isEnabled())

        // No temp basal capable pump should disable plugin
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = false
        Assert.assertEquals(false, loopPlugin.isEnabled())
        virtualPumpPlugin.pumpDescription.isTempBasalCapable = true

        // Fragment is hidden by default
        Assert.assertEquals(false, loopPlugin.isFragmentVisible())
        loopPlugin.setFragmentVisible(PluginType.LOOP, true)
        Assert.assertEquals(true, loopPlugin.isFragmentVisible())
    }

    @Mock lateinit var activeAPS: OpenAPSSMBPlugin
    @Mock lateinit var lastResult: APSResult
    @Mock lateinit var lastResult2: APSResult
    @Mock lateinit var event: Event

    @Test
    fun testPluginInoke() {
        preparePluginInvoke(2.0, 1, 0.5)
        medLinkLoopPlugin . invoke("Testing", false, false)
    }

    fun preparePluginInvoke(smb: Double, percent: Int, rate: Double) {
        `when`(resourceHelper.gs(R.string.loop)).thenReturn("Loop")
        `when`(resourceHelper.gs(R.string.loop_shortname)).thenReturn("LOOP")
        `when`(sp.getString(R.string.key_aps_mode, "open")).thenReturn("closed")
        val pumpDescription = PumpDescription()
        `when`(medLinkPumpPlugin.pumpDescription).thenReturn(pumpDescription)

        // Plugin is disabled by default
        Assert.assertEquals(false, medLinkLoopPlugin.isEnabled(PluginType.LOOP))
        medLinkLoopPlugin.setPluginEnabled(PluginType.LOOP, true)
        Assert.assertEquals(true, medLinkLoopPlugin.isEnabled(PluginType.LOOP))

        // No temp basal capable pump should disable plugin
        medLinkPumpPlugin.pumpDescription.isTempBasalCapable = false
        Assert.assertEquals(false, medLinkLoopPlugin.isEnabled(PluginType.LOOP))
        medLinkPumpPlugin.pumpDescription.isTempBasalCapable = true


        `when`(constraintChecker.isLoopInvocationAllowed()).thenReturn(Constraint(true))
        val basalValues = arrayOfNulls<ProfileValue>(4)
        basalValues[0] = profile.ProfileValue(7200, 0.5)
        basalValues[1] = profile.ProfileValue(14400, 1.0)
        basalValues[2] = profile.ProfileValue(3600, 1.5)
        basalValues[3] = profile.ProfileValue(14400, 2.0)
        PowerMockito.`when`<Array<ProfileValue?>>(profile.getBasalValues()).thenReturn(basalValues)
        `when`(profileFunction.isProfileValid("Loop")).thenReturn(true)
        `when`(profileFunction.getProfile()).thenReturn(profile);
        `when`(medLinkPumpPlugin.getBaseBasalRate()).thenReturn(0.5);
        `when`(activePlugin.activeAPS).thenReturn(activeAPS)
        `when`((activeAPS as PluginBase).isEnabled(PluginType.APS)).thenReturn(true)
        // `when`(activeAPS.invoke("Testing",false)).

        `when`(lastResult.newAndClone(injector)).thenReturn(lastResult)
        `when`(activeAPS.getLastAPSResult()).thenReturn(lastResult)

        val rateConstraint = Constraint(rate)
        val percentConstraint = Constraint(percent)
        val smbConstraint = Constraint(smb)
        `when`(lastResult.getRateConstraint()).thenReturn(rateConstraint)
        `when`(constraintChecker.applyBasalConstraints(lastResult.getRateConstraint(), profile)).thenReturn(rateConstraint)
        `when`(lastResult.getPercentConstraint()).thenReturn(percentConstraint)
        `when`(constraintChecker.applyBasalPercentConstraints(lastResult.getPercentConstraint(), profile)).thenReturn(percentConstraint)
        `when`(lastResult.getSMBConstraint()).thenReturn(smbConstraint)
        `when`(constraintChecker.applyBolusConstraints(lastResult.getSMBConstraint())).thenReturn(smbConstraint)
        `when`(treatmentsPlugin.getLastBolusTime()).thenReturn(System.currentTimeMillis() - 200000)
        `when`(lastResult.isChangeRequested()).thenReturn(true)
        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(Constraint(true))
        `when`(commandQueue.bolusInQueue()).thenReturn(false)
        `when`(commandQueue.isRunning(Command.CommandType.BOLUS)).thenReturn(false)
    }
    @Test
    fun testApplyTBRGreater() {
        preparePluginInvoke(2.0, 0, 3.0)
        medLinkLoopPlugin . invoke("Testing", false, false)
        val callback = object : Callback() {
            override fun run() {

            }
        }
        lastResult.tempBasalRequested = true
        lastResult.usePercent = false
        `when`(medLinkPumpPlugin.isInitialized).thenReturn(true)
        `when`(medLinkLoopPlugin.applyTBRRequest(lastResult, profile, callback)).thenCallRealMethod()
    }
    /* ***********  not working
    @Test
    public void eventTreatmentChangeShouldTriggerInvoke() {

        // Unregister tested plugin to prevent calling real invoke
        MainApp.bus().unregister(loopPlugin);

        class MockedLoopPlugin extends LoopPlugin {
            boolean invokeCalled = false;

            @Override
            public void invoke(String initiator, boolean allowNotification) {
                invokeCalled = true;
            }

        }

        MockedLoopPlugin mockedLoopPlugin = new MockedLoopPlugin();
        Treatment t = new Treatment();
        bus.post(new EventTreatmentChange(t));
        Assert.assertEquals(true, mockedLoopPlugin.invokeCalled);
    }
*/
}