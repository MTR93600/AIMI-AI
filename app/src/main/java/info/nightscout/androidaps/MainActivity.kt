package info.nightscout.androidaps

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PersistableBundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.TaskStackBuilder
import androidx.core.view.GravityCompat
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import dev.doubledot.doki.ui.DokiActivity
import info.nightscout.androidaps.activities.*
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.ActivityMainBinding
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.general.themeselector.ScrollingActivity
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.setupwizard.SetupWizardActivity
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.isRunningRealPumpTest
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.tabs.TabPageAdapter
import info.nightscout.androidaps.utils.ui.UIRunnable
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.*
import javax.inject.Inject
import kotlin.system.exitProcess
import com.ms_square.etsyblur.BlurSupport
import com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_CENTER
import com.google.android.material.bottomappbar.BottomAppBar.FAB_ALIGNMENT_MODE_END
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.extensions.directionToIcon
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.extensions.valueToUnitsString
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.androidaps.plugins.constraints.bgQualityCheck.BgQualityCheckPlugin
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity
import info.nightscout.androidaps.plugins.general.overview.events.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.source.DexcomPlugin
import info.nightscout.androidaps.plugins.source.XdripPlugin
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.ui.SingleClickButton
import info.nightscout.androidaps.utils.wizard.QuickWizard
import java.util.concurrent.TimeUnit
import kotlin.math.abs

