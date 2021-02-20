package info.nightscout.androidaps.plugins.general.overview

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.text.toSpanned
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjoe64.graphview.GraphView
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.databinding.OverviewFragmentBinding
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.plugins.general.wear.events.EventWearDoAction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.source.DexcomPlugin
import info.nightscout.androidaps.plugins.source.XdripPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.CommandQueue
import info.nightscout.androidaps.skins.SkinProvider
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.directionToIcon
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.UIRunnable
import info.nightscout.androidaps.utils.wizard.QuickWizard
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class OverviewFragment : DaggerFragment(), View.OnClickListener, OnLongClickListener {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var nsDeviceStatus: NSDeviceStatus
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var dexcomPlugin: DexcomPlugin
    @Inject lateinit var dexcomMediator: DexcomPlugin.DexcomMediator
    @Inject lateinit var xdripPlugin: XdripPlugin
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var config: Config
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var databaseHelper: DatabaseHelperInterface
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    private var smallWidth = false
    private var smallHeight = false
    private lateinit var dm: DisplayMetrics
    private var axisWidth: Int = 0
    private var rangeToDisplay = 6 // for graph
    private var loopHandler = Handler()
    private var refreshLoop: Runnable? = null

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

    private var carbAnimation: AnimationDrawable? = null

    private val graphLock = Object()

    private var _binding: OverviewFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OverviewFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            //check screen width
            dm = DisplayMetrics()
            activity?.windowManager?.defaultDisplay?.getMetrics(dm)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // pre-process landscape mode
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT
        val landscape = screenHeight < screenWidth

        skinProvider.activeSkin().preProcessLandscapeOverviewLayout(dm, view, landscape, resourceHelper.gb(R.bool.isTablet), smallHeight)
        binding.nsclientLayout.visibility = config.NSCLIENT.toVisibility()

        binding.loopPumpStatusLayout.pumpStatus.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder))

        binding.notifications.setHasFixedSize(false)
        binding.notifications.layoutManager = LinearLayoutManager(view.context)
        axisWidth = if (dm.densityDpi <= 120) 3 else if (dm.densityDpi <= 160) 10 else if (dm.densityDpi <= 320) 35 else if (dm.densityDpi <= 420) 50 else if (dm.densityDpi <= 560) 70 else 80
        binding.graphsLayout.bgGraph.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
        binding.graphsLayout.bgGraph.gridLabelRenderer?.reloadStyles()
        binding.graphsLayout.bgGraph.gridLabelRenderer?.labelVerticalWidth = axisWidth
        binding.graphsLayout.bgGraph.layoutParams?.height = resourceHelper.dpToPx(skinProvider.activeSkin().mainGraphHeight)

        carbAnimation = binding.infoLayout.carbsIcon.background as AnimationDrawable?
        carbAnimation?.setEnterFadeDuration(1200)
        carbAnimation?.setExitFadeDuration(1200)

        rangeToDisplay = sp.getInt(R.string.key_rangetodisplay, 6)

        binding.graphsLayout.bgGraph.setOnLongClickListener {
            rangeToDisplay += 6
            rangeToDisplay = if (rangeToDisplay > 24) 6 else rangeToDisplay
            sp.putInt(R.string.key_rangetodisplay, rangeToDisplay)
            updateGUI("rangeChange")
            sp.putBoolean(R.string.key_objectiveusescale, true)
            false
        }
        prepareGraphsIfNeeded(overviewMenus.setting.size)
        overviewMenus.setupChartMenu(binding.graphsLayout.chartMenuButton)

        binding.loopPumpStatusLayout.activeProfile.setOnClickListener(this)
        binding.loopPumpStatusLayout.activeProfile.setOnLongClickListener(this)
        binding.loopPumpStatusLayout.tempTarget.setOnClickListener(this)
        binding.loopPumpStatusLayout.tempTarget.setOnLongClickListener(this)
        binding.buttonsLayout.acceptTempButton.setOnClickListener(this)
        binding.buttonsLayout.treatmentButton.setOnClickListener(this)
        binding.buttonsLayout.wizardButton.setOnClickListener(this)
        binding.buttonsLayout.calibrationButton.setOnClickListener(this)
        binding.buttonsLayout.cgmButton.setOnClickListener(this)
        binding.buttonsLayout.insulinButton.setOnClickListener(this)
        binding.buttonsLayout.carbsButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnLongClickListener(this)
        binding.infoLayout.apsMode.setOnClickListener(this)
        binding.infoLayout.apsMode.setOnLongClickListener(this)
        binding.loopPumpStatusLayout.activeProfile.setOnLongClickListener(this)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                if (it.now) updateGUI(it.from)
                else scheduleUpdateGUI(it.from)
            }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventExtendedBolusChange") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventTempBasalChange") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventTreatmentChange") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventTempTargetChange") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventAcceptOpenLoopChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventAcceptOpenLoopChange") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventCareportalEventChange") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventInitializationChanged") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventAutosensCalculationFinished") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventProfileNeedsUpdate::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventProfileNeedsUpdate") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventPreferenceChange") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventNewOpenLoopNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI("EventNewOpenLoopNotification") }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updatePumpStatus(it) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventIobCalculationProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ binding.graphsLayout.iobCalculationProgress.text = it.progress }, fabricPrivacy::logException))

        refreshLoop = Runnable {
            scheduleUpdateGUI("refreshLoop")
            loopHandler.postDelayed(refreshLoop, 60 * 1000L)
        }
        loopHandler.postDelayed(refreshLoop, 60 * 1000L)

        updateGUI("onResume")
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onClick(v: View) {
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (childFragmentManager.isStateSaved) return
        activity?.let { activity ->
            when (v.id) {
                R.id.treatment_button -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) TreatmentDialog().show(childFragmentManager, "Overview") })
                R.id.wizard_button -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) WizardDialog().show(childFragmentManager, "Overview") })
                R.id.insulin_button -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) InsulinDialog().show(childFragmentManager, "Overview") })
                R.id.quick_wizard_button -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) onClickQuickWizard() })
                R.id.carbs_button -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) CarbsDialog().show(childFragmentManager, "Overview") })
                R.id.temp_target -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) TempTargetDialog().show(childFragmentManager, "Overview") })

                R.id.active_profile -> {
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also {
                            it.putLong("time", DateUtil.now())
                            it.putInt("mode", ProfileViewerDialog.Mode.RUNNING_PROFILE.ordinal)
                        }
                    }.show(childFragmentManager, "ProfileViewDialog")
                }

                R.id.cgm_button -> {
                    if (xdripPlugin.isEnabled(PluginType.BGSOURCE))
                        openCgmApp("com.eveningoutpost.dexdrip")
                    else if (dexcomPlugin.isEnabled(PluginType.BGSOURCE)) {
                        dexcomMediator.findDexcomPackageName()?.let {
                            openCgmApp(it)
                        }
                            ?: ToastUtils.showToastInUiThread(activity, resourceHelper.gs(R.string.dexcom_app_not_installed))
                    }
                }

                R.id.calibration_button -> {
                    if (xdripPlugin.isEnabled(PluginType.BGSOURCE)) {
                        CalibrationDialog().show(childFragmentManager, "CalibrationDialog")
                    } else if (dexcomPlugin.isEnabled(PluginType.BGSOURCE)) {
                        try {
                            dexcomMediator.findDexcomPackageName()?.let {
                                startActivity(Intent("com.dexcom.cgm.activities.MeterEntryActivity").setPackage(it))
                            }
                                ?: ToastUtils.showToastInUiThread(activity, resourceHelper.gs(R.string.dexcom_app_not_installed))
                        } catch (e: ActivityNotFoundException) {
                            ToastUtils.showToastInUiThread(activity, resourceHelper.gs(R.string.g5appnotdetected))
                        }
                    }
                }

                R.id.accept_temp_button -> {
                    profileFunction.getProfile() ?: return
                    if (loopPlugin.isEnabled(PluginType.LOOP)) {
                        val lastRun = loopPlugin.lastRun
                        loopPlugin.invoke("Accept temp button", false)
                        if (lastRun?.lastAPSRun != null && lastRun.constraintsProcessed?.isChangeRequested == true) {
                            protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.tempbasal_label), lastRun.constraintsProcessed?.toSpanned()
                                    ?: "".toSpanned(), {
                                    uel.log(resourceHelper.gs(R.string.key_uel_accepts_temp_basal))
                                    binding.buttonsLayout.acceptTempButton.visibility = View.GONE
                                    (context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Constants.notificationID)
                                    rxBus.send(EventWearDoAction("cancelChangeRequest"))
                                    loopPlugin.acceptChangeRequest()
                                })
                            })
                        }
                    }
                }

                R.id.aps_mode -> {
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        if (isAdded) LoopDialog().also { dialog ->
                            dialog.arguments = Bundle().also { it.putInt("showOkCancel", 1) }
                        }.show(childFragmentManager, "Overview")
                    })
                }
            }
        }
    }

    private fun openCgmApp(packageName: String) {
        context?.let {
            val packageManager = it.packageManager
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw ActivityNotFoundException()
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                it.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                OKDialog.show(it, "", resourceHelper.gs(R.string.error_starting_cgm))
            }
        }
    }

    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.quick_wizard_button -> {
                startActivity(Intent(v.context, QuickWizardListActivity::class.java))
                return true
            }

            R.id.aps_mode -> {
                activity?.let { activity ->
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        LoopDialog().also { dialog ->
                            dialog.arguments = Bundle().also { it.putInt("showOkCancel", 0) }
                        }.show(childFragmentManager, "Overview")
                    })
                }
            }

            R.id.temp_target -> v.performClick()
            R.id.active_profile -> activity?.let { activity -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { ProfileSwitchDialog().show(childFragmentManager, "Overview") }) }

        }
        return false
    }

    private fun onClickQuickWizard() {
        val actualBg = iobCobCalculatorPlugin.actualBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && actualBg != null && profile != null) {
            binding.buttonsLayout.quickWizardButton.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true)
            if (wizard.calculatedTotalInsulin > 0.0 && quickWizardEntry.carbs() > 0.0) {
                val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(quickWizardEntry.carbs())).value()
                activity?.let {
                    if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints) || carbsAfterConstraints != quickWizardEntry.carbs()) {
                        OKDialog.show(it, resourceHelper.gs(R.string.treatmentdeliveryerror), resourceHelper.gs(R.string.constraints_violation) + "\n" + resourceHelper.gs(R.string.changeyourinput))
                        return
                    }
                    wizard.confirmAndExecute(it)
                }
            }
        }
    }

    private fun updatePumpStatus(event: EventPumpStatusChanged) {
        val status = event.getStatus(resourceHelper)
        if (status != "") {
            binding.loopPumpStatusLayout.pumpStatus.text = status
            binding.loopPumpStatusLayout.pumpStatusLayout.visibility = View.VISIBLE
            binding.loopPumpStatusLayout.loopLayout.visibility = View.GONE
        } else {
            binding.loopPumpStatusLayout.pumpStatusLayout.visibility = View.GONE
            binding.loopPumpStatusLayout.loopLayout.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processButtonsVisibility() {
        val lastBG = iobCobCalculatorPlugin.lastBg()
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val actualBG = iobCobCalculatorPlugin.actualBg()

        // QuickWizard button
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && lastBG != null && profile != null && pump.isInitialized() && !pump.isSuspended()) {
            binding.buttonsLayout.quickWizardButton.visibility = View.VISIBLE
            val wizard = quickWizardEntry.doCalc(profile, profileName, lastBG, false)
            binding.buttonsLayout.quickWizardButton.text = quickWizardEntry.buttonText() + "\n" + resourceHelper.gs(R.string.format_carbs, quickWizardEntry.carbs()) +
                " " + resourceHelper.gs(R.string.formatinsulinunits, wizard.calculatedTotalInsulin)
            if (wizard.calculatedTotalInsulin <= 0) binding.buttonsLayout.quickWizardButton.visibility = View.GONE
        } else binding.buttonsLayout.quickWizardButton.visibility = View.GONE

        // **** Temp button ****
        val lastRun = loopPlugin.lastRun
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        val showAcceptButton = !closedLoopEnabled.value() && // Open mode needed
            lastRun != null &&
            (lastRun.lastOpenModeAccept == 0L || lastRun.lastOpenModeAccept < lastRun.lastAPSRun) &&// never accepted or before last result
            lastRun.constraintsProcessed?.isChangeRequested == true // change is requested

        if (showAcceptButton && pump.isInitialized() && !pump.isSuspended() && loopPlugin.isEnabled(PluginType.LOOP)) {
            binding.buttonsLayout.acceptTempButton.visibility = View.VISIBLE
            binding.buttonsLayout.acceptTempButton.text = "${resourceHelper.gs(R.string.setbasalquestion)}\n${lastRun!!.constraintsProcessed}"
        } else {
            binding.buttonsLayout.acceptTempButton.visibility = View.GONE
        }

        // **** Various treatment buttons ****
        binding.buttonsLayout.carbsButton.visibility = ((!activePlugin.activePump.pumpDescription.storesCarbInfo || pump.isInitialized() && !pump.isSuspended()) && profile != null && sp.getBoolean(R.string.key_show_carbs_button, true)).toVisibility()
        binding.buttonsLayout.treatmentButton.visibility = (pump.isInitialized() && !pump.isSuspended() && profile != null && sp.getBoolean(R.string.key_show_treatment_button, false)).toVisibility()
        binding.buttonsLayout.wizardButton.visibility = (pump.isInitialized() && !pump.isSuspended() && profile != null && sp.getBoolean(R.string.key_show_wizard_button, true)).toVisibility()
        binding.buttonsLayout.insulinButton.visibility = (pump.isInitialized() && !pump.isSuspended() && profile != null && sp.getBoolean(R.string.key_show_insulin_button, true)).toVisibility()

        // **** Calibration & CGM buttons ****
        val xDripIsBgSource = xdripPlugin.isEnabled(PluginType.BGSOURCE)
        val dexcomIsSource = dexcomPlugin.isEnabled(PluginType.BGSOURCE)
        binding.buttonsLayout.calibrationButton.visibility = ((xDripIsBgSource || dexcomIsSource) && actualBG != null && sp.getBoolean(R.string.key_show_calibration_button, true)).toVisibility()
        binding.buttonsLayout.cgmButton.visibility = (sp.getBoolean(R.string.key_show_cgm_button, false) && (xDripIsBgSource || dexcomIsSource)).toVisibility()

    }

    private fun prepareGraphsIfNeeded(numOfGraphs: Int) {
        synchronized(graphLock) {
            if (numOfGraphs != secondaryGraphs.size - 1) {
                //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
                // rebuild needed
                secondaryGraphs.clear()
                secondaryGraphsLabel.clear()
                binding.graphsLayout.iobGraph.removeAllViews()
                for (i in 1 until numOfGraphs) {
                    val relativeLayout = RelativeLayout(context)
                    relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                    val graph = GraphView(context)
                    graph.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, resourceHelper.dpToPx(skinProvider.activeSkin().secondaryGraphHeight)).also { it.setMargins(0, resourceHelper.dpToPx(15), 0, resourceHelper.dpToPx(10)) }
                    graph.gridLabelRenderer?.gridColor = resourceHelper.gc(R.color.graphgrid)
                    graph.gridLabelRenderer?.reloadStyles()
                    graph.gridLabelRenderer?.isHorizontalLabelsVisible = false
                    graph.gridLabelRenderer?.labelVerticalWidth = axisWidth
                    graph.gridLabelRenderer?.numVerticalLabels = 3
                    graph.viewport.backgroundColor = Color.argb(20, 255, 255, 255) // 8% of gray
                    relativeLayout.addView(graph)

                    val label = TextView(context)
                    val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(resourceHelper.dpToPx(30), resourceHelper.dpToPx(25), 0, 0) }
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                    label.layoutParams = layoutParams
                    relativeLayout.addView(label)
                    secondaryGraphsLabel.add(label)

                    binding.graphsLayout.iobGraph.addView(relativeLayout)
                    secondaryGraphs.add(graph)
                }
            }
        }
    }

    var task: Runnable? = null

    private fun scheduleUpdateGUI(from: String) {
        class UpdateRunnable : Runnable {

            override fun run() {
                updateGUI(from)
                task = null
            }
        }
        view?.removeCallbacks(task)
        task = UpdateRunnable()
        view?.postDelayed(task, 500)
    }

    @SuppressLint("SetTextI18n")
    fun updateGUI(from: String) {
        if (_binding == null) return
        aapsLogger.debug("UpdateGUI from $from")

        binding.infoLayout.time.text = dateUtil.timeString(Date())

        if (!profileFunction.isProfileValid("Overview")) {
            binding.loopPumpStatusLayout.pumpStatus.setText(R.string.noprofileset)
            binding.loopPumpStatusLayout.pumpStatusLayout.visibility = View.VISIBLE
            binding.loopPumpStatusLayout.loopLayout.visibility = View.GONE
            return
        }
        binding.notifications.let { notificationStore.updateNotifications(it) }
        binding.loopPumpStatusLayout.pumpStatusLayout.visibility = View.GONE
        binding.loopPumpStatusLayout.loopLayout.visibility = View.VISIBLE

        val profile = profileFunction.getProfile() ?: return
        val actualBG = iobCobCalculatorPlugin.actualBg()
        val lastBG = iobCobCalculatorPlugin.lastBg()
        val pump = activePlugin.activePump
        val units = profileFunction.getUnits()
        val lowLine = defaultValueHelper.determineLowLine()
        val highLine = defaultValueHelper.determineHighLine()
        val lastRun = loopPlugin.lastRun
        val predictionsAvailable = if (config.APS) lastRun?.request?.hasPredictions == true else config.NSCLIENT

        try {
            updateGraph(lastRun, predictionsAvailable, lowLine, highLine, pump, profile)
        } catch (e: IllegalStateException) {
            return // view no longer exists
        }

        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        if (lastBG != null) {
            val color = when {
                lastBG.valueToUnits(units) < lowLine  -> resourceHelper.gc(R.color.low)
                lastBG.valueToUnits(units) > highLine -> resourceHelper.gc(R.color.high)
                else                                  -> resourceHelper.gc(R.color.inrange)
            }

            binding.infoLayout.bg.text = lastBG.valueToUnitsString(units)
            binding.infoLayout.bg.setTextColor(color)
            binding.infoLayout.arrow.setImageResource(lastBG.trendArrow.directionToIcon())
            binding.infoLayout.arrow.setColorFilter(color)

            val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
            if (glucoseStatus != null) {
                binding.infoLayout.deltaLarge.text = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                binding.infoLayout.deltaLarge.setTextColor(color)
                binding.infoLayout.delta.text = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                binding.infoLayout.avgDelta.text = Profile.toSignedUnitsString(glucoseStatus.shortAvgDelta, glucoseStatus.shortAvgDelta * Constants.MGDL_TO_MMOLL, units)
                binding.infoLayout.longAvgDelta.text = Profile.toSignedUnitsString(glucoseStatus.longAvgDelta, glucoseStatus.longAvgDelta * Constants.MGDL_TO_MMOLL, units)
            } else {
                binding.infoLayout.delta.text = "Δ " + resourceHelper.gs(R.string.notavailable)
                binding.infoLayout.avgDelta.text = ""
                binding.infoLayout.longAvgDelta.text = ""
            }

            // strike through if BG is old
            binding.infoLayout.bg.let { overview_bg ->
                var flag = overview_bg.paintFlags
                flag = if (actualBG == null) {
                    flag or Paint.STRIKE_THRU_TEXT_FLAG
                } else flag and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                overview_bg.paintFlags = flag
            }
            binding.infoLayout.timeAgo.text = DateUtil.minAgo(resourceHelper, lastBG.timestamp)
            binding.infoLayout.timeAgoShort.text = "(" + DateUtil.minAgoShort(lastBG.timestamp) + ")"

        }
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        // aps mode
        if (config.APS && pump.pumpDescription.isTempBasalCapable) {
            binding.infoLayout.apsMode.visibility = View.VISIBLE
            binding.infoLayout.timeLayout.visibility = View.GONE
            when {
                loopPlugin.isEnabled() && loopPlugin.isSuperBolus                       -> {
                    binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_superbolus)
                    binding.infoLayout.apsModeText.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                    binding.infoLayout.apsModeText.visibility = View.VISIBLE
                }

                loopPlugin.isDisconnected                                               -> {
                    binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_disconnected)
                    binding.infoLayout.apsModeText.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                    binding.infoLayout.apsModeText.visibility = View.VISIBLE
                }

                loopPlugin.isEnabled() && loopPlugin.isSuspended                        -> {
                    binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_paused)
                    binding.infoLayout.apsModeText.text = DateUtil.age(loopPlugin.minutesToEndOfSuspend() * 60000L, true, resourceHelper)
                    binding.infoLayout.apsModeText.visibility = View.VISIBLE
                }

                pump.isSuspended()                                                        -> {
                    binding.infoLayout.apsMode.setImageResource(if (pump.pumpDescription.pumpType == PumpType.Insulet_Omnipod) {
                        // For Omnipod, indicate the pump as disconnected when it's suspended.
                        // The only way to 'reconnect' it, is through the Omnipod tab
                        R.drawable.ic_loop_disconnected
                    } else {
                        R.drawable.ic_loop_paused
                    })
                    binding.infoLayout.apsModeText.visibility = View.GONE
                }

                loopPlugin.isEnabled() && closedLoopEnabled.value() && loopPlugin.isLGS -> {
                    binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_lgs)
                    binding.infoLayout.apsModeText.visibility = View.GONE
                }

                loopPlugin.isEnabled() && closedLoopEnabled.value()                     -> {
                    binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_closed)
                    binding.infoLayout.apsModeText.visibility = View.GONE
                }

                loopPlugin.isEnabled() && !closedLoopEnabled.value()                    -> {
                    binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_open)
                    binding.infoLayout.apsModeText.visibility = View.GONE
                }

                else                                                                    -> {
                    binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_disabled)
                    binding.infoLayout.apsModeText.visibility = View.GONE
                }
            }
        } else {
            //nsclient
            binding.infoLayout.apsMode.visibility = View.GONE
            binding.infoLayout.apsModeText.visibility = View.GONE
            binding.infoLayout.timeLayout.visibility = View.VISIBLE
        }

        // temp target
        val tempTarget = treatmentsPlugin.tempTargetFromHistory
        if (tempTarget != null) {
            binding.loopPumpStatusLayout.tempTarget.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
            binding.loopPumpStatusLayout.tempTarget.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
            binding.loopPumpStatusLayout.tempTarget.text = Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units) + " " + DateUtil.untilString(tempTarget.end(), resourceHelper)
        } else {
            // If the target is not the same as set in the profile then oref has overridden it
            val targetUsed = lastRun?.constraintsProcessed?.targetBG ?: 0.0

            if (targetUsed != 0.0 && abs(profile.targetMgdl - targetUsed) > 0.01) {
                aapsLogger.debug("Adjusted target. Profile: ${profile.targetMgdl} APS: $targetUsed")
                binding.loopPumpStatusLayout.tempTarget.text = Profile.toTargetRangeString(targetUsed, targetUsed, Constants.MGDL, units)
                binding.loopPumpStatusLayout.tempTarget.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
                binding.loopPumpStatusLayout.tempTarget.setBackgroundColor(resourceHelper.gc(R.color.tempTargetBackground))
            } else {
                binding.loopPumpStatusLayout.tempTarget.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault))
                binding.loopPumpStatusLayout.tempTarget.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault))
                binding.loopPumpStatusLayout.tempTarget.text = Profile.toTargetRangeString(profile.targetLowMgdl, profile.targetHighMgdl, Constants.MGDL, units)
            }
        }

        // Basal, TBR
        val activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())
        binding.infoLayout.baseBasal.text = activeTemp?.let { "T:" + activeTemp.toStringVeryShort() }
            ?: resourceHelper.gs(R.string.pump_basebasalrate, profile.basal)
        binding.infoLayout.basalLayout.setOnClickListener {
            var fullText = "${resourceHelper.gs(R.string.basebasalrate_label)}: ${resourceHelper.gs(R.string.pump_basebasalrate, profile.basal)}"
            if (activeTemp != null)
                fullText += "\n" + resourceHelper.gs(R.string.tempbasal_label) + ": " + activeTemp.toStringFull()
            activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.basal), fullText)
            }
        }
        binding.infoLayout.baseBasal.setTextColor(activeTemp?.let { resourceHelper.gc(R.color.basal) }
            ?: resourceHelper.gc(R.color.defaulttextcolor))

        binding.infoLayout.baseBasalIcon.setImageResource(R.drawable.ic_cp_basal_no_tbr)
        val percentRate = activeTemp?.tempBasalConvertedToPercent(System.currentTimeMillis(), profile)
            ?: 100
        if (percentRate > 100) binding.infoLayout.baseBasalIcon.setImageResource(R.drawable.ic_cp_basal_tbr_high)
        if (percentRate < 100) binding.infoLayout.baseBasalIcon.setImageResource(R.drawable.ic_cp_basal_tbr_low)

        // Extended bolus
        val extendedBolus = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis())
        binding.infoLayout.extendedBolus.text =
            if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses)
                resourceHelper.gs(R.string.pump_basebasalrate, extendedBolus.absoluteRate())
            else ""
        binding.infoLayout.extendedBolus.setOnClickListener {
            if (extendedBolus != null) activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.extended_bolus), extendedBolus.toString())
            }
        }
        binding.infoLayout.extendedLayout.visibility = (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses).toVisibility()

        // Active profile
        binding.loopPumpStatusLayout.activeProfile.text = profileFunction.getProfileNameWithDuration()
        if (profile.percentage != 100 || profile.timeshift != 0) {
            binding.loopPumpStatusLayout.activeProfile.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning))
            binding.loopPumpStatusLayout.activeProfile.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning))
        } else {
            binding.loopPumpStatusLayout.activeProfile.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault))
            binding.loopPumpStatusLayout.activeProfile.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault))
        }

        processButtonsVisibility()

        // iob
        treatmentsPlugin.updateTotalIOBTreatments()
        treatmentsPlugin.updateTotalIOBTempBasals()
        val bolusIob = treatmentsPlugin.lastCalculationTreatments.round()
        val basalIob = treatmentsPlugin.lastCalculationTempBasals.round()
        binding.infoLayout.iob.text = resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob)

        binding.infoLayout.iobLayout.setOnClickListener {
            activity?.let {
                OKDialog.show(it, resourceHelper.gs(R.string.iob),
                    resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob + basalIob.basaliob) + "\n" +
                        resourceHelper.gs(R.string.bolus) + ": " + resourceHelper.gs(R.string.formatinsulinunits, bolusIob.iob) + "\n" +
                        resourceHelper.gs(R.string.basal) + ": " + resourceHelper.gs(R.string.formatinsulinunits, basalIob.basaliob)
                )
            }
        }

        // Status lights
        binding.statusLightsLayout.statusLights.visibility = (sp.getBoolean(R.string.key_show_statuslights, true) || config.NSCLIENT).toVisibility()
        statusLightHandler.updateStatusLights(binding.statusLightsLayout.cannulaAge, binding.statusLightsLayout.insulinAge, binding.statusLightsLayout.reservoirLevel, binding.statusLightsLayout.sensorAge, null, binding.statusLightsLayout.pbAge, binding.statusLightsLayout.batteryLevel)

        // cob
        var cobText: String = resourceHelper.gs(R.string.value_unavailable_short)
        val cobInfo = iobCobCalculatorPlugin.getCobInfo(false, "Overview COB")
        if (cobInfo.displayCob != null) {
            cobText = resourceHelper.gs(R.string.format_carbs, cobInfo.displayCob.toInt())
            if (cobInfo.futureCarbs > 0) cobText += "(" + DecimalFormatter.to0Decimal(cobInfo.futureCarbs) + ")"
        }

        if (config.APS && lastRun?.constraintsProcessed != null) {
            if (lastRun.constraintsProcessed!!.carbsReq > 0) {
                //only display carbsreq when carbs have not been entered recently
                if (treatmentsPlugin.lastCarbTime < lastRun.lastAPSRun) {
                    cobText = cobText + " | " + lastRun.constraintsProcessed!!.carbsReq + " " + resourceHelper.gs(R.string.required)
                }
                binding.infoLayout.cob.text = cobText
                if (carbAnimation?.isRunning == false)
                    carbAnimation?.start()
            } else {
                binding.infoLayout.cob.text = cobText
                carbAnimation?.stop()
                carbAnimation?.selectDrawable(0)
            }
        } else binding.infoLayout.cob.text = cobText

        // pump status from ns
        binding.pump.text = nsDeviceStatus.pumpStatus
        binding.pump.setOnClickListener { activity?.let { OKDialog.show(it, resourceHelper.gs(R.string.pump), nsDeviceStatus.extendedPumpStatus) } }

        // OpenAPS status from ns
        binding.openaps.text = nsDeviceStatus.openApsStatus
        binding.openaps.setOnClickListener { activity?.let { OKDialog.show(it, resourceHelper.gs(R.string.openaps), nsDeviceStatus.extendedOpenApsStatus) } }

        // Uploader status from ns
        binding.uploader.text = nsDeviceStatus.uploaderStatusSpanned
        binding.uploader.setOnClickListener { activity?.let { OKDialog.show(it, resourceHelper.gs(R.string.uploader), nsDeviceStatus.extendedUploaderStatus) } }

        // Sensitivity
        if (sp.getBoolean(R.string.key_openapsama_useautosens, false) && constraintChecker.isAutosensModeEnabled().value()) {
            binding.infoLayout.sensitivityIcon.setImageResource(R.drawable.ic_swap_vert_black_48dp_green)
        } else {
            binding.infoLayout.sensitivityIcon.setImageResource(R.drawable.ic_x_swap_vert)
        }

        binding.infoLayout.sensitivity.text =
            iobCobCalculatorPlugin.getLastAutosensData("Overview")?.let { autosensData ->
                String.format(Locale.ENGLISH, "%.0f%%", autosensData.autosensResult.ratio * 100)
            } ?: ""
    }

    private fun updateGraph(lastRun: LoopInterface.LastRun?, predictionsAvailable: Boolean, lowLine: Double, highLine: Double, pump: PumpInterface, profile: Profile) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            if (_binding == null) return@launch
            val menuChartSettings = overviewMenus.setting
            prepareGraphsIfNeeded(menuChartSettings.size)
            val graphData = GraphData(injector, binding.graphsLayout.bgGraph, iobCobCalculatorPlugin, treatmentsPlugin)
            val secondaryGraphsData: ArrayList<GraphData> = ArrayList()

            // do preparation in different thread
            withContext(Dispatchers.Default) {
                // align to hours
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = System.currentTimeMillis()
                calendar[Calendar.MILLISECOND] = 0
                calendar[Calendar.SECOND] = 0
                calendar[Calendar.MINUTE] = 0
                calendar.add(Calendar.HOUR, 1)
                val hoursToFetch: Int
                val toTime: Long
                val fromTime: Long
                val endTime: Long
                val apsResult = if (config.APS) lastRun?.constraintsProcessed else NSDeviceStatus.getAPSResult(injector)
                if (predictionsAvailable && apsResult != null && menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal]) {
                    var predictionHours = (ceil(apsResult.latestPredictionsTime - System.currentTimeMillis().toDouble()) / (60 * 60 * 1000)).toInt()
                    predictionHours = min(2, predictionHours)
                    predictionHours = max(0, predictionHours)
                    hoursToFetch = rangeToDisplay - predictionHours
                    toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
                    fromTime = toTime - T.hours(hoursToFetch.toLong()).msecs()
                    endTime = toTime + T.hours(predictionHours.toLong()).msecs()
                } else {
                    hoursToFetch = rangeToDisplay
                    toTime = calendar.timeInMillis + 100000 // little bit more to avoid wrong rounding - GraphView specific
                    fromTime = toTime - T.hours(hoursToFetch.toLong()).msecs()
                    endTime = toTime
                }
                val now = System.currentTimeMillis()

                //  ------------------ 1st graph

                // **** In range Area ****
                graphData.addInRangeArea(fromTime, endTime, lowLine, highLine)

                // **** BG ****
                if (predictionsAvailable && menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal])
                    graphData.addBgReadings(fromTime, toTime, lowLine, highLine, apsResult?.predictions)
                else graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null)

                // Treatments
                graphData.addTreatments(fromTime, endTime)

                // set manual x bounds to have nice steps
                graphData.setNumVerticalLabels()
                graphData.formatAxis(fromTime, endTime)


                if (menuChartSettings[0][OverviewMenus.CharType.ACT.ordinal])
                    graphData.addActivity(fromTime, endTime, false, 0.8)

                // add basal data
                if (pump.pumpDescription.isTempBasalCapable && menuChartSettings[0][OverviewMenus.CharType.BAS.ordinal])
                    graphData.addBasals(fromTime, now, lowLine / graphData.maxY / 1.2)

                // add target line
                graphData.addTargetLine(fromTime, toTime, profile, loopPlugin.lastRun)

                // **** NOW line ****
                graphData.addNowLine(now)

                // ------------------ 2nd graph
                synchronized(graphLock) {
                    for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
                        val secondGraphData = GraphData(injector, secondaryGraphs[g], iobCobCalculatorPlugin, treatmentsPlugin)
                        var useABSForScale = false
                        var useIobForScale = false
                        var useCobForScale = false
                        var useDevForScale = false
                        var useRatioForScale = false
                        var useDSForScale = false
                        var useBGIForScale = false
                        when {
                            menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]      -> useABSForScale = true
                            menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]      -> useIobForScale = true
                            menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]      -> useCobForScale = true
                            menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]      -> useDevForScale = true
                            menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]      -> useRatioForScale = true
                            menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]      -> useBGIForScale = true
                            menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                        }
                        val alignIobScale = menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal] && menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]
                        val alignDevBgiScale = menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] && menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]

                        if (menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(fromTime, now, useABSForScale, 1.0)
                        if (menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(fromTime, now, useIobForScale, 1.0, menuChartSettings[g + 1][OverviewMenus.CharType.PRE.ordinal], alignIobScale)
                        if (menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(fromTime, now, useCobForScale, if (useCobForScale) 1.0 else 0.5)
                        if (menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(fromTime, now, useDevForScale, 1.0, alignDevBgiScale)
                        if (menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(fromTime, now, useRatioForScale, 1.0)
                        if (menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]) secondGraphData.addMinusBGI(fromTime, endTime, useBGIForScale, if(alignDevBgiScale) 1.0 else 0.8, alignDevBgiScale)
                        if (menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && buildHelper.isDev()) secondGraphData.addDeviationSlope(fromTime, now, useDSForScale, 1.0)

                        // set manual x bounds to have nice steps
                        secondGraphData.formatAxis(fromTime, endTime)
                        secondGraphData.addNowLine(now)
                        secondaryGraphsData.add(secondGraphData)
                    }
                }
            }
            // finally enforce drawing of graphs in UI thread
            graphData.performUpdate()
            synchronized(graphLock) {
                for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
                    secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)
                    secondaryGraphs[g].visibility = (
                        menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal] ||
                            menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal] ||
                            menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal] ||
                            menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] ||
                            menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal] ||
                            menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal] ||
                            menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal]
                        ).toVisibility()
                    secondaryGraphsData[g].performUpdate()
                }
            }
        }
    }
}
