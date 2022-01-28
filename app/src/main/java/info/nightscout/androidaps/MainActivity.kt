package info.nightscout.androidaps

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.PersistableBundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import dev.doubledot.doki.ui.DokiActivity
import info.nightscout.androidaps.activities.*
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.ActivityMainBinding
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.setupwizard.SetupWizardActivity
import info.nightscout.androidaps.utils.AndroidPermission
import info.nightscout.androidaps.utils.FabricPrivacy
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
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.*
import javax.inject.Inject
import kotlin.system.exitProcess

class MainActivity : NoSplashAppCompatActivity() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
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
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var profileFunction: ProfileFunction

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Iconify.with(FontAwesomeModule())
        LocaleHelper.update(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        // initialize screen wake lock
        processPreferenceChange(EventPreferenceChange(rh.gs(R.string.key_keep_screen_on)))
        binding.mainPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                setPluginPreferenceMenuName()
                checkPluginPreferences(binding.mainPager)
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
        protectionCheck.queryProtection(this, ProtectionCheck.Protection.APPLICATION, null,
                                        UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed)) { finish() } },
                                        UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed)) { finish() } }
        )
    }

    private fun setWakeLock() {
        val keepScreenOn = sp.getBoolean(R.string.key_keep_screen_on, false)
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        for (p in activePlugin.getPluginsList()) {
            pageAdapter.registerNewFragment(p)
            if (p.isEnabled() && p.hasFragment() && !p.isFragmentVisible() && !p.pluginDescription.neverVisible) {
                val menuItem = menu.add(p.name)
                menuItem.isCheckable = true
                if (p.menuIcon != -1) {
                    menuItem.setIcon(p.menuIcon)
                } else {
                    menuItem.setIcon(R.drawable.ic_settings)
                }
                menuItem.setOnMenuItemClickListener {
                    val intent = Intent(this, SingleFragmentActivity::class.java)
                    intent.putExtra("plugin", activePlugin.getPluginsList().indexOf(p))
                    startActivity(intent)
                    binding.mainDrawerLayout.closeDrawers()
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
            binding.toolbar.layoutParams = LinearLayout.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT, resources.getDimension(R.dimen.compact_height).toInt())
            TabLayoutMediator(binding.tabsCompact, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).nameShort
            }.attach()
        } else {
            binding.tabsNormal.visibility = View.VISIBLE
            binding.tabsCompact.visibility = View.GONE
            val typedValue = TypedValue()
            if (theme.resolveAttribute(R.attr.actionBarSize, typedValue, true)) {
                binding.toolbar.layoutParams = LinearLayout.LayoutParams(
                    Toolbar.LayoutParams.MATCH_PARENT,
                    TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
                )
            }
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

    private fun setPluginPreferenceMenuName() {
        if (binding.mainPager.currentItem >= 0) {
            val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = rh.gs(R.string.nav_preferences_plugin, plugin.name)
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        val result = super.onMenuOpened(featureId, menu)
        menu.findItem(R.id.nav_treatments)?.isEnabled = profileFunction.getProfile() != null
        return result
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        menuInflater.inflate(R.menu.menu_main, menu)
        pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences)
        setPluginPreferenceMenuName()
        checkPluginPreferences(binding.mainPager)
        return true
    }

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
                AlertDialog.Builder(this)
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
        FirebaseCrashlytics.getInstance().setCustomKey("Remote", remote)
        FirebaseCrashlytics.getInstance().setCustomKey("Committed", BuildConfig.COMMITTED)
        FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", sp.getString(R.string.key_email_for_crash_report, ""))
    }

}
