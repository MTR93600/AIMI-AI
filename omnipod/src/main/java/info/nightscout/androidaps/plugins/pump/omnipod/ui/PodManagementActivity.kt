package info.nightscout.androidaps.plugins.pump.omnipod.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.FragmentStatePagerAdapter
import com.atech.android.library.wizardpager.WizardPagerActivity
import com.atech.android.library.wizardpager.WizardPagerContext
import com.atech.android.library.wizardpager.data.WizardPagerSettings
import com.atech.android.library.wizardpager.defs.WizardStepsWayType
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.R
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.defs.PodActionType
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.model.FullInitPodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.model.RemovePodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.model.ShortInitPodWizardModel
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.pages.InitPodRefreshAction
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnipod_pod_mgmt.*
import javax.inject.Inject

/**
 * Created by andy on 30/08/2019
 */
class PodManagementActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var podStateManager: PodStateManager
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var aapsOmnipodManager: AapsOmnipodManager

    private var disposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.omnipod_pod_mgmt)

        initpod_init_pod.setOnClickListener {
            initPodAction()
        }

        initpod_remove_pod.setOnClickListener {
            deactivatePodAction()
        }

        initpod_reset_pod.setOnClickListener {
            discardPodAction()
        }

        initpod_pod_history.setOnClickListener {
            showPodHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ refreshButtons() }, { fabricPrivacy.logException(it) })
        disposables += rxBus
            .toObservable(EventOmnipodPumpValuesChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ refreshButtons() }, { fabricPrivacy.logException(it) })

        refreshButtons()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    private fun initPodAction() {

        val pagerSettings = WizardPagerSettings()
        var refreshAction = InitPodRefreshAction(injector, PodActionType.INIT_POD)

        pagerSettings.setWizardStepsWayType(WizardStepsWayType.CancelNext)
        pagerSettings.setFinishStringResourceId(R.string.close)
        pagerSettings.setFinishButtonBackground(R.drawable.finish_background)
        pagerSettings.setNextButtonBackground(R.drawable.selectable_item_background)
        pagerSettings.setBackStringResourceId(R.string.cancel)
        pagerSettings.cancelAction = refreshAction
        pagerSettings.finishAction = refreshAction
        pagerSettings.pagerAdapterBehavior = FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT

        val wizardPagerContext = WizardPagerContext.getInstance()

        wizardPagerContext.clearContext()
        wizardPagerContext.pagerSettings = pagerSettings
        val isFullInit = !podStateManager.isPodInitialized || podStateManager.podProgressStatus.isBefore(PodProgressStatus.PRIMING_COMPLETED)
        if (isFullInit) {
            wizardPagerContext.wizardModel = FullInitPodWizardModel(applicationContext)
        } else {
            wizardPagerContext.wizardModel = ShortInitPodWizardModel(applicationContext)
        }

        val myIntent = Intent(this@PodManagementActivity, WizardPagerActivity::class.java)
        this@PodManagementActivity.startActivity(myIntent)
    }

    private fun deactivatePodAction() {
        val pagerSettings = WizardPagerSettings()
        var refreshAction = InitPodRefreshAction(injector, PodActionType.DEACTIVATE_POD)

        pagerSettings.setWizardStepsWayType(WizardStepsWayType.CancelNext)
        pagerSettings.setFinishStringResourceId(R.string.close)
        pagerSettings.setFinishButtonBackground(R.drawable.finish_background)
        pagerSettings.setNextButtonBackground(R.drawable.selectable_item_background)
        pagerSettings.setBackStringResourceId(R.string.cancel)
        pagerSettings.cancelAction = refreshAction
        pagerSettings.finishAction = refreshAction
        pagerSettings.pagerAdapterBehavior = FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT

        val wizardPagerContext = WizardPagerContext.getInstance();

        wizardPagerContext.clearContext()
        wizardPagerContext.pagerSettings = pagerSettings
        wizardPagerContext.wizardModel = RemovePodWizardModel(applicationContext)

        val myIntent = Intent(this@PodManagementActivity, WizardPagerActivity::class.java)
        this@PodManagementActivity.startActivity(myIntent)

    }

    private fun discardPodAction() {
        OKDialog.showConfirmation(this,
            resourceHelper.gs(R.string.omnipod_cmd_discard_pod_desc), Thread {
            aapsOmnipodManager.discardPodState()
            rxBus.send(EventOmnipodPumpValuesChanged())
        })
    }

    private fun showPodHistory() {
        startActivity(Intent(applicationContext, PodHistoryActivity::class.java))
    }

    private fun refreshButtons() {
        initpod_init_pod.isEnabled = !podStateManager.isPodActivationCompleted
        initpod_remove_pod.isEnabled = podStateManager.isPodInitialized
        initpod_reset_pod.isEnabled = podStateManager.hasPodState()

        val waitingForRlView = findViewById<LinearLayout>(R.id.initpod_waiting_for_rl_layout)

        if (rileyLinkServiceData.rileyLinkServiceState.isReady) {
            waitingForRlView.visibility = View.GONE
        } else {
            // if rileylink is not running we disable all operations that require a RL connection
            waitingForRlView.visibility = View.VISIBLE
            initpod_init_pod.isEnabled = false
            initpod_remove_pod.isEnabled = false
            initpod_reset_pod.isEnabled = false
        }
    }

}