open class MainActivity : NoSplashAppCompatActivity() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var androidPermission: AndroidPermission
    @Inject lateinit var sp: SP
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils
    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var loop: Loop
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var config: Config
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var overviewPlugin: OverviewPlugin
    @Inject lateinit var automationPlugin: AutomationPlugin
    @Inject lateinit var bgQualityCheckPlugin: BgQualityCheckPlugin
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dexcomPlugin: DexcomPlugin
    @Inject lateinit var dexcomMediator: DexcomPlugin.DexcomMediator
    @Inject lateinit var xdripPlugin: XdripPlugin

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null
    private var menuOpen = false
    private var isRotate = false
    private var deltashort = ""
    private var avgdelta = ""
    private lateinit var refreshLoop: Runnable
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var isProtectionCheckActive = false
    private lateinit var binding: ActivityMainBinding

    // change to selected theme in theme manager
    fun changeTheme(newTheme: Int) {
        setNewTheme(newTheme)
        refreshActivities()
    }

    // change to a new theme selected in theme manager
    fun setNewTheme(newTheme: Int) {
        sp.putInt("theme", newTheme)

        if ( sp.getString(R.string.key_use_dark_mode, "dark") == "dark") {
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", info.nightscout.androidaps.core.R.color.background_dark))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        } else {
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", info.nightscout.androidaps.core.R.color.background_light))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        }
        setTheme(newTheme)
        ThemeUtil.setActualTheme(newTheme)
    }

    // restart activities if something like theme change happens
    fun refreshActivities() {
        TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, MainActivity::class.java))
            .addNextIntent(this.intent)
            .startActivities()
        recreate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Iconify.with(FontAwesomeModule())
        if ( sp.getString(R.string.key_use_dark_mode, "dark") == "dark") {
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", info.nightscout.androidaps.core.R.color.background_dark))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        } else {
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", info.nightscout.androidaps.core.R.color.background_light))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        }
        setTheme(ThemeUtil.getThemeId(sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)))
        ThemeUtil.setActualTheme(ThemeUtil.getThemeId(sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)))
        LocaleHelper.update(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.bottomAppBar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        //bluring for navigation drawer
        BlurSupport.addTo( binding.mainDrawerLayout)

        var downX = 0F
        var downY = 0F
        var dx = 0F
        var dy = 0F
        //remember 3 dot icon for switching fab icon from center to right and back
        val overflowIcon = binding.bottomAppBar.overflowIcon

        // detect single tap like click
        class SingleTapDetector : GestureDetector.SimpleOnGestureListener() {

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                return true
            }
        }
        val gestureDetector = GestureDetector(this, SingleTapDetector())
        // set on touch listener for move detetction
        binding.fab.setOnTouchListener(@SuppressLint("ClickableViewAccessibility")
                                       fun(view: View, event: MotionEvent): Boolean {
            if (gestureDetector.onTouchEvent(event)) {
                // code for single tap or onclick
                onClick(view)
            } else {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                    }

                    MotionEvent.ACTION_MOVE -> {
                        dx += event.x - downX
                        dy += event.y - downY
                        binding.fab.translationX = dx
                    }

                    MotionEvent.ACTION_UP   -> {
                        if (binding.bottomAppBar.fabAlignmentMode == FAB_ALIGNMENT_MODE_CENTER) {
                            binding.bottomAppBar.fabAlignmentMode = FAB_ALIGNMENT_MODE_END
                            binding.bottomNavigation.menu.findItem(R.id.placeholder)?.isVisible = false
                            binding.bottomAppBar.overflowIcon = null
                        } else {
                            binding.bottomAppBar.fabAlignmentMode = FAB_ALIGNMENT_MODE_CENTER
                            binding.bottomNavigation.menu.findItem(R.id.placeholder)?.isVisible = true
                            binding.bottomAppBar.overflowIcon = overflowIcon
                        }
                    }
                }
            }
            return true
        })

        binding.statusLightsLayout.overviewBg.setOnClickListener {
            val fullText = avgdelta
            this.let {
                OKDialog.show(it, "Delta", fullText, null)
            }
        }

        binding.statusLightsLayout.apsMode.setOnClickListener  { view: View? -> onClick(view!!) }
        binding.statusLightsLayout.apsMode.setOnLongClickListener{ view: View? -> onLongClick(view!!) }
        binding.mainBottomFabMenu.treatmentButton.setOnClickListener { view: View? -> onClick(view!!) }
        binding.mainBottomFabMenu.calibrationButton.setOnClickListener { view: View? -> onClick(view!!) }
        binding.mainBottomFabMenu.quickwizardButton.setOnClickListener { view: View? -> onClick(view!!) }
        binding.mainBottomFabMenu.quickwizardButton.setOnLongClickListener { view: View? -> onLongClick(view!!) }

        setupBottomNavigationView()

        //fab menu
        //hide the fab menu icons and label
        //ViewAnimation.init(binding.mainBottomFabMenu.calibrationButton)
        //ViewAnimation.init(binding.mainBottomFabMenu.quickwizardButton)

        binding.mainBottomFabMenu.fabMenu.visibility = View.GONE

        // initialize screen wake lock
        processPreferenceChange(EventPreferenceChange(rh.gs(R.string.key_keep_screen_on)))
        binding.mainPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                setPluginPreferenceMenuName()
                checkPluginPreferences(binding.mainPager)
                // do the trick to show bottombar >> performHide and than performShow
                binding.bottomAppBar.performHide()
                binding.bottomAppBar.performShow()
                setDisabledMenuItemColorPluginPreferences()
            }
        })

        //Check here if loop plugin is disabled. Else check via constraints
        if (!(loop as PluginBase).isEnabled()) versionCheckerUtils.triggerCheckVersion()
        setUserStats()
        setupViews()
        disposable += rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.recreate) recreate()
                           else setupViews()
                           setWakeLock()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processPreferenceChange(it) }, fabricPrivacy::logException)
        if (startWizard() && !isRunningRealPumpTest()) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                startActivity(Intent(this, SetupWizardActivity::class.java))
            })
        }
        androidPermission.notifyForStoragePermission(this)
        androidPermission.notifyForBatteryOptimizationPermission(this)
        if (!config.NSCLIENT) androidPermission.notifyForLocationPermissions(this)
        if (config.PUMPDRIVERS) {
            androidPermission.notifyForSMSPermissions(this, smsCommunicatorPlugin)
            androidPermission.notifyForSystemWindowPermissions(this)
            androidPermission.notifyForBtConnectPermission(this)
        }
    }

    private fun openCgmApp(packageName: String) {
        this.let {
            val packageManager = it.packageManager
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: throw ActivityNotFoundException()
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                it.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                OKDialog.show(it, "", rh.gs(R.string.error_starting_cgm))
            }
        }
    }

    private fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.quickwizardButton -> {
                val i = Intent(v.context, QuickWizardListActivity::class.java)
                startActivity(i)
                return true
            }
            R.id.aps_mode     -> {
                val args = Bundle()
                args.putInt("showOkCancel", 0)                  // 0-> false
                val pvd = LoopDialog()
                pvd.arguments = args
                pvd.show(supportFragmentManager, "Overview")
            }
        }
        return false
    }

    private fun setupBottomNavigationView() {
        val manager = supportFragmentManager
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (manager.isStateSaved) return
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.wizardButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { WizardDialog().show(manager, "Main") })
                R.id.insulinButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { InsulinDialog().show(manager, "Main") })
                R.id.carbsButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { CarbsDialog().show(manager, "Main") })

                R.id.cgmButton -> {
                    if (xdripPlugin.isEnabled())
                        openCgmApp("com.eveningoutpost.dexdrip")
                    else if (dexcomPlugin.isEnabled()) {
                        dexcomMediator.findDexcomPackageName()?.let {
                            openCgmApp(it)
                        }
                            ?: ToastUtils.showToastInUiThread(this, rh.gs(R.string.dexcom_app_not_installed))
                    }
                }
            }
            return@setOnItemSelectedListener true
        }
    }

    open fun onClick(view: View) {
        action(view, view.id, supportFragmentManager)
    }

    fun action(view: View?, id: Int, manager: FragmentManager?) {
        val fillDialog = FillDialog()
        val newCareDialog = CareDialog()

        this.let {
            when (id) {
                R.id.sensorage -> {
                    newCareDialog.setOptions(CareDialog.EventType.SENSOR_INSERT, R.string.careportal_cgmsensorinsert).show(manager!!, "Actions")
                    return
                }

                R.id.reservoirView, R.id.cannulaOrPatch -> {
                    fillDialog.show(manager!!, "FillDialog")
                    return
                }

                R.id.batteryage -> {
                    newCareDialog.setOptions(CareDialog.EventType.BATTERY_CHANGE, R.string.careportal_pumpbatterychange).show(manager!!, "Actions")
                    return
                }

                R.id.fab -> {
                    isRotate = ViewAnimation.rotateFab(view, !isRotate)
                    if (isRotate) {
                        binding.mainBottomFabMenu.fabMenu.visibility = View.VISIBLE
                        ViewAnimation.showIn(binding.mainBottomFabMenu.calibrationButton)
                        ViewAnimation.showIn(binding.mainBottomFabMenu.quickwizardButton)
                        ViewAnimation.showIn(binding.mainBottomFabMenu.treatmentButton)
                    } else {
                        ViewAnimation.showOut(binding.mainBottomFabMenu.calibrationButton)
                        ViewAnimation.showOut(binding.mainBottomFabMenu.quickwizardButton)
                        ViewAnimation.showOut( binding.mainBottomFabMenu.treatmentButton)
                        binding.mainBottomFabMenu.fabMenu.visibility = View.GONE
                    }
                    binding.bottomAppBar.performHide()
                    binding.bottomAppBar.performShow()
                    return
                }

                R.id.treatmentButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { TreatmentDialog().show(manager!!, "MainActivity") })
                R.id.quickwizardButton -> protectionCheck.queryProtection(this, ProtectionCheck.Protection.BOLUS, UIRunnable { onClickQuickWizard() })

                R.id.calibrationButton  -> {
                    if (xdripPlugin.isEnabled()) {
                        CalibrationDialog().show(supportFragmentManager, "CalibrationDialog")
                    } else if (dexcomPlugin.isEnabled()) {
                        try {
                            dexcomMediator.findDexcomPackageName()?.let {
                                startActivity(
                                    Intent("com.dexcom.cgm.activities.MeterEntryActivity")
                                        .setPackage(it)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                )
                            }
                                ?: ToastUtils.showToastInUiThread(this, rh.gs(R.string.dexcom_app_not_installed))
                        } catch (e: ActivityNotFoundException) {
                            ToastUtils.showToastInUiThread(this, rh.gs(R.string.g5appnotdetected))
                        }
                    }
                }
                R.id.aps_mode     -> {
                    val args = Bundle()
                    args.putInt("showOkCancel", 1)
                    val pvd = LoopDialog()
                    pvd.arguments = args
                    pvd.show(manager!!, "Overview")
                }
            }
        }
    }

    private fun onClickQuickWizard() {
        val actualBg = iobCobCalculator.ads.actualBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val pump = activePlugin.activePump
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && actualBg != null && profile != null) {
            binding.mainBottomFabMenu.quickwizardButton.show()
            val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true)
            if (wizard.calculatedTotalInsulin > 0.0 && quickWizardEntry.carbs() > 0.0) {
                val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(quickWizardEntry.carbs())).value()
                this.let {
                    if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints) || carbsAfterConstraints != quickWizardEntry.carbs()) {
                        OKDialog.show(this, rh.gs(R.string.treatmentdeliveryerror), rh.gs(R.string.constraints_violation) + "\n" + rh.gs(R.string.changeyourinput))
                        return
                    }
                    wizard.confirmAndExecute(it)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processButtonsVisibility() {
        val xDripIsBgSource = xdripPlugin.isEnabled()
        val dexcomIsSource = dexcomPlugin.isEnabled()
        val lastBG = iobCobCalculator.ads.lastBg()
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val actualBG = iobCobCalculator.ads.actualBg()

        // QuickWizard button
        val quickWizardEntry = quickWizard.getActive()
        if (quickWizardEntry != null && lastBG != null && profile != null && pump.isInitialized() && !pump.isSuspended() && !loop.isDisconnected) {
            binding.mainBottomFabMenu.quickwizardButton.show()
            val wizard = quickWizardEntry.doCalc(profile, profileName, lastBG, false)
            binding.mainBottomFabMenu.quickwizardbuttonLabel.text = quickWizardEntry.buttonText() + "\n" + rh.gs(R.string.format_carbs, quickWizardEntry.carbs()) +
                " " + rh.gs(R.string.formatinsulinunits, wizard.calculatedTotalInsulin)
            if (wizard.calculatedTotalInsulin <= 0) binding.mainBottomFabMenu.quickwizardButton.hide()
        } else binding.mainBottomFabMenu.quickwizardButton.hide()

        val conditionPumpProfile = !loop.isDisconnected && pump.isInitialized() && !pump.isSuspended() && profile != null

        // **** Various buttons ****
        binding.mainBottomFabMenu.treatmentButton.visibility = (conditionPumpProfile  && sp.getBoolean(R.string.key_show_treatment_button, false)).toVisibility()

        binding.bottomNavigation.menu.findItem(R.id.carbsButton)?.isVisible =
            ((!activePlugin.activePump.pumpDescription.storesCarbInfo || pump.isInitialized() && !pump.isSuspended()) && profile != null
                && sp.getBoolean(R.string.key_show_carbs_button, true))

        binding.bottomNavigation.menu.findItem(R.id.wizardButton)?.isVisible = (conditionPumpProfile && sp.getBoolean(R.string.key_show_wizard_button, true))

        binding.bottomNavigation.menu.findItem(R.id.insulinButton)?.isVisible  =  (conditionPumpProfile && sp.getBoolean(R.string.key_show_insulin_button, true))

        // **** Calibration & CGM buttons ****
        binding.mainBottomFabMenu.calibrationButton.visibility = (xDripIsBgSource && actualBG != null && sp.getBoolean(R.string.key_show_calibration_button, true)).toVisibility()

        binding.bottomNavigation.menu.findItem(R.id.cgmButton)?.isVisible = (sp.getBoolean(R.string.key_show_cgm_button, false) && (xDripIsBgSource || dexcomIsSource))
        if (dexcomIsSource) {
            binding.bottomNavigation.menu.findItem(R.id.cgmButton).setIcon(R.drawable.ic_byoda)
        } else if (xDripIsBgSource) {
            binding.bottomNavigation.menu.findItem(R.id.cgmButton).setIcon(R.drawable.ic_xdrip)
        }
    }

    fun updateBg() {
        val units = profileFunction.getUnits()
        binding.statusLightsLayout.overviewBg.text =  overviewData.lastBg?.valueToUnitsString(units)
        binding.statusLightsLayout.overviewBg.setTextColor((overviewData.getlastBgColor(this)))
        binding.statusLightsLayout.overviewArrow.setImageResource(trendCalculator.getTrendArrow(overviewData.lastBg).directionToIcon())
        binding.statusLightsLayout.overviewArrow.setColorFilter(overviewData.getlastBgColor(this))
        binding.statusLightsLayout.overviewArrow.contentDescription = overviewData.lastBgDescription + " " + rh.gs(R.string.and) + " " + trendCalculator.getTrendDescription(overviewData.lastBg)
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        if (glucoseStatus != null) {
            findViewById<TextView>(R.id.overview_delta)?.text =  Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
            findViewById<TextView>(R.id.timeago)?.text = dateUtil.minAgo(rh, overviewData.lastBg?.timestamp)
            avgdelta =   "         Δ " + Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
            avgdelta +=   "\n15m Δ " +  Profile.toSignedUnitsString(glucoseStatus.shortAvgDelta, glucoseStatus.shortAvgDelta * Constants.MGDL_TO_MMOLL, units)
            avgdelta +=   "\n40m Δ " +  Profile.toSignedUnitsString(glucoseStatus.longAvgDelta, glucoseStatus.longAvgDelta * Constants.MGDL_TO_MMOLL, units)
        } else {
            avgdelta =  ""
        }

        /*
        binding.infoLayout.bg.text = overviewData.lastBg?.valueToUnitsString(units)
            ?: rh.gs(R.string.notavailable)
        binding.infoLayout.bg.setTextColor(overviewData.getlastBgColor(this))
        binding.infoLayout.arrow.setImageResource(trendCalculator.getTrendArrow(overviewData.lastBg).directionToIcon())
        binding.infoLayout.arrow.setColorFilter(overviewData.getlastBgColor(this))
        binding.infoLayout.arrow.contentDescription = overviewData.lastBgDescription + " " + rh.gs(R.string.and) + " " + trendCalculator.getTrendDescription(overviewData.lastBg)

        */

      /*  if (glucoseStatus != null) {
            binding.infoLayout.deltaLarge.text = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
            binding.infoLayout.deltaLarge.setTextColor(overviewData.getlastBgColor(this))
            binding.infoLayout.delta.text = Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
            binding.infoLayout.avgDelta.text = Profile.toSignedUnitsString(glucoseStatus.shortAvgDelta, glucoseStatus.shortAvgDelta * Constants.MGDL_TO_MMOLL, units)
            binding.infoLayout.longAvgDelta.text = Profile.toSignedUnitsString(glucoseStatus.longAvgDelta, glucoseStatus.longAvgDelta * Constants.MGDL_TO_MMOLL, units)
        } else {
            binding.infoLayout.deltaLarge.text = ""
            binding.infoLayout.delta.text = "Δ " + rh.gs(R.string.notavailable)
            binding.infoLayout.avgDelta.text = ""
            binding.infoLayout.longAvgDelta.text = ""
        }*/

        // strike through if BG is old
       /* binding.infoLayout.bg.paintFlags =
            if (!overviewData.isActualBg) binding.infoLayout.bg.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else binding.infoLayout.bg.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        val outDate = (if (!overviewData.isActualBg) rh.gs(R.string.a11y_bg_outdated) else "")
        binding.infoLayout.bg.contentDescription =
            rh.gs(R.string.a11y_blood_glucose) + " " + binding.infoLayout.bg.text.toString() + " " + overviewData.lastBgDescription + " " + outDate

        binding.infoLayout.timeAgo.text = dateUtil.minAgo(rh, overviewData.lastBg?.timestamp)
        binding.infoLayout.timeAgo.contentDescription = dateUtil.minAgoLong(rh, overviewData.lastBg?.timestamp)
        binding.infoLayout.timeAgoShort.text = "(" + dateUtil.minAgoShort(overviewData.lastBg?.timestamp) + ")"

        val qualityIcon = bgQualityCheckPlugin.icon()
        if (qualityIcon != 0) {
            binding.infoLayout.bgQuality.visibility = View.VISIBLE
            binding.infoLayout.bgQuality.setImageResource(qualityIcon)
            binding.infoLayout.bgQuality.contentDescription = rh.gs(R.string.a11y_bg_quality) + " " + bgQualityCheckPlugin.stateDescription()
            binding.infoLayout.bgQuality.setOnClickListener {
                this?.let { context -> OKDialog.show(context, rh.gs(R.string.data_status), bgQualityCheckPlugin.message) }
            }
        } else {
            binding.infoLayout.bgQuality.visibility = View.GONE
        }
        */
    }

    private fun upDateStatusLight() {
        // Status lights
        val isPatchPump = activePlugin.activePump.pumpDescription.isPatchPump
        binding.statusLightsLayout.apply {
            cannulaOrPatch.setImageResource(if (isPatchPump) R.drawable.ic_patch_pump_outline else R.drawable.ic_katheter)
            cannulaOrPatch.contentDescription = rh.gs(if (isPatchPump) R.string.statuslights_patch_pump_age else R.string.statuslights_cannula_age)
            cannulaOrPatch.scaleX = if (isPatchPump) 1.2f else 1.2f
            cannulaOrPatch.scaleY = cannulaOrPatch.scaleX
            insulinAge.visibility = isPatchPump.not().toVisibility()
            statusLights.visibility = (sp.getBoolean(R.string.key_show_statuslights, true) || config.NSCLIENT).toVisibility()
        }
        statusLightHandler.updateStatusLights(
            binding.statusLightsLayout.cannulaAge,
            binding.statusLightsLayout.insulinAge,
            binding.statusLightsLayout.reservoirLevel,
            binding.statusLightsLayout.sensorAge,
            null,
            binding.statusLightsLayout.pbAge,
            binding.statusLightsLayout.batteryLevel,
            rh.gac(this, R.attr.statuslightNormal),
            rh.gac(this, R.attr.statuslightWarning),
            rh.gac(this, R.attr.statuslightAlarm))
    }

    fun updateTime() {
        //binding.infoLayout.time.text = dateUtil.timeString(dateUtil.now())
        upDateStatusLight()
        processButtonsVisibility()
        processAps()
    }

    private fun checkPluginPreferences(viewPager: ViewPager2) {
        if (viewPager.currentItem >= 0) pluginPreferencesMenuItem?.isEnabled = (viewPager.adapter as TabPageAdapter).getPluginAt(viewPager.currentItem).preferencesId != -1
    }

    private fun startWizard(): Boolean =
        !sp.getBoolean(R.string.key_setupwizard_processed, false)

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        actionBarDrawerToggle.syncState()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
                if (!isProtectionCheckActive) {
            isProtectionCheckActive = true
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.APPLICATION, UIRunnable { isProtectionCheckActive = false },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed)) { isProtectionCheckActive = false; finish() } },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed)) { isProtectionCheckActive = false; finish() } }
            )
        }
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewTime::class.java)
            .debounce(2L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateTime() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewBg::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateBg() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateTime() }, fabricPrivacy::logException)

        updateTime()
        updateBg()
    }

    private fun setWakeLock() {
        val keepScreenOn = sp.getBoolean(R.string.key_keep_screen_on, false)
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun processAps() {
        val pump = activePlugin.activePump

        // aps mode
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        fun apsModeSetA11yLabel(stringRes: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                binding.statusLightsLayout.apsMode.stateDescription = rh.gs(stringRes)
            } else {
                binding.statusLightsLayout.apsMode.contentDescription = rh.gs(R.string.apsmode_title) + " " + rh.gs(stringRes)
            }
        }

        if (config.APS && pump.pumpDescription.isTempBasalCapable) {
            binding.statusLightsLayout.apsMode.visibility = View.VISIBLE
            when {
                (loop as PluginBase).isEnabled() && loop.isSuperBolus                       -> {
                    binding.statusLightsLayout.apsMode.setImageResource(R.drawable.ic_loop_superbolus)
                    apsModeSetA11yLabel(R.string.superbolus)
                    binding.statusLightsLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                    binding.statusLightsLayout.apsModeText.visibility = View.VISIBLE
                }

                loop.isDisconnected                                                         -> {
                    binding.statusLightsLayout.apsMode.setImageResource(R.drawable.ic_loop_disconnected)
                    apsModeSetA11yLabel(R.string.disconnected)
                    binding.statusLightsLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                    binding.statusLightsLayout.apsModeText.visibility = View.VISIBLE
                }

                (loop as PluginBase).isEnabled() && loop.isSuspended                        -> {
                    binding.statusLightsLayout.apsMode.setImageResource(R.drawable.ic_loop_paused)
                    apsModeSetA11yLabel(R.string.suspendloop_label)
                    binding.statusLightsLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                    binding.statusLightsLayout.apsModeText.visibility = View.VISIBLE
                }

                pump.isSuspended()                                                          -> {
                    binding.statusLightsLayout.apsMode.setImageResource(
                        if (pump.model() == PumpType.OMNIPOD_EROS || pump.model() == PumpType.OMNIPOD_DASH) {
                            // For Omnipod, indicate the pump as disconnected when it's suspended.
                            // The only way to 'reconnect' it, is through the Omnipod tab
                            apsModeSetA11yLabel(R.string.disconnected)
                            R.drawable.ic_loop_disconnected
                        } else {
                            apsModeSetA11yLabel(R.string.pump_paused)
                            R.drawable.ic_loop_paused
                        }
                    )
                    binding.statusLightsLayout.apsModeText.visibility = View.GONE
                }

                (loop as PluginBase).isEnabled() && closedLoopEnabled.value() && loop.isLGS -> {
                    binding.statusLightsLayout.apsMode.setImageResource(R.drawable.ic_loop_lgs)
                    apsModeSetA11yLabel(R.string.uel_lgs_loop_mode)
                    binding.statusLightsLayout.apsModeText.visibility = View.GONE
                }

                (loop as PluginBase).isEnabled() && closedLoopEnabled.value()               -> {
                    binding.statusLightsLayout.apsMode.setImageResource(R.drawable.ic_loop_closed)
                    apsModeSetA11yLabel(R.string.closedloop)
                    binding.statusLightsLayout.apsModeText.visibility = View.GONE
                }

                (loop as PluginBase).isEnabled() && !closedLoopEnabled.value()              -> {
                    binding.statusLightsLayout.apsMode.setImageResource(R.drawable.ic_loop_open)
                    apsModeSetA11yLabel(R.string.openloop)
                    binding.statusLightsLayout.apsModeText.visibility = View.GONE
                }

                else                                                                        -> {
                    binding.statusLightsLayout.apsMode.setImageResource(R.drawable.ic_loop_disabled)
                    apsModeSetA11yLabel(R.string.disabledloop)
                    binding.statusLightsLayout.apsModeText.visibility = View.GONE
                }
            }
        } else {
            //nsclient
            binding.statusLightsLayout.apsMode.visibility = View.GONE
            binding.statusLightsLayout.apsModeText.visibility = View.GONE
        }
    }

    private fun processPreferenceChange(ev: EventPreferenceChange) {
        if (ev.isChanged(rh, R.string.key_keep_screen_on)) setWakeLock()
        if (ev.isChanged(rh, R.string.key_skin)) recreate()
    }

    private fun setupViews() {
        // Menu
        val pageAdapter = TabPageAdapter(this)
        binding.mainNavigationView.setNavigationItemSelectedListener { true }
        val menu = binding.mainNavigationView.menu.also { it.clear() }
        var itemId = 0
        for (p in activePlugin.getPluginsList()) {
            pageAdapter.registerNewFragment(p)
            if (
                p.hasFragment() && p.isFragmentVisible() && p.isEnabled(p.pluginDescription.mainType) && !p.pluginDescription.neverVisible) {
                val menuItem = menu.add(Menu.NONE, itemId++, Menu.NONE, p.name)
                if(p.menuIcon != -1) {
                    menuItem.setIcon(p.menuIcon)
                } else
                {
                    menuItem.setIcon(R.drawable.ic_settings)
                }
                menuItem.isCheckable = true
                if (p.menuIcon != -1) {
                    menuItem.setIcon(p.menuIcon)
                } else {
                    menuItem.setIcon(R.drawable.ic_settings)
                }
                menuItem.setOnMenuItemClickListener {
                    binding.mainDrawerLayout.closeDrawers()
                    binding.mainPager.setCurrentItem(it.itemId, true)
                    true
                }
            }
        }
        binding.mainPager.adapter = pageAdapter
        binding.mainPager.offscreenPageLimit = 8 // This may cause more memory consumption
        checkPluginPreferences(binding.mainPager)

        // Tabs
        if (sp.getBoolean(R.string.key_short_tabtitles, false)) {
            binding.tabsNormal.visibility = View.GONE
            binding.tabsCompact.visibility = View.VISIBLE
            TabLayoutMediator(binding.tabsCompact, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).nameShort
            }.attach()
        } else {
            binding.tabsNormal.visibility = View.VISIBLE
            binding.tabsCompact.visibility = View.GONE
            TabLayoutMediator(binding.tabsNormal, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).name
            }.attach()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun setDisabledMenuItemColorPluginPreferences() {
        if( pluginPreferencesMenuItem?.isEnabled == false){
            val spanString = SpannableString(this.menu?.findItem(R.id.nav_plugin_preferences)?.title.toString())
            spanString.setSpan(ForegroundColorSpan(rh.gac(R.attr.disabledTextColor)), 0, spanString.length, 0)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = spanString
        }
    }

    private fun setPluginPreferenceMenuName() {
        if (binding.mainPager.currentItem >= 0) {
            val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = rh.gs(R.string.nav_preferences_plugin, plugin.name)
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menuOpen = true
        if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.mainDrawerLayout.closeDrawers()
        }
        val result = super.onMenuOpened(featureId, menu)
        menu.findItem(R.id.nav_treatments)?.isEnabled = profileFunction.getProfile() != null
        return result
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        menuOpen = false;
        super.onPanelClosed(featureId, menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //show all selected plugins not selected for hamburger menu in option menu
        this.menu = menu
        var itemId = 0
        for (p in activePlugin.getPluginsList()) {
            if (p.hasFragment() && !p.isFragmentVisible() && p.isEnabled(p.pluginDescription.mainType) && !p.pluginDescription.neverVisible) {
                val menuItem = menu.add(Menu.NONE, itemId++, Menu.NONE, p.name )
                if(p.menuIcon != -1) {
                    menuItem.setIcon(p.menuIcon)
                } else
                {
                    menuItem.setIcon(R.drawable.ic_settings)
                }
                menuItem.setOnMenuItemClickListener {
                    val intent = Intent(this, SingleFragmentActivity::class.java)
                    intent.putExtra("plugin", activePlugin.getPluginsList().indexOf(p))
                    startActivity(intent)
                    true
                }
            }
        }
        menuInflater.inflate(R.menu.menu_main, menu)
        pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences)
        setPluginPreferenceMenuName()
        checkPluginPreferences(binding.mainPager)
        setDisabledMenuItemColorPluginPreferences()
        return true
    }

    /*
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.menu_main, menu)
        pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences)
        setPluginPreferenceMenuName()
        checkPluginPreferences(binding.mainPager)
        return true
    }
     */

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_preferences        -> {
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                    val i = Intent(this, PreferencesActivity::class.java)
                    i.putExtra("id", -1)
                    startActivity(i)
                })
                return true
            }

            R.id.nav_historybrowser     -> {
                startActivity(Intent(this, HistoryBrowseActivity::class.java))
                return true
            }

            R.id.nav_treatments         -> {
                startActivity(Intent(this, TreatmentsActivity::class.java))
                return true
            }

            R.id.nav_themeselector -> {
                startActivity(Intent(this, ScrollingActivity::class.java))
                return true
            }

            R.id.nav_setupwizard        -> {
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                    startActivity(Intent(this, SetupWizardActivity::class.java))
                })
                return true
            }

            R.id.nav_about              -> {
                var message = "Build: ${BuildConfig.BUILDVERSION}\n"
                message += "Flavor: ${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE}\n"
                message += "${rh.gs(R.string.configbuilder_nightscoutversion_label)} ${nsSettingsStatus.getVersion()}"
                if (buildHelper.isEngineeringMode()) message += "\n${rh.gs(R.string.engineering_mode_enabled)}"
                if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(R.string.fabric_upload_disabled)}"
                message += rh.gs(R.string.about_link_urls)
                val messageSpanned = SpannableString(message)
                Linkify.addLinks(messageSpanned, Linkify.WEB_URLS)
                AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle(rh.gs(R.string.app_name) + " " + BuildConfig.VERSION)
                    .setIcon(iconsProvider.getIcon())
                    .setMessage(messageSpanned)
                    .setPositiveButton(rh.gs(R.string.ok), null)
                    .setNeutralButton(rh.gs(R.string.cta_dont_kill_my_app_info)) { _, _ -> DokiActivity.start(context = this@MainActivity) }
                    .create().apply {
                        show()
                        findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                    }
                return true
            }

            R.id.nav_exit               -> {
                aapsLogger.debug(LTag.CORE, "Exiting")
                uel.log(Action.EXIT_AAPS, Sources.Aaps)
                rxBus.send(EventAppExit())
                finish()
                System.runFinalization()
                exitProcess(0)
            }

            R.id.nav_plugin_preferences -> {
                val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                    val i = Intent(this, PreferencesActivity::class.java)
                    i.putExtra("id", plugin.preferencesId)
                    startActivity(i)
                })
                return true
            }
