package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonOverviewButtonsBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.databinding.OmnipodCommonOverviewPodInfoBinding
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandHandleTimeChange
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandResumeDelivery
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandSilenceAlerts
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandSuspendDelivery
import info.nightscout.androidaps.plugins.pump.omnipod.eros.OmnipodErosPumpPlugin
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.databinding.OmnipodErosOverviewBinding
import info.nightscout.androidaps.plugins.pump.omnipod.eros.databinding.OmnipodErosOverviewRileyLinkStatusBinding
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.util.TimeUtil
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command.CommandGetPodStatus
import info.nightscout.androidaps.plugins.pump.omnipod.eros.util.AapsOmnipodUtil
import info.nightscout.androidaps.plugins.pump.omnipod.eros.util.OmnipodAlertUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.Duration
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

class OmnipodErosOverviewFragment : DaggerFragment() {
    companion object {

        private const val REFRESH_INTERVAL_MILLIS = 15 * 1000L // 15 seconds
        private const val PLACEHOLDER = "-"
    }

    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var omnipodErosPumpPlugin: OmnipodErosPumpPlugin
    @Inject lateinit var podStateManager: ErosPodStateManager
    @Inject lateinit var sp: SP
    @Inject lateinit var omnipodUtil: AapsOmnipodUtil
    @Inject lateinit var omnipodAlertUtil: OmnipodAlertUtil
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var omnipodManager: AapsOmnipodErosManager
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var disposables: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateUi() }
            handler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        }
    }

    var _binding: OmnipodErosOverviewBinding? = null
    var _rileyLinkStatusBinding: OmnipodErosOverviewRileyLinkStatusBinding? = null
    var _podInfoBinding: OmnipodCommonOverviewPodInfoBinding? = null
    var _buttonBinding: OmnipodCommonOverviewButtonsBinding? = null

    // These properties are only valid between onCreateView and
    // onDestroyView.
    val binding get() = _binding!!
    val rileyLinkStatusBinding get() = _rileyLinkStatusBinding!!
    val podInfoBinding get() = _podInfoBinding!!
    val buttonBinding get() = _buttonBinding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OmnipodErosOverviewBinding.inflate(inflater, container, false).also {
            _buttonBinding = OmnipodCommonOverviewButtonsBinding.bind(it.root)
            _podInfoBinding = OmnipodCommonOverviewPodInfoBinding.bind(it.root)
            _rileyLinkStatusBinding = OmnipodErosOverviewRileyLinkStatusBinding.bind(it.root)
            _binding = it
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonBinding.buttonPodManagement.setOnClickListener {
            if (omnipodErosPumpPlugin.rileyLinkService?.verifyConfiguration() == true) {
                activity?.let { activity ->
                    context?.let { context ->
                        protectionCheck.queryProtection(
                            activity, ProtectionCheck.Protection.PREFERENCES,
                            UIRunnable { startActivity(Intent(context, ErosPodManagementActivity::class.java)) }
                        )
                    }
                }
            } else {
                displayNotConfiguredDialog()
            }
        }

        buttonBinding.buttonResumeDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandResumeDelivery(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_resume_delivery), true).messageOnSuccess(rh.gs(R.string.omnipod_common_confirmation_delivery_resumed))
            )
        }

        buttonBinding.buttonRefreshStatus.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandGetPodStatus(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_refresh_status), false)
            )
        }

        buttonBinding.buttonSilenceAlerts.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSilenceAlerts(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_silence_alerts), false)
                    .messageOnSuccess(rh.gs(R.string.omnipod_common_confirmation_silenced_alerts))
                    .actionOnSuccess { rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_ALERTS)) })
        }

        buttonBinding.buttonSuspendDelivery.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandSuspendDelivery(),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_suspend_delivery), true)
                    .messageOnSuccess(rh.gs(R.string.omnipod_common_confirmation_suspended_delivery))
            )
        }

        buttonBinding.buttonSetTime.setOnClickListener {
            disablePodActionButtons()
            commandQueue.customCommand(
                CommandHandleTimeChange(true),
                DisplayResultDialogCallback(rh.gs(R.string.omnipod_common_error_failed_to_set_time), true)
                    .messageOnSuccess(rh.gs(R.string.omnipod_common_confirmation_time_on_pod_updated))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshLoop, REFRESH_INTERVAL_MILLIS)
        disposables += rxBus
            .toObservable(EventRileyLinkDeviceStatusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateRileyLinkStatus()
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventOmnipodErosPumpValuesChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateOmnipodStatus()
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateQueueStatus()
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        disposables += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updatePodActionButtons()
                       }, fabricPrivacy::logException)
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
        handler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi() {
        updateRileyLinkStatus()
        updateOmnipodStatus()
        updatePodActionButtons()
        updateQueueStatus()
    }

    @Synchronized
    private fun updateRileyLinkStatus() {
        val rileyLinkServiceState = rileyLinkServiceData.rileyLinkServiceState

        val resourceId = rileyLinkServiceState.resourceId
        val rileyLinkError = rileyLinkServiceData.rileyLinkError

        rileyLinkStatusBinding.rileyLinkStatus.text =
            when {
                rileyLinkServiceState == RileyLinkServiceState.NotStarted -> rh.gs(resourceId)
                rileyLinkServiceState.isConnecting                        -> "{fa-bluetooth-b spin}   " + rh.gs(resourceId)
                rileyLinkServiceState.isError && rileyLinkError == null   -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
                rileyLinkServiceState.isError && rileyLinkError != null   -> "{fa-bluetooth-b}   " + rh.gs(rileyLinkError.getResourceId(RileyLinkTargetDevice.Omnipod))
                else                                                      -> "{fa-bluetooth-b}   " + rh.gs(resourceId)
            }
        rileyLinkStatusBinding.rileyLinkStatus.setTextColor(if (rileyLinkServiceState.isError || rileyLinkError != null) Color.RED else Color.WHITE)
    }

    private fun updateOmnipodStatus() {
        updateLastConnection()
        updateLastBolus()
        updateTempBasal()
        updatePodStatus()

        val errors = ArrayList<String>()
        if (omnipodErosPumpPlugin.rileyLinkService != null) {
            val rileyLinkErrorDescription = omnipodErosPumpPlugin.rileyLinkService.errorDescription
            if (StringUtils.isNotEmpty(rileyLinkErrorDescription)) {
                errors.add(rileyLinkErrorDescription)
            }
        }

        if (!podStateManager.hasPodState() || !podStateManager.isPodInitialized) {
            podInfoBinding.uniqueId.text = if (podStateManager.hasPodState()) {
                podStateManager.address.toString()
            } else {
                PLACEHOLDER
            }
            podInfoBinding.podLot.text = PLACEHOLDER
            podInfoBinding.podSequenceNumber.text = PLACEHOLDER
            podInfoBinding.firmwareVersion.text = PLACEHOLDER
            podInfoBinding.timeOnPod.text = PLACEHOLDER
            podInfoBinding.podExpiryDate.text = PLACEHOLDER
            podInfoBinding.podExpiryDate.setTextColor(Color.WHITE)
            podInfoBinding.baseBasalRate.text = PLACEHOLDER
            podInfoBinding.totalDelivered.text = PLACEHOLDER
            podInfoBinding.reservoir.text = PLACEHOLDER
            podInfoBinding.reservoir.setTextColor(Color.WHITE)
            podInfoBinding.podActiveAlerts.text = PLACEHOLDER
        } else {
            podInfoBinding.uniqueId.text = podStateManager.address.toString()
            podInfoBinding.podLot.text = podStateManager.lot.toString()
            podInfoBinding.podSequenceNumber.text = podStateManager.tid.toString()
            podInfoBinding.firmwareVersion.text = rh.gs(R.string.omnipod_eros_overview_firmware_version_value, podStateManager.pmVersion.toString(), podStateManager.piVersion.toString())

            podInfoBinding.timeOnPod.text = readableZonedTime(podStateManager.time)
            podInfoBinding.timeOnPod.setTextColor(
                if (podStateManager.timeDeviatesMoreThan(OmnipodConstants.TIME_DEVIATION_THRESHOLD)) {
                    Color.RED
                } else {
                    Color.WHITE
                }
            )
            val expiresAt = podStateManager.expiresAt
            if (expiresAt == null) {
                podInfoBinding.podExpiryDate.text = PLACEHOLDER
                podInfoBinding.podExpiryDate.setTextColor(Color.WHITE)
            } else {
                podInfoBinding.podExpiryDate.text = readableZonedTime(expiresAt)
                podInfoBinding.podExpiryDate.setTextColor(
                    if (DateTime.now().isAfter(expiresAt)) {
                        Color.RED
                    } else {
                        Color.WHITE
                    }
                )
            }

            if (podStateManager.isPodFaulted) {
                val faultEventCode = podStateManager.faultEventCode
                errors.add(rh.gs(R.string.omnipod_common_pod_status_pod_fault_description, faultEventCode.value, faultEventCode.name))
            }

            // base basal rate
            podInfoBinding.baseBasalRate.text = if (podStateManager.isPodActivationCompleted) {
                rh.gs(R.string.pump_basebasalrate, omnipodErosPumpPlugin.model().determineCorrectBasalSize(podStateManager.basalSchedule.rateAt(TimeUtil.toDuration(DateTime.now()))))
            } else {
                PLACEHOLDER
            }

            // total delivered
            podInfoBinding.totalDelivered.text = if (podStateManager.isPodActivationCompleted && podStateManager.totalInsulinDelivered != null) {
                rh.gs(R.string.omnipod_common_overview_total_delivered_value, podStateManager.totalInsulinDelivered - OmnipodConstants.POD_SETUP_UNITS)
            } else {
                PLACEHOLDER
            }

            // reservoir
            if (podStateManager.reservoirLevel == null) {
                podInfoBinding.reservoir.text = rh.gs(R.string.omnipod_common_overview_reservoir_value_over50)
                podInfoBinding.reservoir.setTextColor(Color.WHITE)
            } else {
                val lowReservoirThreshold = (omnipodAlertUtil.lowReservoirAlertUnits
                    ?: OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD).toDouble()

                podInfoBinding.reservoir.text = rh.gs(R.string.omnipod_common_overview_reservoir_value, podStateManager.reservoirLevel)
                podInfoBinding.reservoir.setTextColor(
                    if (podStateManager.reservoirLevel < lowReservoirThreshold) {
                        Color.RED
                    } else {
                        Color.WHITE
                    }
                )
            }

            podInfoBinding.podActiveAlerts.text = if (podStateManager.hasActiveAlerts()) {
                TextUtils.join(System.lineSeparator(), omnipodUtil.getTranslatedActiveAlerts(podStateManager))
            } else {
                PLACEHOLDER
            }
        }

        if (errors.size == 0) {
            podInfoBinding.errors.text = PLACEHOLDER
            podInfoBinding.errors.setTextColor(Color.WHITE)
        } else {
            podInfoBinding.errors.text = StringUtils.join(errors, System.lineSeparator())
            podInfoBinding.errors.setTextColor(Color.RED)
        }
    }

    private fun updateLastConnection() {
        if (podStateManager.isPodInitialized && podStateManager.lastSuccessfulCommunication != null) {
            podInfoBinding.lastConnection.text = readableDuration(podStateManager.lastSuccessfulCommunication)
            val lastConnectionColor =
                if (omnipodErosPumpPlugin.isUnreachableAlertTimeoutExceeded(getPumpUnreachableTimeout().millis)) {
                    Color.RED
                } else {
                    Color.WHITE
                }
            podInfoBinding.lastConnection.setTextColor(lastConnectionColor)
        } else {
            podInfoBinding.lastConnection.setTextColor(Color.WHITE)
            podInfoBinding.lastConnection.text = if (podStateManager.hasPodState() && podStateManager.lastSuccessfulCommunication != null) {
                readableDuration(podStateManager.lastSuccessfulCommunication)
            } else {
                PLACEHOLDER
            }
        }
    }

    private fun updatePodStatus() {
        podInfoBinding.podStatus.text = if (!podStateManager.hasPodState()) {
            rh.gs(R.string.omnipod_common_pod_status_no_active_pod)
        } else if (!podStateManager.isPodActivationCompleted) {
            if (!podStateManager.isPodInitialized) {
                rh.gs(R.string.omnipod_common_pod_status_waiting_for_activation)
            } else {
                if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIMING_COMPLETED)) {
                    rh.gs(R.string.omnipod_common_pod_status_waiting_for_activation)
                } else {
                    rh.gs(R.string.omnipod_common_pod_status_waiting_for_cannula_insertion)
                }
            }
        } else {
            if (podStateManager.podProgressStatus.isRunning) {
                var status = if (podStateManager.isSuspended) {
                    rh.gs(R.string.omnipod_common_pod_status_suspended)
                } else {
                    rh.gs(R.string.omnipod_common_pod_status_running)
                }

                if (!podStateManager.isBasalCertain) {
                    status += " (" + rh.gs(R.string.omnipod_eros_uncertain) + ")"
                }

                status
            } else if (podStateManager.podProgressStatus == PodProgressStatus.FAULT_EVENT_OCCURRED) {
                rh.gs(R.string.omnipod_common_pod_status_pod_fault)
            } else if (podStateManager.podProgressStatus == PodProgressStatus.INACTIVE) {
                rh.gs(R.string.omnipod_common_pod_status_inactive)
            } else {
                podStateManager.podProgressStatus.toString()
            }
        }

        val podStatusColor =
            if (!podStateManager.isPodActivationCompleted || podStateManager.isPodDead || podStateManager.isSuspended || (podStateManager.isPodRunning && !podStateManager.isBasalCertain)) {
                Color.RED
            } else {
                Color.WHITE
            }
        podInfoBinding.podStatus.setTextColor(podStatusColor)
    }

    private fun updateLastBolus() {
        if (podStateManager.isPodActivationCompleted && podStateManager.hasLastBolus()) {
            var text =
                rh.gs(
                    R.string.omnipod_common_overview_last_bolus_value,
                    omnipodErosPumpPlugin.model().determineCorrectBolusSize(podStateManager.lastBolusAmount),
                    rh.gs(R.string.insulin_unit_shortname),
                    readableDuration(podStateManager.lastBolusStartTime)
                )
            val textColor: Int

            if (podStateManager.isLastBolusCertain) {
                textColor = Color.WHITE
            } else {
                textColor = Color.RED
                text += " (" + rh.gs(R.string.omnipod_eros_uncertain) + ")"
            }

            podInfoBinding.lastBolus.text = text
            podInfoBinding.lastBolus.setTextColor(textColor)

        } else {
            podInfoBinding.lastBolus.text = PLACEHOLDER
            podInfoBinding.lastBolus.setTextColor(Color.WHITE)
        }
    }

    private fun updateTempBasal() {
        if (podStateManager.isPodActivationCompleted && podStateManager.isTempBasalRunning) {
            if (!podStateManager.hasTempBasal()) {
                podInfoBinding.tempBasal.text = "???"
                podInfoBinding.tempBasal.setTextColor(Color.RED)
            } else {
                val now = DateTime.now()

                val startTime = podStateManager.tempBasalStartTime
                val amount = podStateManager.tempBasalAmount
                val duration = podStateManager.tempBasalDuration

                val minutesRunning = Duration(startTime, now).standardMinutes

                var text: String
                val textColor: Int
                text = rh.gs(R.string.omnipod_common_overview_temp_basal_value, amount, dateUtil.timeString(startTime.millis), minutesRunning, duration.standardMinutes)
                if (podStateManager.isTempBasalCertain) {
                    textColor = Color.WHITE
                } else {
                    textColor = Color.RED
                    text += " (" + rh.gs(R.string.omnipod_eros_uncertain) + ")"
                }

                podInfoBinding.tempBasal.text = text
                podInfoBinding.tempBasal.setTextColor(textColor)
            }
        } else {
            var text = PLACEHOLDER
            val textColor: Int

            if (!podStateManager.isPodActivationCompleted || podStateManager.isTempBasalCertain) {
                textColor = Color.WHITE
            } else {
                textColor = Color.RED
                text += " (" + rh.gs(R.string.omnipod_eros_uncertain) + ")"
            }

            podInfoBinding.tempBasal.text = text
            podInfoBinding.tempBasal.setTextColor(textColor)
        }
    }

    private fun updateQueueStatus() {
        if (isQueueEmpty()) {
            podInfoBinding.queue.visibility = View.GONE
        } else {
            podInfoBinding.queue.visibility = View.VISIBLE
            podInfoBinding.queue.text = commandQueue.spannedStatus().toString()
        }
    }

    private fun updatePodActionButtons() {
        updateRefreshStatusButton()
        updateResumeDeliveryButton()
        updateSilenceAlertsButton()
        updateSuspendDeliveryButton()
        updateSetTimeButton()
    }

    private fun disablePodActionButtons() {
        buttonBinding.buttonSilenceAlerts.isEnabled = false
        buttonBinding.buttonResumeDelivery.isEnabled = false
        buttonBinding.buttonSuspendDelivery.isEnabled = false
        buttonBinding.buttonSetTime.isEnabled = false
        buttonBinding.buttonRefreshStatus.isEnabled = false
    }

    private fun updateRefreshStatusButton() {
        buttonBinding.buttonRefreshStatus.isEnabled = podStateManager.isPodInitialized && podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)
            && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
    }

    private fun updateResumeDeliveryButton() {
        if (podStateManager.isPodRunning && (podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandResumeDelivery::class.java))) {
            buttonBinding.buttonResumeDelivery.visibility = View.VISIBLE
            buttonBinding.buttonResumeDelivery.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            buttonBinding.buttonResumeDelivery.visibility = View.GONE
        }
    }

    private fun updateSilenceAlertsButton() {
        if (!omnipodManager.isAutomaticallyAcknowledgeAlertsEnabled && podStateManager.isPodRunning && (podStateManager.hasActiveAlerts() || commandQueue.isCustomCommandInQueue(
                CommandSilenceAlerts::class.java
            ))
        ) {
            buttonBinding.buttonSilenceAlerts.visibility = View.VISIBLE
            buttonBinding.buttonSilenceAlerts.isEnabled = rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            buttonBinding.buttonSilenceAlerts.visibility = View.GONE
        }
    }

    private fun updateSuspendDeliveryButton() {
        // If the Pod is currently suspended, we show the Resume delivery button instead.
        if (omnipodManager.isSuspendDeliveryButtonEnabled && podStateManager.isPodRunning && (!podStateManager.isSuspended || commandQueue.isCustomCommandInQueue(CommandSuspendDelivery::class.java))) {
            buttonBinding.buttonSuspendDelivery.visibility = View.VISIBLE
            buttonBinding.buttonSuspendDelivery.isEnabled = podStateManager.isPodRunning && !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            buttonBinding.buttonSuspendDelivery.visibility = View.GONE
        }
    }

    private fun updateSetTimeButton() {
        if (podStateManager.isPodRunning && (podStateManager.timeDeviatesMoreThan(Duration.standardMinutes(5)) || commandQueue.isCustomCommandInQueue(CommandHandleTimeChange::class.java))) {
            buttonBinding.buttonSetTime.visibility = View.VISIBLE
            buttonBinding.buttonSetTime.isEnabled = !podStateManager.isSuspended && rileyLinkServiceData.rileyLinkServiceState.isReady && isQueueEmpty()
        } else {
            buttonBinding.buttonSetTime.visibility = View.GONE
        }
    }

    private fun displayNotConfiguredDialog() {
        context?.let {
            UIRunnable {
                OKDialog.show(
                    it, rh.gs(R.string.omnipod_common_warning),
                    rh.gs(R.string.omnipod_eros_error_operation_not_possible_no_configuration), null
                )
            }.run()
        }
    }

    private fun displayErrorDialog(title: String, message: String, withSound: Boolean) {
        context?.let {
            ErrorHelperActivity.runAlarm(it, message, title, if (withSound) R.raw.boluserror else 0)
        }
    }

    private fun displayOkDialog(title: String, message: String) {
        context?.let {
            UIRunnable {
                OKDialog.show(it, title, message, null)
            }.run()
        }
    }

    private fun readableZonedTime(time: DateTime): String {
        val timeAsJavaData = time.toLocalDateTime().toDate()
        val timeZone = podStateManager.timeZone.toTimeZone()
        if (timeZone == TimeZone.getDefault()) {
            return dateUtil.dateAndTimeString(timeAsJavaData.time)
        }

        val isDaylightTime = timeZone.inDaylightTime(timeAsJavaData)
        val locale = resources.configuration.locales.get(0)
        val timeZoneDisplayName = timeZone.getDisplayName(isDaylightTime, TimeZone.SHORT, locale) + " " + timeZone.getDisplayName(isDaylightTime, TimeZone.LONG, locale)
        return rh.gs(R.string.omnipod_common_time_with_timezone, dateUtil.dateAndTimeString(timeAsJavaData.time), timeZoneDisplayName)
    }

    private fun readableDuration(dateTime: DateTime): String {
        val duration = Duration(dateTime, DateTime.now())
        val hours = duration.standardHours.toInt()
        val minutes = duration.standardMinutes.toInt()
        val seconds = duration.standardSeconds.toInt()
        when {
            seconds < 10           -> {
                return rh.gs(R.string.omnipod_common_moments_ago)
            }

            seconds < 60           -> {
                return rh.gs(R.string.omnipod_common_less_than_a_minute_ago)
            }

            seconds < 60 * 60      -> { // < 1 hour
                return rh.gs(R.string.omnipod_common_time_ago, rh.gq(R.plurals.omnipod_common_minutes, minutes, minutes))
            }

            seconds < 24 * 60 * 60 -> { // < 1 day
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    return rh.gs(
                        R.string.omnipod_common_time_ago,
                        rh.gs(R.string.omnipod_common_composite_time, rh.gq(R.plurals.omnipod_common_hours, hours, hours), rh.gq(R.plurals.omnipod_common_minutes, minutesLeft, minutesLeft))
                    )
                return rh.gs(R.string.omnipod_common_time_ago, rh.gq(R.plurals.omnipod_common_hours, hours, hours))
            }

            else                   -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    return rh.gs(
                        R.string.omnipod_common_time_ago,
                        rh.gs(R.string.omnipod_common_composite_time, rh.gq(R.plurals.omnipod_common_days, days, days), rh.gq(R.plurals.omnipod_common_hours, hoursLeft, hoursLeft))
                    )
                return rh.gs(R.string.omnipod_common_time_ago, rh.gq(R.plurals.omnipod_common_days, days, days))
            }
        }
    }

    private fun isQueueEmpty(): Boolean {
        return commandQueue.size() == 0 && commandQueue.performing() == null
    }

    // FIXME ideally we should just have access to LocalAlertUtils here
    private fun getPumpUnreachableTimeout(): Duration {
        return Duration.standardMinutes(sp.getInt(R.string.key_pump_unreachable_threshold_minutes, Constants.DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES).toLong())
    }

    inner class DisplayResultDialogCallback(private val errorMessagePrefix: String, private val withSoundOnError: Boolean) : Callback() {

        private var messageOnSuccess: String? = null
        private var actionOnSuccess: Runnable? = null

        override fun run() {
            if (result.success) {
                val messageOnSuccess = this.messageOnSuccess
                if (messageOnSuccess != null) {
                    displayOkDialog(rh.gs(R.string.omnipod_common_confirmation), messageOnSuccess)
                }
                actionOnSuccess?.run()
            } else {
                displayErrorDialog(rh.gs(R.string.omnipod_common_warning), rh.gs(R.string.omnipod_common_two_strings_concatenated_by_colon, errorMessagePrefix, result.comment), withSoundOnError)
            }
        }

        fun messageOnSuccess(message: String): DisplayResultDialogCallback {
            messageOnSuccess = message
            return this
        }

        fun actionOnSuccess(action: Runnable): DisplayResultDialogCallback {
            actionOnSuccess = action
            return this
        }
    }

}
