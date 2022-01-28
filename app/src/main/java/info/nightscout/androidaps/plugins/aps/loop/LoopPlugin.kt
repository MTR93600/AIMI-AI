package info.nightscout.androidaps.plugins.aps.loop

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.*
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.OfflineEvent
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.InsertAndCancelCurrentOfflineEventTransaction
import info.nightscout.androidaps.database.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.androidaps.events.EventAcceptOpenLoopChange
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventTempTargetChange
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.interfaces.Loop.LastRun
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.configBuilder.RunningConfiguration
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.wear.events.EventWearConfirmAction
import info.nightscout.androidaps.plugins.general.wear.events.EventWearInitiateAction
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.extensions.buildDeviceStatus
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.convertedToPercent
import info.nightscout.androidaps.extensions.plannedRemainingMinutes
import info.nightscout.androidaps.plugins.aps.events.EventLoopInvoked
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@OpenForTesting
@Singleton
class LoopPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val sp: SP,
    config: Config,
    private val constraintChecker: ConstraintChecker,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val commandQueue: CommandQueue,
    private val activePlugin: ActivePlugin,
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val receiverStatusStore: ReceiverStatusStore,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger,
    private val repository: AppRepository,
    private val runningConfiguration: RunningConfiguration
) : PluginBase(PluginDescription()
    .mainType(PluginType.LOOP)
    .fragmentClass(LoopFragment::class.java.name)
    .pluginIcon(R.drawable.ic_loop_closed_white)
    .pluginName(R.string.loop)
    .shortName(R.string.loop_shortname)
    .preferencesId(R.xml.pref_loop)
    .enableByDefault(config.APS)
    .description(R.string.description_loop),
    aapsLogger, rh, injector
), Loop {

    private val disposable = CompositeDisposable()
    private var lastBgTriggeredRun: Long = 0
    private var carbsSuggestionsSuspendedUntil: Long = 0
    private var prevCarbsreq = 0
    override var lastRun: LastRun? = null

    override fun onStart() {
        createNotificationChannel()
        super.onStart()
        disposable.add(rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ invoke("EventTempTargetChange", true) }, fabricPrivacy::logException)
        )
        /*
          This method is triggered once autosens calculation has completed, so the LoopPlugin
          has current data to work with. However, autosens calculation can be triggered by multiple
          sources and currently only a new BG should trigger a loop run. Hence we return early if
          the event causing the calculation is not EventNewBg.
          <p>
         */
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventAutosensCalculationFinished ->
                // Autosens calculation not triggered by a new BG
                if (event.cause !is EventNewBG) return@subscribe
                val glucoseValue = iobCobCalculator.ads.actualBg() ?: return@subscribe
                // BG outdated
                // already looped with that value
                if (glucoseValue.timestamp <= lastBgTriggeredRun) return@subscribe
                lastBgTriggeredRun = glucoseValue.timestamp
                invoke("AutosenseCalculation for $glucoseValue", true)
            }, fabricPrivacy::logException)
        )
    }

    private fun createNotificationChannel() {
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        @SuppressLint("WrongConstant") val channel = NotificationChannel(CHANNEL_ID,
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH)
        mNotificationManager.createNotificationChannel(channel)
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun specialEnableCondition(): Boolean {
        return try {
            val pump = activePlugin.activePump
            pump.pumpDescription.isTempBasalCapable
        } catch (ignored: Exception) {
            // may fail during initialization
            true
        }
    }

    override fun minutesToEndOfSuspend(): Int {
        val offlineEventWrapped = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet()
        return if (offlineEventWrapped is ValueWrapper.Existing) T.msecs(offlineEventWrapped.value.timestamp + offlineEventWrapped.value.duration - dateUtil.now()).mins().toInt()
        else 0
    }

    override val isSuspended: Boolean
        get() = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing

    override var enabled: Boolean
        get() = isEnabled()
        set(value) {
            setPluginEnabled(PluginType.LOOP, value)
        }

    override val isLGS: Boolean
        get() {
            val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
            val maxIobAllowed = constraintChecker.getMaxIOBAllowed().value()
            val apsMode = sp.getString(R.string.key_aps_mode, "open")
            val pump = activePlugin.activePump
            var isLGS = false
            if (!isSuspended && !pump.isSuspended()) if (closedLoopEnabled.value()) if (maxIobAllowed == HardLimits.MAX_IOB_LGS || apsMode == "lgs") isLGS = true
            return isLGS
        }

    override val isSuperBolus: Boolean
        get() {
            val offlineEventWrapped = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet()
            return offlineEventWrapped is ValueWrapper.Existing && offlineEventWrapped.value.reason == OfflineEvent.Reason.SUPER_BOLUS
        }

    override val isDisconnected: Boolean
        get() {
            val offlineEventWrapped = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet()
            return offlineEventWrapped is ValueWrapper.Existing && offlineEventWrapped.value.reason == OfflineEvent.Reason.DISCONNECT_PUMP
        }

    @Suppress("SameParameterValue")
    private fun treatmentTimeThreshold(durationMinutes: Int): Boolean {
        val threshold = System.currentTimeMillis() + durationMinutes * 60 * 1000
        var bool = false
        val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        val lastCarbsTime = repository.getLastCarbsRecord()?.timestamp ?: 0L
        if (lastBolusTime > threshold || lastCarbsTime > threshold) bool = true
        return bool
    }

    @Synchronized
    fun isEmptyQueue(): Boolean {
        val maxMinutes = 2L
        val start = dateUtil.now()
        while (start + T.mins(maxMinutes).msecs() > dateUtil.now()) {
            if (commandQueue.size() == 0 && commandQueue.performing() == null) return true
            SystemClock.sleep(100)
        }
        return false
    }

    @Synchronized
    override fun invoke(initiator: String, allowNotification: Boolean, tempBasalFallback: Boolean) {
        try {
            aapsLogger.debug(LTag.APS, "invoke from $initiator")
            val loopEnabled = constraintChecker.isLoopInvocationAllowed()
            if (!loopEnabled.value()) {
                val message = """
                    ${rh.gs(R.string.loopdisabled)}
                    ${loopEnabled.getReasons(aapsLogger)}
                    """.trimIndent()
                aapsLogger.debug(LTag.APS, message)
                rxBus.send(EventLoopSetLastRunGui(message))
                return
            }
            val pump = activePlugin.activePump
            var apsResult: APSResult? = null
            if (!isEnabled(PluginType.LOOP)) return
            val profile = profileFunction.getProfile()
            if (profile == null || !profileFunction.isProfileValid("Loop")) {
                aapsLogger.debug(LTag.APS, rh.gs(R.string.noprofileset))
                rxBus.send(EventLoopSetLastRunGui(rh.gs(R.string.noprofileset)))
                return
            }

            // Check if pump info is loaded
            if (pump.baseBasalRate < 0.01) return
            val usedAPS = activePlugin.activeAPS
            if ((usedAPS as PluginBase).isEnabled()) {
                usedAPS.invoke(initiator, tempBasalFallback)
                apsResult = usedAPS.lastAPSResult
            }

            // Check if we have any result
            if (apsResult == null) {
                rxBus.send(EventLoopSetLastRunGui(rh.gs(R.string.noapsselected)))
                return
            } else rxBus.send(EventLoopInvoked())

            if (!isEmptyQueue()) {
                aapsLogger.debug(LTag.APS, rh.gs(R.string.pumpbusy))
                rxBus.send(EventLoopSetLastRunGui(rh.gs(R.string.pumpbusy)))
                return
            }

            // Prepare for pumps using % basals
            if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT && allowPercentage()) {
                apsResult.usePercent = true
            }
            apsResult.percent = (apsResult.rate / profile.getBasal() * 100).toInt()

            // check rate for constraints
            val resultAfterConstraints = apsResult.newAndClone(injector)
            resultAfterConstraints.rateConstraint = Constraint(resultAfterConstraints.rate)
            resultAfterConstraints.rate = constraintChecker.applyBasalConstraints(resultAfterConstraints.rateConstraint!!, profile).value()
            resultAfterConstraints.percentConstraint = Constraint(resultAfterConstraints.percent)
            resultAfterConstraints.percent = constraintChecker.applyBasalPercentConstraints(resultAfterConstraints.percentConstraint!!, profile).value()
            resultAfterConstraints.smbConstraint = Constraint(resultAfterConstraints.smb)
            resultAfterConstraints.smb = constraintChecker.applyBolusConstraints(resultAfterConstraints.smbConstraint!!).value()

            // safety check for multiple SMBs
            val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
            if (lastBolusTime != 0L && lastBolusTime + T.mins(3).msecs() > System.currentTimeMillis()) {
                aapsLogger.debug(LTag.APS, "SMB requested but still in 3 min interval")
                resultAfterConstraints.smb = 0.0
            }
            prevCarbsreq = lastRun?.constraintsProcessed?.carbsReq ?: prevCarbsreq
            lastRun = (lastRun ?: LastRun()).also { lastRun ->
                lastRun.request = apsResult
                lastRun.constraintsProcessed = resultAfterConstraints
                lastRun.lastAPSRun = dateUtil.now()
                lastRun.source = (usedAPS as PluginBase).name
                lastRun.tbrSetByPump = null
                lastRun.smbSetByPump = null
                lastRun.lastTBREnact = 0
                lastRun.lastTBRRequest = 0
                lastRun.lastSMBEnact = 0
                lastRun.lastSMBRequest = 0
                buildDeviceStatus(dateUtil, this, iobCobCalculator, profileFunction,
                    activePlugin.activePump, receiverStatusStore, runningConfiguration,
                    BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION)?.also {
                    repository.insert(it)
                }

                if (isSuspended) {
                    aapsLogger.debug(LTag.APS, rh.gs(R.string.loopsuspended))
                    rxBus.send(EventLoopSetLastRunGui(rh.gs(R.string.loopsuspended)))
                    return
                }
                if (pump.isSuspended()) {
                    aapsLogger.debug(LTag.APS, rh.gs(R.string.pumpsuspended))
                    rxBus.send(EventLoopSetLastRunGui(rh.gs(R.string.pumpsuspended)))
                    return
                }
                val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
                if (closedLoopEnabled.value()) {
                    if (allowNotification) {
                        if (resultAfterConstraints.isCarbsRequired
                            && resultAfterConstraints.carbsReq >= sp.getInt(R.string.key_smb_enable_carbs_suggestions_threshold, 0) && carbsSuggestionsSuspendedUntil < System.currentTimeMillis() && !treatmentTimeThreshold(-15)) {
                            if (sp.getBoolean(R.string.key_enable_carbs_required_alert_local, true) && !sp.getBoolean(R.string.key_raise_notifications_as_android_notifications, true)) {
                                val carbReqLocal = Notification(Notification.CARBS_REQUIRED, resultAfterConstraints.carbsRequiredText, Notification.NORMAL)
                                rxBus.send(EventNewNotification(carbReqLocal))
                            }
                            if (sp.getBoolean(R.string.key_ns_create_announcements_from_carbs_req, false)) {
                                disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(resultAfterConstraints.carbsRequiredText)).subscribe()
                            }
                            if (sp.getBoolean(R.string.key_enable_carbs_required_alert_local, true) && sp.getBoolean(R.string.key_raise_notifications_as_android_notifications, true)) {
                                val intentAction5m = Intent(context, CarbSuggestionReceiver::class.java)
                                intentAction5m.putExtra("ignoreDuration", 5)
                                val pendingIntent5m = PendingIntent.getBroadcast(context, 1, intentAction5m, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                                val actionIgnore5m = NotificationCompat.Action(R.drawable.ic_notif_aaps, rh.gs(R.string.ignore5m, "Ignore 5m"), pendingIntent5m)
                                val intentAction15m = Intent(context, CarbSuggestionReceiver::class.java)
                                intentAction15m.putExtra("ignoreDuration", 15)
                                val pendingIntent15m = PendingIntent.getBroadcast(context, 1, intentAction15m, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                                val actionIgnore15m = NotificationCompat.Action(R.drawable.ic_notif_aaps, rh.gs(R.string.ignore15m, "Ignore 15m"), pendingIntent15m)
                                val intentAction30m = Intent(context, CarbSuggestionReceiver::class.java)
                                intentAction30m.putExtra("ignoreDuration", 30)
                                val pendingIntent30m = PendingIntent.getBroadcast(context, 1, intentAction30m, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                                val actionIgnore30m = NotificationCompat.Action(R.drawable.ic_notif_aaps, rh.gs(R.string.ignore30m, "Ignore 30m"), pendingIntent30m)
                                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                                builder.setSmallIcon(R.drawable.notif_icon)
                                    .setContentTitle(rh.gs(R.string.carbssuggestion))
                                    .setContentText(resultAfterConstraints.carbsRequiredText)
                                    .setAutoCancel(true)
                                    .setPriority(Notification.IMPORTANCE_HIGH)
                                    .setCategory(Notification.CATEGORY_ALARM)
                                    .addAction(actionIgnore5m)
                                    .addAction(actionIgnore15m)
                                    .addAction(actionIgnore30m)
                                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                    .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
                                val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                                // mId allows you to update the notification later on.
                                mNotificationManager.notify(Constants.notificationID, builder.build())
                                uel.log(Action.CAREPORTAL, Sources.Loop, rh.gs(R.string.carbsreq, resultAfterConstraints.carbsReq, resultAfterConstraints.carbsReqWithin),
                                        ValueWithUnit.Gram(resultAfterConstraints.carbsReq),
                                        ValueWithUnit.Minute(resultAfterConstraints.carbsReqWithin))
                                rxBus.send(EventNewOpenLoopNotification())

                                //only send to wear if Native notifications are turned off
                                if (!sp.getBoolean(R.string.key_raise_notifications_as_android_notifications, true)) {
                                    // Send to Wear
                                    rxBus.send(EventWearInitiateAction("changeRequest"))
                                }
                            }
                        } else {
                            //If carbs were required previously, but are no longer needed, dismiss notifications
                            if (prevCarbsreq > 0) {
                                dismissSuggestion()
                                rxBus.send(EventDismissNotification(Notification.CARBS_REQUIRED))
                            }
                        }
                    }
                    if (resultAfterConstraints.isChangeRequested
                        && !commandQueue.bolusInQueue()) {
                        val waiting = PumpEnactResult(injector)
                        waiting.queued = true
                        if (resultAfterConstraints.tempBasalRequested) lastRun.tbrSetByPump = waiting
                        if (resultAfterConstraints.bolusRequested()) lastRun.smbSetByPump = waiting
                        rxBus.send(EventLoopUpdateGui())
                        fabricPrivacy.logCustom("APSRequest")
                        applyTBRRequest(resultAfterConstraints, profile, object : Callback() {
                            override fun run() {
                                if (result.enacted || result.success) {
                                    lastRun.tbrSetByPump = result
                                    lastRun.lastTBRRequest = lastRun.lastAPSRun
                                    lastRun.lastTBREnact = dateUtil.now()
                                    rxBus.send(EventLoopUpdateGui())
                                    applySMBRequest(resultAfterConstraints, object : Callback() {
                                        override fun run() {
                                            // Callback is only called if a bolus was actually requested
                                            if (result.enacted || result.success) {
                                                lastRun.smbSetByPump = result
                                                lastRun.lastSMBRequest = lastRun.lastAPSRun
                                                lastRun.lastSMBEnact = dateUtil.now()
                                            } else {
                                                Thread {
                                                    SystemClock.sleep(1000)
                                                    invoke("tempBasalFallback", allowNotification, true)
                                                }.start()
                                            }
                                            rxBus.send(EventLoopUpdateGui())
                                        }
                                    })
                                } else {
                                    lastRun.tbrSetByPump = result
                                    lastRun.lastTBRRequest = lastRun.lastAPSRun
                                }
                                rxBus.send(EventLoopUpdateGui())
                            }
                        })
                    } else {
                        lastRun.tbrSetByPump = null
                        lastRun.smbSetByPump = null
                    }
                } else {
                    if (resultAfterConstraints.isChangeRequested && allowNotification) {
                        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        builder.setSmallIcon(R.drawable.notif_icon)
                            .setContentTitle(rh.gs(R.string.openloop_newsuggestion))
                            .setContentText(resultAfterConstraints.toString())
                            .setAutoCancel(true)
                            .setPriority(Notification.IMPORTANCE_HIGH)
                            .setCategory(Notification.CATEGORY_ALARM)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        if (sp.getBoolean(R.string.key_wear_control, false)) {
                            builder.setLocalOnly(true)
                        }
                        presentSuggestion(builder)
                    } else if (allowNotification) {
                        dismissSuggestion()
                    }
                }
                rxBus.send(EventLoopUpdateGui())
            }
        } finally {
            aapsLogger.debug(LTag.APS, "invoke end")
        }
    }

    override fun disableCarbSuggestions(durationMinutes: Int) {
        carbsSuggestionsSuspendedUntil = System.currentTimeMillis() + durationMinutes * 60 * 1000
        dismissSuggestion()
    }

    private fun presentSuggestion(builder: NotificationCompat.Builder) {
        // Creates an explicit intent for an Activity in your app
        val resultIntent = Intent(context, MainActivity::class.java)

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(MainActivity::class.java)
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(resultPendingIntent)
        builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // mId allows you to update the notification later on.
        mNotificationManager.notify(Constants.notificationID, builder.build())
        rxBus.send(EventNewOpenLoopNotification())

        // Send to Wear
        rxBus.send(EventWearInitiateAction("changeRequest"))
    }

    private fun dismissSuggestion() {
        // dismiss notifications
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Constants.notificationID)
        rxBus.send(EventWearConfirmAction("cancelChangeRequest"))
    }

    override fun acceptChangeRequest() {
        val profile = profileFunction.getProfile() ?: return
        lastRun?.let { lastRun ->
            lastRun.constraintsProcessed?.let { constraintsProcessed ->
                applyTBRRequest(constraintsProcessed, profile, object : Callback() {
                    override fun run() {
                        if (result.enacted) {
                            lastRun.tbrSetByPump = result
                            lastRun.lastTBRRequest = lastRun.lastAPSRun
                            lastRun.lastTBREnact = dateUtil.now()
                            lastRun.lastOpenModeAccept = dateUtil.now()
                            buildDeviceStatus(dateUtil, this@LoopPlugin, iobCobCalculator, profileFunction,
                                activePlugin.activePump, receiverStatusStore, runningConfiguration,
                                BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION)?.also {
                                repository.insert(it)
                            }
                            sp.incInt(R.string.key_ObjectivesmanualEnacts)
                        }
                        rxBus.send(EventAcceptOpenLoopChange())
                    }
                })
            }
        }
        fabricPrivacy.logCustom("AcceptTemp")
    }

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     * TODO: update pump drivers to support APS request in %
     */
    private fun applyTBRRequest(request: APSResult, profile: Profile, callback: Callback?) {
        if (!request.tempBasalRequested) {
            callback?.result(PumpEnactResult(injector).enacted(false).success(true).comment(R.string.nochangerequested))?.run()
            return
        }
        val pump = activePlugin.activePump
        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + rh.gs(R.string.pumpNotInitialized))
            callback?.result(PumpEnactResult(injector).comment(R.string.pumpNotInitialized).enacted(false).success(false))?.run()
            return
        }
        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + rh.gs(R.string.pumpsuspended))
            callback?.result(PumpEnactResult(injector).comment(R.string.pumpsuspended).enacted(false).success(false))?.run()
            return
        }
        aapsLogger.debug(LTag.APS, "applyAPSRequest: $request")
        val now = System.currentTimeMillis()
        val activeTemp = iobCobCalculator.getTempBasalIncludingConvertedExtended(now)
        if (request.usePercent && allowPercentage()) {
            if (request.percent == 100 && request.duration == 0) {
                if (activeTemp != null) {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()")
                    uel.log(Action.CANCEL_TEMP_BASAL, Sources.Loop)
                    commandQueue.cancelTempBasal(false, callback)
                } else {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly")
                    callback?.result(PumpEnactResult(injector).percent(request.percent).duration(0)
                        .enacted(false).success(true).comment(R.string.basal_set_correctly))?.run()
                }
            } else if (activeTemp != null && activeTemp.plannedRemainingMinutes > 5 && request.duration - activeTemp.plannedRemainingMinutes < 30 && request.percent == activeTemp.convertedToPercent(now, profile)) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly")
                callback?.result(PumpEnactResult(injector).percent(request.percent)
                    .enacted(false).success(true).duration(activeTemp.plannedRemainingMinutes)
                    .comment(R.string.let_temp_basal_run))?.run()
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: tempBasalPercent()")
                uel.log(Action.TEMP_BASAL, Sources.Loop,
                    ValueWithUnit.Percent(request.percent),
                    ValueWithUnit.Minute(request.duration))
                commandQueue.tempBasalPercent(request.percent, request.duration, false, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
            }
        } else {
            if (request.rate == 0.0 && request.duration == 0 || abs(request.rate - pump.baseBasalRate) < pump.pumpDescription.basalStep) {
                if (activeTemp != null) {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()")
                    uel.log(Action.CANCEL_TEMP_BASAL, Sources.Loop)
                    commandQueue.cancelTempBasal(false, callback)
                } else {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly")
                    callback?.result(PumpEnactResult(injector).absolute(request.rate).duration(0)
                        .enacted(false).success(true).comment(R.string.basal_set_correctly))?.run()
                }
            } else if (activeTemp != null && activeTemp.plannedRemainingMinutes > 5 && request.duration - activeTemp.plannedRemainingMinutes < 30 && abs(request.rate - activeTemp.convertedToAbsolute(now, profile)) < pump.pumpDescription.basalStep) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly")
                callback?.result(PumpEnactResult(injector).absolute(activeTemp.convertedToAbsolute(now, profile))
                    .enacted(false).success(true).duration(activeTemp.plannedRemainingMinutes)
                    .comment(R.string.let_temp_basal_run))?.run()
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: setTempBasalAbsolute()")
                uel.log(Action.TEMP_BASAL, Sources.Loop,
                    ValueWithUnit.UnitPerHour(request.rate),
                    ValueWithUnit.Minute(request.duration))
                commandQueue.tempBasalAbsolute(request.rate, request.duration, false, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
            }
        }
    }

    private fun applySMBRequest(request: APSResult, callback: Callback?) {
        if (!request.bolusRequested()) {
            return
        }
        val pump = activePlugin.activePump
        val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        if (lastBolusTime != 0L && lastBolusTime + 3 * 60 * 1000 > System.currentTimeMillis()) {
            aapsLogger.debug(LTag.APS, "SMB requested but still in 3 min interval")
            callback?.result(PumpEnactResult(injector)
                .comment(R.string.smb_frequency_exceeded)
                .enacted(false).success(false))?.run()
            return
        }
        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + rh.gs(R.string.pumpNotInitialized))
            callback?.result(PumpEnactResult(injector).comment(R.string.pumpNotInitialized).enacted(false).success(false))?.run()
            return
        }
        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + rh.gs(R.string.pumpsuspended))
            callback?.result(PumpEnactResult(injector).comment(R.string.pumpsuspended).enacted(false).success(false))?.run()
            return
        }
        aapsLogger.debug(LTag.APS, "applySMBRequest: $request")

        // deliver SMB
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.lastKnownBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
        detailedBolusInfo.insulin = request.smb
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.SMB
        detailedBolusInfo.deliverAtTheLatest = request.deliverAt
        aapsLogger.debug(LTag.APS, "applyAPSRequest: bolus()")
        if (request.smb > 0.0)
            uel.log(Action.SMB, Sources.Loop, ValueWithUnit.Insulin(detailedBolusInfo.insulin))
        commandQueue.bolus(detailedBolusInfo, callback)
    }

    private fun allowPercentage(): Boolean {
        return virtualPumpPlugin.isEnabled()
    }

    override fun goToZeroTemp(durationInMinutes: Int, profile: Profile, reason: OfflineEvent.Reason) {
        val pump = activePlugin.activePump
        disposable += repository.runTransactionForResult(InsertAndCancelCurrentOfflineEventTransaction(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), reason))
            .subscribe({ result ->
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $it") }
            }, {
                aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
            })
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            commandQueue.tempBasalAbsolute(0.0, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        ErrorHelperActivity.runAlarm(context, result.comment, rh.gs(R.string.tempbasaldeliveryerror), R.raw.boluserror)
                    }
                }
            })
        } else {
            commandQueue.tempBasalPercent(0, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        ErrorHelperActivity.runAlarm(context, result.comment, rh.gs(R.string.tempbasaldeliveryerror), R.raw.boluserror)
                    }
                }
            })
        }
        if (pump.pumpDescription.isExtendedBolusCapable && iobCobCalculator.getExtendedBolus(dateUtil.now()) != null) {
            commandQueue.cancelExtended(object : Callback() {
                override fun run() {
                    if (!result.success) {
                        ErrorHelperActivity.runAlarm(context, result.comment, rh.gs(R.string.extendedbolusdeliveryerror), R.raw.boluserror)
                    }
                }
            })
        }
    }

    override fun suspendLoop(durationInMinutes: Int) {
        disposable += repository.runTransactionForResult(InsertAndCancelCurrentOfflineEventTransaction(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), OfflineEvent.Reason.SUSPEND))
            .subscribe({ result ->
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $it") }
            }, {
                aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
            })
        commandQueue.cancelTempBasal(true, object : Callback() {
            override fun run() {
                if (!result.success) {
                    ErrorHelperActivity.runAlarm(context, result.comment, rh.gs(R.string.tempbasaldeliveryerror), R.raw.boluserror)
                }
            }
        })
    }

    companion object {

        private const val CHANNEL_ID = "AndroidAPS-OpenLoop"
    }
}