/*
            R.id.nav_survey             -> {
                startActivity(Intent(this, SurveyActivity::class.java))
                return true
            }
*/
            R.id.nav_defaultprofile     -> {
                startActivity(Intent(this, ProfileHelperActivity::class.java))
                return true
            }

            R.id.nav_stats              -> {
                startActivity(Intent(this, StatsActivity::class.java))
                return true
            }
        }
        return actionBarDrawerToggle.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.mainDrawerLayout.closeDrawers()
            return
        }
        if (menuOpen) {
            this.menu?.close()
            return
        }
        if (binding.mainPager.currentItem != 0) {
            binding.mainPager.currentItem = 0
            return
        }
        super.onBackPressed()
    }

    // Correct place for calling setUserStats() would be probably MainApp
    // but we need to have it called at least once a day. Thus this location

    private fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        // Size is limited to 36 chars
        val remote = BuildConfig.REMOTE.lowercase(Locale.getDefault())
            .replace("https://", "")
            .replace("http://", "")
            .replace(".git", "")
            .replace(".com/", ":")
            .replace(".org/", ":")
            .replace(".net/", ":")
        fabricPrivacy.firebaseAnalytics.setUserProperty("Mode", BuildConfig.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.firebaseAnalytics.setUserProperty("Language", sp.getString(R.string.key_language, Locale.getDefault().language))
        fabricPrivacy.firebaseAnalytics.setUserProperty("Version", BuildConfig.VERSION)
        fabricPrivacy.firebaseAnalytics.setUserProperty("HEAD", BuildConfig.HEAD)
        fabricPrivacy.firebaseAnalytics.setUserProperty("Remote", remote)
        val hashes: List<String> = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.firebaseAnalytics.setUserProperty("Hash", hashes[0])
        activePlugin.activePump.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.NSCLIENT && !config.PUMPCONTROL)
            activePlugin.activeAPS.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.firebaseAnalytics.setUserProperty("BgSource", it::class.java.simpleName) }
        fabricPrivacy.firebaseAnalytics.setUserProperty("Profile", activePlugin.activeProfileSource.javaClass.simpleName)
        activePlugin.activeSensitivity.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Sensitivity", it::class.java.simpleName) }
        activePlugin.activeInsulin.let { fabricPrivacy.firebaseAnalytics.setUserProperty("Insulin", it::class.java.simpleName) }
        // Add to crash log too
        FirebaseCrashlytics.getInstance().setCustomKey("HEAD", BuildConfig.HEAD)
        FirebaseCrashlytics.getInstance().setCustomKey("Version", BuildConfig.VERSION)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildType", BuildConfig.BUILD_TYPE)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildFlavor", BuildConfig.FLAVOR)
        FirebaseCrashlytics.getInstance().setCustomKey("Remote", remote)
        FirebaseCrashlytics.getInstance().setCustomKey("Committed", BuildConfig.COMMITTED)
        FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", sp.getString(R.string.key_email_for_crash_report, ""))
    }

}
