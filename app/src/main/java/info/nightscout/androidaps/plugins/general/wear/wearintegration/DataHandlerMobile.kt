package info.nightscout.androidaps.plugins.general.wear.wearintegration

import android.app.NotificationManager
import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import info.nightscout.androidaps.database.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import info.nightscout.androidaps.dialogs.CarbsDialog
import info.nightscout.androidaps.dialogs.InsulinDialog
import info.nightscout.androidaps.events.EventMobileToWear
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.toStringShort
import info.nightscout.androidaps.extensions.total
import info.nightscout.androidaps.extensions.valueToUnits
import info.nightscout.androidaps.extensions.valueToUnitsString
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.GlucoseValueDataPoint
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.services.AlarmSoundServiceHelper
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.wizard.BolusWizard
import info.nightscout.androidaps.utils.wizard.QuickWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

@Singleton
class DataHandlerMobile @Inject constructor(
    aapsSchedulers: AapsSchedulers,
    private val injector: HasAndroidInjector,
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val sp: SP,
    private val config: Config,
    private val iobCobCalculator: IobCobCalculator,
    private val repository: AppRepository,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val profileFunction: ProfileFunction,
    private val loop: Loop,
    private val nsDeviceStatus: NSDeviceStatus,
    private val receiverStatusStore: ReceiverStatusStore,
    private val quickWizard: QuickWizard,
    private val defaultValueHelper: DefaultValueHelper,
    private val trendCalculator: TrendCalculator,
    private val dateUtil: DateUtil,
    private val constraintChecker: ConstraintChecker,
    private val uel: UserEntryLogger,
    private val activePlugin: ActivePlugin,
    private val commandQueue: CommandQueue,
    private val fabricPrivacy: FabricPrivacy,
    private val alarmSoundServiceHelper: AlarmSoundServiceHelper
) {

    private val disposable = CompositeDisposable()

    private var lastBolusWizard: BolusWizard? = null

    init {
        // From Wear
        disposable += rxBus
            .toObservable(EventData.ActionPong::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "Pong received from ${it.sourceNodeId}")
                           fabricPrivacy.logCustom("WearOS_${it.apiLevel}")
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.CancelBolus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "CancelBolus received from ${it.sourceNodeId}")
                           activePlugin.activePump.stopBolusDelivering()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.OpenLoopRequestConfirmed::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "OpenLoopRequestConfirmed received from ${it.sourceNodeId}")
                           loop.acceptChangeRequest()
                           (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Constants.notificationID)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionResendData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ResendData received from ${it.sourceNodeId}")
                           resendData(it.from)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionPumpStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionPumpStatus received from ${it.sourceNodeId}")
                           rxBus.send(
                               EventMobileToWear(
                                   EventData.ConfirmAction(
                                       rh.gs(R.string.medtronic_pump_status).uppercase(),
                                       activePlugin.activePump.shortStatus(false),
                                       returnCommand = null
                                   )
                               )
                           )
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionLoopStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionLoopStatus received from ${it.sourceNodeId}")
                           rxBus.send(
                               EventMobileToWear(
                                   EventData.ConfirmAction(
                                       rh.gs(R.string.loop_status).uppercase(),
                                       "TARGETS:\n$targetsStatus\n\n$loopStatus\n\nOAPS RESULT:\n$oAPSResultStatus",
                                       returnCommand = null
                                   )
                               )
                           )
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionTddStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "ActionTddStatus received from ${it.sourceNodeId}")
                handleTddStatus()
            }
        disposable += rxBus
            .toObservable(EventData.ActionProfileSwitchSendInitialData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionProfileSwitchSendInitialData received $it from ${it.sourceNodeId}")
                           handleProfileSwitchSendInitialData()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionProfileSwitchPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionProfileSwitchPreCheck received $it from ${it.sourceNodeId}")
                           handleProfileSwitchPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionProfileSwitchConfirmed::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionProfileSwitchConfirmed received $it from ${it.sourceNodeId}")
                           doProfileSwitch(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionTempTargetPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionTempTargetPreCheck received $it from ${it.sourceNodeId}")
                           handleTempTargetPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionTempTargetConfirmed::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionTempTargetConfirmed received $it from ${it.sourceNodeId}")
                           doTempTarget(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionBolusPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionBolusPreCheck received $it from ${it.sourceNodeId}")
                           handleBolusPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionBolusConfirmed::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionBolusConfirmed received $it from ${it.sourceNodeId}")
                           doBolus(it.insulin, it.carbs, null, 0)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionECarbsPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionECarbsPreCheck received $it from ${it.sourceNodeId}")
                           handleECarbsPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionECarbsConfirmed::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionECarbsConfirmed received $it from ${it.sourceNodeId}")
                           doECarbs(it.carbs, it.carbsTime, it.duration)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionFillPresetPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionFillPresetPreCheck received $it from ${it.sourceNodeId}")
                           handleFillPresetPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionFillPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionFillPreCheck received $it from ${it.sourceNodeId}")
                           handleFillPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionFillConfirmed::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionFillConfirmed received $it from ${it.sourceNodeId}")
                           if (constraintChecker.applyBolusConstraints(Constraint(it.insulin)).value() - it.insulin != 0.0) {
                               ToastUtils.showToastInUiThread(context, "aborting: previously applied constraint changed")
                               sendError("aborting: previously applied constraint changed")
                           } else
                               doFillBolus(it.insulin)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionQuickWizardPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionQuickWizardPreCheck received $it from ${it.sourceNodeId}")
                           handleQuickWizardPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionWizardPreCheck::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionWizardPreCheck received $it from ${it.sourceNodeId}")
                           handleWizardPreCheck(it)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.ActionWizardConfirmed::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "ActionWizardConfirmed received $it from ${it.sourceNodeId}")
                           if (lastBolusWizard?.timeStamp == it.timeStamp) { //use last calculation as confirmed string matches
                               doBolus(lastBolusWizard!!.calculatedTotalInsulin, lastBolusWizard!!.carbs, null, 0)
                           }
                           lastBolusWizard = null
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.SnoozeAlert::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "SnoozeAlert received $it from ${it.sourceNodeId}")
                           alarmSoundServiceHelper.stopService(context, "Muted from wear")
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventData.WearException::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "WearException received $it from ${it.sourceNodeId}")
                           fabricPrivacy.logWearException(it)
                       }, fabricPrivacy::logException)
    }

    private fun handleTddStatus() {
        val activePump = activePlugin.activePump
        var message: String
        // check if DB up to date
        val dummies: MutableList<TotalDailyDose> = LinkedList()
        val historyList = getTDDList(dummies)
        if (isOldData(historyList)) {
            message = "OLD DATA - "
            //if pump is not busy: try to fetch data
            if (activePump.isBusy()) {
                message += rh.gs(R.string.pumpbusy)
            } else {
                message += "trying to fetch data from pump."
                commandQueue.loadTDDs(object : Callback() {
                    override fun run() {
                        val dummies1: MutableList<TotalDailyDose> = LinkedList()
                        val historyList1 = getTDDList(dummies1)
                        val reloadMessage =
                            if (isOldData(historyList1))
                                "TDD: Still old data! Cannot load from pump.\n" + generateTDDMessage(historyList1, dummies1)
                            else
                                generateTDDMessage(historyList1, dummies1)
                        rxBus.send(
                            EventMobileToWear(
                                EventData.ConfirmAction(
                                    rh.gs(R.string.tdd),
                                    reloadMessage,
                                    returnCommand = null
                                )
                            )
                        )
                    }
                })
            }
        } else { // if up to date: prepare, send (check if CPP is activated -> add CPP stats)
            message = generateTDDMessage(historyList, dummies)
        }
        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.tdd),
                    message,
                    returnCommand = null
                )
            )
        )
    }

    private fun handleWizardPreCheck(command: EventData.ActionWizardPreCheck) {
        val pump = activePlugin.activePump
        if (!pump.isInitialized() || pump.isSuspended() || loop.isDisconnected) {
            sendError(rh.gs(R.string.wizard_pump_not_available))
            return
        }
        val carbsBeforeConstraints = command.carbs
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbsBeforeConstraints)).value()
        if (carbsAfterConstraints - carbsBeforeConstraints != 0) {
            sendError(rh.gs(R.string.wizard_carbs_constraint))
            return
        }
        val percentage = command.percentage
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        if (profile == null) {
            sendError(rh.gs(R.string.wizard_no_active_profile))
            return
        }
        val bgReading = iobCobCalculator.ads.actualBg()
        if (bgReading == null) {
            sendError(rh.gs(R.string.wizard_no_actual_bg))
            return
        }
        val cobInfo = iobCobCalculator.getCobInfo(false, "Wizard wear")
        if (cobInfo.displayCob == null) {
            sendError(rh.gs(R.string.wizard_no_cob))
            return
        }
        val dbRecord = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        val tempTarget = if (dbRecord is ValueWrapper.Existing) dbRecord.value else null

        val bolusWizard = BolusWizard(injector).doCalc(
            profile = profile,
            profileName = profileName,
            tempTarget = tempTarget,
            carbs = carbsAfterConstraints,
            cob = cobInfo.displayCob!!,
            bg = bgReading.valueToUnits(profileFunction.getUnits()),
            correction = 0.0,
            percentageCorrection = percentage,
            useBg = sp.getBoolean(R.string.key_wearwizard_bg, true),
            useCob = sp.getBoolean(R.string.key_wearwizard_cob, true),
            includeBolusIOB = sp.getBoolean(R.string.key_wearwizard_iob, true),
            includeBasalIOB = sp.getBoolean(R.string.key_wearwizard_iob, true),
            useSuperBolus = false,
            useTT = sp.getBoolean(R.string.key_wearwizard_tt, false),
            useTrend = sp.getBoolean(R.string.key_wearwizard_trend, false),
            useAlarm = false
        )
        val insulinAfterConstraints = bolusWizard.insulinAfterConstraints
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)
        if (abs(insulinAfterConstraints - bolusWizard.calculatedTotalInsulin) >= minStep) {
            sendError(rh.gs(R.string.wizard_constraint_bolus_size, bolusWizard.calculatedTotalInsulin))
            return
        }
        if (bolusWizard.calculatedTotalInsulin <= 0 && bolusWizard.carbs <= 0) {
            sendError("No insulin required")
            return
        }
        val message =
            rh.gs(R.string.wizard_result, bolusWizard.calculatedTotalInsulin, bolusWizard.carbs) + "\n_____________\n" + bolusWizard.explainShort()
        lastBolusWizard = bolusWizard
        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.confirm).uppercase(), message,
                    returnCommand = EventData.ActionWizardConfirmed(bolusWizard.timeStamp)
                )
            )
        )
    }

    private fun handleQuickWizardPreCheck(command: EventData.ActionQuickWizardPreCheck) {
        val actualBg = iobCobCalculator.ads.actualBg()
        val profile = profileFunction.getProfile()
        val profileName = profileFunction.getProfileName()
        val quickWizardEntry = quickWizard.get(command.guid)
        //Log.i("QuickWizard", "handleInitiate: quick_wizard " + quickWizardEntry?.buttonText() + " c " + quickWizardEntry?.carbs())
        if (quickWizardEntry == null) {
            sendError(rh.gs(R.string.quick_wizard_not_available))
            return
        }
        if (actualBg == null) {
            sendError(rh.gs(R.string.wizard_no_actual_bg))
            return
        }
        if (profile == null) {
            sendError(rh.gs(R.string.wizard_no_active_profile))
            return
        }
        val cobInfo = iobCobCalculator.getCobInfo(false, "QuickWizard wear")
        if (cobInfo.displayCob == null) {
            sendError(rh.gs(R.string.wizard_no_cob))
            return
        }
        val pump = activePlugin.activePump
        if (!pump.isInitialized() || pump.isSuspended() || loop.isDisconnected) {
            sendError(rh.gs(R.string.wizard_pump_not_available))
            return
        }

        val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true)

        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(quickWizardEntry.carbs())).value()
        if (carbsAfterConstraints != quickWizardEntry.carbs()) {
            sendError(rh.gs(R.string.wizard_carbs_constraint))
            return
        }
        val insulinAfterConstraints = wizard.insulinAfterConstraints
        val minStep = pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints)
        if (abs(insulinAfterConstraints - wizard.calculatedTotalInsulin) >= minStep) {
            sendError(rh.gs(R.string.wizard_constraint_bolus_size, wizard.calculatedTotalInsulin))
            return
        }

        val message = rh.gs(R.string.quick_wizard_message, quickWizardEntry.buttonText(), wizard.calculatedTotalInsulin, quickWizardEntry.carbs()) +
            "\n_____________\n" + wizard.explainShort()

        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.confirm).uppercase(), message,
                    returnCommand = EventData.ActionBolusConfirmed(insulinAfterConstraints, carbsAfterConstraints)
                )
            )
        )
    }

    private fun handleBolusPreCheck(command: EventData.ActionBolusPreCheck) {
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(command.insulin)).value()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(command.carbs)).value()
        val pump = activePlugin.activePump
        if (insulinAfterConstraints > 0 && (!pump.isInitialized() || pump.isSuspended() || loop.isDisconnected)) {
            sendError(rh.gs(R.string.wizard_pump_not_available))
            return
        }
        var message = ""
        message += rh.gs(R.string.bolus) + ": " + insulinAfterConstraints + "U\n"
        message += rh.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g"
        if (insulinAfterConstraints - command.insulin != 0.0 || carbsAfterConstraints - command.carbs != 0)
            message += "\n" + rh.gs(R.string.constraintapllied)
        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.confirm).uppercase(), message,
                    returnCommand = EventData.ActionBolusConfirmed(insulinAfterConstraints, carbsAfterConstraints)
                )
            )
        )
    }

    private fun handleECarbsPreCheck(command: EventData.ActionECarbsPreCheck) {
        val startTimeStamp = System.currentTimeMillis() + T.mins(command.carbsTimeShift.toLong()).msecs()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(command.carbs)).value()
        var message = rh.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g" +
            "\n" + rh.gs(R.string.time) + ": " + dateUtil.timeString(startTimeStamp) +
            "\n" + rh.gs(R.string.duration) + ": " + command.duration + "h"
        if (carbsAfterConstraints - command.carbs != 0) {
            message += "\n" + rh.gs(R.string.constraintapllied)
        }
        if (carbsAfterConstraints <= 0) {
            sendError("Carbs = 0! No action taken!")
            return
        }
        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.confirm).uppercase(), message,
                    returnCommand = EventData.ActionECarbsConfirmed(carbsAfterConstraints, startTimeStamp, command.duration)
                )
            )
        )
    }

    private fun handleFillPresetPreCheck(command: EventData.ActionFillPresetPreCheck) {
        val amount: Double = when (command.button) {
            1    -> sp.getDouble("fill_button1", 0.3)
            2    -> sp.getDouble("fill_button2", 0.0)
            3    -> sp.getDouble("fill_button3", 0.0)
            else -> return
        }
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
        var message = rh.gs(R.string.primefill) + ": " + insulinAfterConstraints + "U"
        if (insulinAfterConstraints - amount != 0.0) message += "\n" + rh.gs(R.string.constraintapllied)
        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.confirm).uppercase(), message,
                    returnCommand = EventData.ActionFillConfirmed(insulinAfterConstraints)
                )
            )
        )
    }

    private fun handleFillPreCheck(command: EventData.ActionFillPreCheck) {
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(command.insulin)).value()
        var message = rh.gs(R.string.primefill) + ": " + insulinAfterConstraints + "U"
        if (insulinAfterConstraints - command.insulin != 0.0) message += "\n" + rh.gs(R.string.constraintapllied)
        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.confirm).uppercase(), message,
                    returnCommand = EventData.ActionFillConfirmed(insulinAfterConstraints)
                )
            )
        )
    }

    private fun handleProfileSwitchSendInitialData() {
        val activeProfileSwitch = repository.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet()
        if (activeProfileSwitch is ValueWrapper.Existing) { // read CPP values
            rxBus.send(
                EventMobileToWear(EventData.ActionProfileSwitchOpenActivity(T.msecs(activeProfileSwitch.value.originalTimeshift).hours().toInt(), activeProfileSwitch.value.originalPercentage))
            )
        } else {
            sendError("No active profile switch!")
            return
        }

    }

    private fun handleProfileSwitchPreCheck(command: EventData.ActionProfileSwitchPreCheck) {
        val activeProfileSwitch = repository.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet()
        if (activeProfileSwitch is ValueWrapper.Absent) {
            sendError("No active profile switch!")
        }
        if (command.percentage < Constants.CPP_MIN_PERCENTAGE || command.percentage > Constants.CPP_MAX_PERCENTAGE) {
            sendError(rh.gs(R.string.valueoutofrange, "Profile-Percentage"))
        }
        if (command.timeShift < 0 || command.timeShift > 23) {
            sendError(rh.gs(R.string.valueoutofrange, "Profile-Timeshift"))
        }
        val message = "Profile:" + "\n\n" +
            "Timeshift: " + command.timeShift + "\n" +
            "Percentage: " + command.percentage + "%"
        rxBus.send(
            EventMobileToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.confirm).uppercase(), message,
                    returnCommand = EventData.ActionProfileSwitchConfirmed(command.timeShift, command.percentage)
                )
            )
        )
    }

    private fun handleTempTargetPreCheck(action: EventData.ActionTempTargetPreCheck) {
        val title = rh.gs(R.string.confirm).uppercase()
        var message = ""
        val presetIsMGDL = profileFunction.getUnits() == GlucoseUnit.MGDL
        when (action.command) {
            EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_ACTIVITY -> {
                val activityTTDuration = defaultValueHelper.determineActivityTTDuration()
                val activityTT = defaultValueHelper.determineActivityTT()
                val reason = rh.gs(R.string.activity)
                message += rh.gs(R.string.wear_action_tempt_preset_message, reason, activityTT, activityTTDuration)
                rxBus.send(
                    EventMobileToWear(
                        EventData.ConfirmAction(
                            title, message,
                            returnCommand = EventData.ActionTempTargetConfirmed(presetIsMGDL, activityTTDuration, activityTT, activityTT)
                        )
                    )
                )
            }

            EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_HYPO     -> {
                val hypoTTDuration = defaultValueHelper.determineHypoTTDuration()
                val hypoTT = defaultValueHelper.determineHypoTT()
                val reason = rh.gs(R.string.hypo)
                message += rh.gs(R.string.wear_action_tempt_preset_message, reason, hypoTT, hypoTTDuration)
                rxBus.send(
                    EventMobileToWear(
                        EventData.ConfirmAction(
                            title, message,
                            returnCommand = EventData.ActionTempTargetConfirmed(presetIsMGDL, hypoTTDuration, hypoTT, hypoTT)
                        )
                    )
                )
            }

            EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_EATING   -> {
                val eatingSoonTTDuration = defaultValueHelper.determineEatingSoonTTDuration()
                val eatingSoonTT = defaultValueHelper.determineEatingSoonTT()
                val reason = rh.gs(R.string.eatingsoon)
                message += rh.gs(R.string.wear_action_tempt_preset_message, reason, eatingSoonTT, eatingSoonTTDuration)
                rxBus.send(
                    EventMobileToWear(
                        EventData.ConfirmAction(
                            title, message,
                            returnCommand = EventData.ActionTempTargetConfirmed(presetIsMGDL, eatingSoonTTDuration, eatingSoonTT, eatingSoonTT)
                        )
                    )
                )
            }

            EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL          -> {
                message += rh.gs(R.string.wear_action_tempt_cancel_message)
                rxBus.send(
                    EventMobileToWear(
                        EventData.ConfirmAction(
                            title, message,
                            returnCommand = EventData.ActionTempTargetConfirmed(true, 0, 0.0, 0.0)
                        )
                    )
                )
            }

            EventData.ActionTempTargetPreCheck.TempTargetCommand.MANUAL          -> {
                if (profileFunction.getUnits() == GlucoseUnit.MGDL != action.isMgdl) {
                    sendError(rh.gs(R.string.wear_action_tempt_unit_error))
                    return
                }
                if (action.duration == 0) {
                    message += rh.gs(R.string.wear_action_tempt_zero_message)
                    rxBus.send(
                        EventMobileToWear(
                            EventData.ConfirmAction(
                                title, message,
                                returnCommand = EventData.ActionTempTargetConfirmed(true, 0, 0.0, 0.0)
                            )
                        )
                    )
                } else {
                    var low = action.low
                    var high = action.high
                    if (!action.isMgdl) {
                        low *= Constants.MMOLL_TO_MGDL
                        high *= Constants.MMOLL_TO_MGDL
                    }
                    if (low < HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0] || low > HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1]) {
                        sendError(rh.gs(R.string.wear_action_tempt_min_bg_error))
                        return
                    }
                    if (high < HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0] || high > HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1]) {
                        sendError(rh.gs(R.string.wear_action_tempt_max_bg_error))
                        return
                    }
                    message += if (low == high) rh.gs(R.string.wear_action_tempt_manual_message, action.low, action.duration)
                    else rh.gs(R.string.wear_action_tempt_manual_range_message, action.low, action.high, action.duration)
                    rxBus.send(
                        EventMobileToWear(
                            EventData.ConfirmAction(
                                title, message,
                                returnCommand = EventData.ActionTempTargetConfirmed(presetIsMGDL, action.duration, action.low, action.high)
                            )
                        )
                    )
                }
            }
        }
    }

    private fun QuickWizardEntry.toWear(): EventData.QuickWizard.QuickWizardEntry =
        EventData.QuickWizard.QuickWizardEntry(
            guid = guid(),
            buttonText = buttonText(),
            carbs = carbs(),
            validFrom = validFrom(),
            validTo = validTo()
        )

    fun resendData(from: String) {
        aapsLogger.debug(LTag.WEAR, "Sending data to wear from $from")
        // SingleBg
        iobCobCalculator.ads.lastBg()?.let { rxBus.send(EventMobileToWear(getSingleBG(it))) }
        // Preferences
        rxBus.send(
            EventMobileToWear(
                EventData.Preferences(
                    timeStamp = System.currentTimeMillis(),
                    wearControl = sp.getBoolean(R.string.key_wear_control, false),
                    unitsMgdl = profileFunction.getUnits() == GlucoseUnit.MGDL,
                    bolusPercentage = sp.getInt(R.string.key_boluswizard_percentage, 100),
                    maxCarbs = sp.getInt(R.string.key_treatmentssafety_maxcarbs, 48),
                    maxBolus = sp.getDouble(R.string.key_treatmentssafety_maxbolus, 3.0),
                    insulinButtonIncrement1 = sp.getDouble(R.string.key_insulin_button_increment_1, InsulinDialog.PLUS1_DEFAULT),
                    insulinButtonIncrement2 = sp.getDouble(R.string.key_insulin_button_increment_2, InsulinDialog.PLUS2_DEFAULT),
                    carbsButtonIncrement1 = sp.getInt(R.string.key_carbs_button_increment_1, CarbsDialog.FAV1_DEFAULT),
                    carbsButtonIncrement2 = sp.getInt(R.string.key_carbs_button_increment_2, CarbsDialog.FAV2_DEFAULT)
                )
            )
        )
        // QuickWizard
        rxBus.send(
            EventMobileToWear(
                EventData.QuickWizard(
                    ArrayList(quickWizard.list().filter { it.forDevice(QuickWizardEntry.DEVICE_WATCH) }.map { it.toWear() })
                )
            )
        )
        // GraphData
        val startTime = System.currentTimeMillis() - (60000 * 60 * 5.5).toLong()
        rxBus.send(EventMobileToWear(EventData.GraphData(ArrayList(repository.compatGetBgReadingsDataFromTime(startTime, true).blockingGet().map { getSingleBG(it) }))))
        // Treatments
        sendTreatments()
        // Status
        // Keep status last. Wear start refreshing after status received
        sendStatus()
    }

    private fun sendTreatments() {
        val now = System.currentTimeMillis()
        val startTimeWindow = now - (60000 * 60 * 5.5).toLong()
        val basals = arrayListOf<EventData.TreatmentData.Basal>()
        val temps = arrayListOf<EventData.TreatmentData.TempBasal>()
        val boluses = arrayListOf<EventData.TreatmentData.Treatment>()
        val predictions = arrayListOf<EventData.SingleBg>()
        val profile = profileFunction.getProfile() ?: return
        var beginBasalSegmentTime = startTimeWindow
        var runningTime = startTimeWindow
        var beginBasalValue = profile.getBasal(beginBasalSegmentTime)
        var endBasalValue = beginBasalValue
        var tb1 = iobCobCalculator.getTempBasalIncludingConvertedExtended(runningTime)
        var tb2: TemporaryBasal?
        var tbBefore = beginBasalValue
        var tbAmount = beginBasalValue
        var tbStart = runningTime
        if (tb1 != null) {
            val profileTB = profileFunction.getProfile(runningTime)
            if (profileTB != null) {
                tbAmount = tb1.convertedToAbsolute(runningTime, profileTB)
                tbStart = runningTime
            }
        }
        while (runningTime < now) {
            val profileTB = profileFunction.getProfile(runningTime) ?: return
            //basal rate
            endBasalValue = profile.getBasal(runningTime)
            if (endBasalValue != beginBasalValue) {
                //push the segment we recently left
                basals.add(EventData.TreatmentData.Basal(beginBasalSegmentTime, runningTime, beginBasalValue))

                //begin new Basal segment
                beginBasalSegmentTime = runningTime
                beginBasalValue = endBasalValue
            }

            //temps
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(runningTime)
            if (tb1 == null && tb2 == null) {
                //no temp stays no temp
            } else if (tb1 != null && tb2 == null) {
                //temp is over -> push it
                temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, runningTime, endBasalValue, tbAmount))
                tb1 = null
            } else if (tb1 == null && tb2 != null) {
                //temp begins
                tb1 = tb2
                tbStart = runningTime
                tbBefore = endBasalValue
                tbAmount = tb1.convertedToAbsolute(runningTime, profileTB)
            } else if (tb1 != null && tb2 != null) {
                val currentAmount = tb2.convertedToAbsolute(runningTime, profileTB)
                if (currentAmount != tbAmount) {
                    temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, runningTime, currentAmount, tbAmount))
                    tbStart = runningTime
                    tbBefore = tbAmount
                    tbAmount = currentAmount
                    tb1 = tb2
                }
            }
            runningTime += (5 * 60 * 1000).toLong()
        }
        if (beginBasalSegmentTime != runningTime) {
            //push the remaining segment
            basals.add(EventData.TreatmentData.Basal(beginBasalSegmentTime, runningTime, beginBasalValue))
        }
        if (tb1 != null) {
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(now) //use "now" to express current situation
            if (tb2 == null) {
                //express the cancelled temp by painting it down one minute early
                temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, now - 60 * 1000, endBasalValue, tbAmount))
            } else {
                //express currently running temp by painting it a bit into the future
                val profileNow = profileFunction.getProfile(now)
                val currentAmount = tb2.convertedToAbsolute(now, profileNow!!)
                if (currentAmount != tbAmount) {
                    temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, now, tbAmount, tbAmount))
                    temps.add(EventData.TreatmentData.TempBasal(now, tbAmount, runningTime + 5 * 60 * 1000, currentAmount, currentAmount))
                } else {
                    temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, runningTime + 5 * 60 * 1000, tbAmount, tbAmount))
                }
            }
        } else {
            tb2 = iobCobCalculator.getTempBasalIncludingConvertedExtended(now) //use "now" to express current situation
            if (tb2 != null) {
                //onset at the end
                val profileTB = profileFunction.getProfile(runningTime)
                val currentAmount = tb2.convertedToAbsolute(runningTime, profileTB!!)
                temps.add(EventData.TreatmentData.TempBasal(now - 60 * 1000, endBasalValue, runningTime + 5 * 60 * 1000, currentAmount, currentAmount))
            }
        }
        repository.getBolusesIncludingInvalidFromTime(startTimeWindow, true).blockingGet()
            .stream()
            .filter { (_, _, _, _, _, _, _, _, _, type) -> type !== Bolus.Type.PRIMING }
            .forEach { (_, _, _, isValid, _, _, timestamp, _, amount, type) -> boluses.add(EventData.TreatmentData.Treatment(timestamp, amount, 0.0, type === Bolus.Type.SMB, isValid)) }
        repository.getCarbsDataFromTimeExpanded(startTimeWindow, true).blockingGet()
            .forEach { (_, _, _, isValid, _, _, timestamp, _, _, amount) -> boluses.add(EventData.TreatmentData.Treatment(timestamp, 0.0, amount, false, isValid)) }
        val finalLastRun = loop.lastRun
        if (sp.getBoolean("wear_predictions", true) && finalLastRun?.request?.hasPredictions == true && finalLastRun.constraintsProcessed != null) {
            val predArray = finalLastRun.constraintsProcessed!!.predictions
                .stream().map { bg: GlucoseValue -> GlucoseValueDataPoint(bg, defaultValueHelper, profileFunction, rh) }
                .collect(Collectors.toList())
            if (predArray.isNotEmpty())
                for (bg in predArray) if (bg.data.value > 39)
                    predictions.add(
                        EventData.SingleBg(
                            timeStamp = bg.data.timestamp,
                            glucoseUnits = Constants.MGDL,
                            sgv = bg.data.value,
                            high = 0.0,
                            low = 0.0,
                            color = bg.color(null)
                        )
                    )
        }
        rxBus.send(EventMobileToWear(EventData.TreatmentData(temps, basals, boluses, predictions)))
    }

    private fun sendStatus() {
        val profile = profileFunction.getProfile()
        var status = rh.gs(R.string.noprofile)
        var iobSum = ""
        var iobDetail = ""
        var cobString = ""
        var currentBasal = ""
        var bgiString = ""
        if (profile != null) {
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            iobSum = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob)
            iobDetail = "(${DecimalFormatter.to2Decimal(bolusIob.iob)}|${DecimalFormatter.to2Decimal(basalIob.basaliob)})"
            cobString = iobCobCalculator.getCobInfo(false, "WatcherUpdaterService").generateCOBString()
            currentBasal = iobCobCalculator.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())?.toStringShort() ?: rh.gs(R.string.pump_basebasalrate, profile.getBasal())

            //bgi
            val bgi = -(bolusIob.activity + basalIob.activity) * 5 * Profile.fromMgdlToUnits(profile.getIsfMgdl(), profileFunction.getUnits())
            bgiString = "" + (if (bgi >= 0) "+" else "") + DecimalFormatter.to1Decimal(bgi)
            status = generateStatusString(profile, currentBasal, iobSum, iobDetail, bgiString)
        }

        //batteries
        val phoneBattery = receiverStatusStore.batteryLevel
        val rigBattery = nsDeviceStatus.uploaderStatus.trim { it <= ' ' }
        //OpenAPS status
        val openApsStatus =
            if (config.APS) loop.lastRun?.let { if (it.lastTBREnact != 0L) it.lastTBREnact else -1 } ?: -1
            else nsDeviceStatus.openApsTimestamp

        rxBus.send(
            EventMobileToWear(
                EventData.Status(
                    externalStatus = status,
                    iobSum = iobSum,
                    iobDetail = iobDetail,
                    detailedIob = sp.getBoolean(R.string.key_wear_detailediob, false),
                    cob = cobString,
                    currentBasal = currentBasal,
                    battery = phoneBattery.toString(),
                    rigBattery = rigBattery,
                    openApsStatus = openApsStatus,
                    bgi = bgiString,
                    showBgi = sp.getBoolean(R.string.key_wear_showbgi, false),
                    batteryLevel = if (phoneBattery >= 30) 1 else 0
                )
            )
        )
    }

    private fun deltaString(deltaMGDL: Double, deltaMMOL: Double, units: GlucoseUnit): String {
        val detailed = sp.getBoolean(R.string.key_wear_detailed_delta, false)
        var deltaString = if (deltaMGDL >= 0) "+" else "-"
        deltaString += if (units == GlucoseUnit.MGDL) {
            if (detailed) DecimalFormatter.to1Decimal(abs(deltaMGDL)) else DecimalFormatter.to0Decimal(abs(deltaMGDL))
        } else {
            if (detailed) DecimalFormatter.to2Decimal(abs(deltaMMOL)) else DecimalFormatter.to1Decimal(abs(deltaMMOL))
        }
        return deltaString
    }

    private fun getSingleBG(glucoseValue: GlucoseValue): EventData.SingleBg {
        val glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true)
        val units = profileFunction.getUnits()
        val lowLine = Profile.toMgdl(defaultValueHelper.determineLowLine(), units)
        val highLine = Profile.toMgdl(defaultValueHelper.determineHighLine(), units)

        return EventData.SingleBg(
            timeStamp = glucoseValue.timestamp,
            sgvString = glucoseValue.valueToUnitsString(units),
            glucoseUnits = units.asText,
            slopeArrow = trendCalculator.getTrendArrow(glucoseValue).symbol,
            delta = glucoseStatus?.let { deltaString(it.delta, it.delta * Constants.MGDL_TO_MMOLL, units) } ?: "--",
            avgDelta = glucoseStatus?.let { deltaString(it.shortAvgDelta, it.shortAvgDelta * Constants.MGDL_TO_MMOLL, units) } ?: "--",
            sgvLevel = if (glucoseValue.value > highLine) 1L else if (glucoseValue.value < lowLine) -1L else 0L,
            sgv = glucoseValue.value,
            high = highLine,
            low = lowLine,
            color = 0
        )
    }

    //Check for Temp-Target:
    private
    val targetsStatus: String
        get() {
            var ret = ""
            if (!config.APS) {
                return "Targets only apply in APS mode!"
            }
            val profile = profileFunction.getProfile() ?: return "No profile set :("
            //Check for Temp-Target:
            val tempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
            if (tempTarget is ValueWrapper.Existing) {
                ret += "Temp Target: " + Profile.toTargetRangeString(tempTarget.value.lowTarget, tempTarget.value.lowTarget, GlucoseUnit.MGDL, profileFunction.getUnits())
                ret += "\nuntil: " + dateUtil.timeString(tempTarget.value.end)
                ret += "\n\n"
            }
            ret += "DEFAULT RANGE: "
            ret += Profile.fromMgdlToUnits(profile.getTargetLowMgdl(), profileFunction.getUnits()).toString() + " - " + Profile.fromMgdlToUnits(
                profile.getTargetHighMgdl(),
                profileFunction.getUnits()
            )
            ret += " target: " + Profile.fromMgdlToUnits(profile.getTargetMgdl(), profileFunction.getUnits())
            return ret
        }

    private
    val oAPSResultStatus: String
        get() {
            var ret = ""
            if (!config.APS)
                return "Only apply in APS mode!"
            val usedAPS = activePlugin.activeAPS
            val result = usedAPS.lastAPSResult ?: return "Last result not available!"
            ret += if (!result.isChangeRequested) {
                rh.gs(R.string.nochangerequested) + "\n"
            } else if (result.rate == 0.0 && result.duration == 0) {
                rh.gs(R.string.canceltemp) + "\n"
            } else {
                rh.gs(R.string.rate) + ": " + DecimalFormatter.to2Decimal(result.rate) + " U/h " +
                    "(" + DecimalFormatter.to2Decimal(result.rate / activePlugin.activePump.baseBasalRate * 100) + "%)\n" +
                    rh.gs(R.string.duration) + ": " + DecimalFormatter.to0Decimal(result.duration.toDouble()) + " min\n"
            }
            ret += "\n" + rh.gs(R.string.reason) + ": " + result.reason
            return ret
        }

    // decide if enabled/disabled closed/open; what Plugin as APS?
    private
    val loopStatus: String
        get() {
            var ret = ""
            // decide if enabled/disabled closed/open; what Plugin as APS?
            if ((loop as PluginBase).isEnabled()) {
                ret += if (constraintChecker.isClosedLoopAllowed().value()) {
                    "CLOSED LOOP\n"
                } else {
                    "OPEN LOOP\n"
                }
                val aps = activePlugin.activeAPS
                ret += "APS: " + (aps as PluginBase).name
                val lastRun = loop.lastRun
                if (lastRun != null) {
                    ret += "\nLast Run: " + dateUtil.timeString(lastRun.lastAPSRun)
                    if (lastRun.lastTBREnact != 0L) ret += "\nLast Enact: " + dateUtil.timeString(lastRun.lastTBREnact)
                }
            } else {
                ret += "LOOP DISABLED\n"
            }
            return ret
        }

    private fun isOldData(historyList: List<TotalDailyDose>): Boolean {
        val startsYesterday = activePlugin.activePump.pumpDescription.supportsTDDs
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        return historyList.size < 3 || df.format(Date(historyList[0].timestamp)) != df.format(Date(System.currentTimeMillis() - if (startsYesterday) 1000 * 60 * 60 * 24 else 0))
    }

    private fun getTDDList(returnDummies: MutableList<TotalDailyDose>): MutableList<TotalDailyDose> {
        var historyList = repository.getLastTotalDailyDoses(10, false).blockingGet().toMutableList()
        //var historyList = databaseHelper.getTDDs().toMutableList()
        historyList = historyList.subList(0, min(10, historyList.size))
        //fill single gaps - only needed for Dana*R data
        val dummies: MutableList<TotalDailyDose> = returnDummies
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        for (i in 0 until historyList.size - 1) {
            val elem1 = historyList[i]
            val elem2 = historyList[i + 1]
            if (df.format(Date(elem1.timestamp)) != df.format(Date(elem2.timestamp + 25 * 60 * 60 * 1000))) {
                val dummy = TotalDailyDose(timestamp = elem1.timestamp - T.hours(24).msecs(), bolusAmount = elem1.bolusAmount / 2, basalAmount = elem1.basalAmount / 2)
                dummies.add(dummy)
                elem1.basalAmount /= 2.0
                elem1.bolusAmount /= 2.0
            }
        }
        historyList.addAll(dummies)
        historyList.sortWith { lhs, rhs -> (rhs.timestamp - lhs.timestamp).toInt() }
        return historyList
    }

    private fun generateTDDMessage(historyList: MutableList<TotalDailyDose>, dummies: MutableList<TotalDailyDose>): String {
        val profile = profileFunction.getProfile() ?: return "No profile loaded :("
        if (historyList.isEmpty()) {
            return "No history data!"
        }
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        var message = ""
        val refTDD = profile.baseBasalSum() * 2
        if (df.format(Date(historyList[0].timestamp)) == df.format(Date())) {
            val tdd = historyList[0].total
            historyList.removeAt(0)
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n"
            message += "\n"
        }
        var weighted03 = 0.0
        var weighted05 = 0.0
        var weighted07 = 0.0
        historyList.reverse()
        for ((i, record) in historyList.withIndex()) {
            val tdd = record.total
            if (i == 0) {
                weighted03 = tdd
                weighted05 = tdd
                weighted07 = tdd
            } else {
                weighted07 = weighted07 * 0.3 + tdd * 0.7
                weighted05 = weighted05 * 0.5 + tdd * 0.5
                weighted03 = weighted03 * 0.7 + tdd * 0.3
            }
        }
        message += "weighted:\n"
        message += "0.3: " + DecimalFormatter.to2Decimal(weighted03) + "U " + (DecimalFormatter.to0Decimal(100 * weighted03 / refTDD) + "%") + "\n"
        message += "0.5: " + DecimalFormatter.to2Decimal(weighted05) + "U " + (DecimalFormatter.to0Decimal(100 * weighted05 / refTDD) + "%") + "\n"
        message += "0.7: " + DecimalFormatter.to2Decimal(weighted07) + "U " + (DecimalFormatter.to0Decimal(100 * weighted07 / refTDD) + "%") + "\n"
        message += "\n"
        historyList.reverse()
        //add TDDs:
        for (record in historyList) {
            val tdd = record.total
            message += df.format(Date(record.timestamp)) + " " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + (if (dummies.contains(
                    record
                )
            ) "x" else "") + "\n"
        }
        return message
    }

    private fun generateStatusString(profile: Profile?, currentBasal: String, iobSum: String, iobDetail: String, bgiString: String): String {
        var status = ""
        profile ?: return rh.gs(R.string.noprofile)
        if (!(loop as PluginBase).isEnabled()) status += rh.gs(R.string.disabledloop) + "\n"

        val iobString =
            if (sp.getBoolean(R.string.key_wear_detailediob, false)) "$iobSum $iobDetail"
            else iobSum + "U"

        status += "$currentBasal $iobString"

        //add BGI if shown, otherwise return
        if (sp.getBoolean(R.string.key_wear_showbgi, false)) status += " $bgiString"
        return status
    }

    private fun doTempTarget(command: EventData.ActionTempTargetConfirmed) {
        if (command.duration != 0) {
            disposable += repository.runTransactionForResult(
                InsertAndCancelCurrentTemporaryTargetTransaction(
                    timestamp = System.currentTimeMillis(),
                    duration = TimeUnit.MINUTES.toMillis(command.duration.toLong()),
                    reason = TemporaryTarget.Reason.WEAR,
                    lowTarget = Profile.toMgdl(command.low, profileFunction.getUnits()),
                    highTarget = Profile.toMgdl(command.high, profileFunction.getUnits())
                )
            ).subscribe({ result ->
                            result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                            result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                        }, {
                            aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                        })
            uel.log(
                UserEntry.Action.TT, UserEntry.Sources.Wear,
                ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.WEAR),
                ValueWithUnit.fromGlucoseUnit(command.low, profileFunction.getUnits().asText),
                ValueWithUnit.fromGlucoseUnit(command.high, profileFunction.getUnits().asText).takeIf { command.low != command.high },
                ValueWithUnit.Minute(command.duration)
            )
        } else {
            disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(System.currentTimeMillis()))
                .subscribe({ result ->
                               result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                           }, {
                               aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                           })
            uel.log(
                UserEntry.Action.CANCEL_TT, UserEntry.Sources.Wear,
                ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.WEAR)
            )
        }
    }

    private fun doBolus(amount: Double, carbs: Int, carbsTime: Long?, carbsDuration: Int) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = amount
        detailedBolusInfo.carbs = carbs.toDouble()
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.NORMAL
        detailedBolusInfo.carbsTimestamp = carbsTime
        detailedBolusInfo.carbsDuration = T.hours(carbsDuration.toLong()).msecs()
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            val action = when {
                amount == 0.0     -> UserEntry.Action.CARBS
                carbs == 0        -> UserEntry.Action.BOLUS
                carbsDuration > 0 -> UserEntry.Action.EXTENDED_CARBS
                else              -> UserEntry.Action.TREATMENT
            }
            uel.log(action, UserEntry.Sources.Wear,
                    ValueWithUnit.Insulin(amount).takeIf { amount != 0.0 },
                    ValueWithUnit.Gram(carbs).takeIf { carbs != 0 },
                    ValueWithUnit.Hour(carbsDuration).takeIf { carbsDuration != 0 })
            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                override fun run() {
                    if (!result.success)
                        sendError(rh.gs(R.string.treatmentdeliveryerror) + "\n" + result.comment)
                }
            })
        }
    }

    private fun doFillBolus(amount: Double) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = amount
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.PRIMING
        uel.log(
            UserEntry.Action.PRIME_BOLUS, UserEntry.Sources.Wear,
            ValueWithUnit.Insulin(amount).takeIf { amount != 0.0 })
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                if (!result.success) {
                    sendError(rh.gs(R.string.treatmentdeliveryerror) + "\n" + result.comment)
                }
            }
        })
    }

    private fun doECarbs(carbs: Int, carbsTime: Long, duration: Int) {
        uel.log(if (duration == 0) UserEntry.Action.CARBS else UserEntry.Action.EXTENDED_CARBS, UserEntry.Sources.Wear,
                ValueWithUnit.Timestamp(carbsTime),
                ValueWithUnit.Gram(carbs),
                ValueWithUnit.Hour(duration).takeIf { duration != 0 })
        doBolus(0.0, carbs, carbsTime, duration)
    }

    private fun doProfileSwitch(command: EventData.ActionProfileSwitchConfirmed) {
        //check for validity
        if (command.percentage < Constants.CPP_MIN_PERCENTAGE || command.percentage > Constants.CPP_MAX_PERCENTAGE)
            return
        if (command.timeShift < 0 || command.timeShift > 23)
            return
        profileFunction.getProfile() ?: return
        //send profile to pump
        uel.log(
            UserEntry.Action.PROFILE_SWITCH, UserEntry.Sources.Wear,
            ValueWithUnit.Percent(command.percentage),
            ValueWithUnit.Hour(command.timeShift).takeIf { command.timeShift != 0 })
        profileFunction.createProfileSwitch(0, command.percentage, command.timeShift)
    }

    @Synchronized private fun sendError(errorMessage: String) {
        rxBus.send(EventMobileToWear(EventData.ConfirmAction(rh.gs(R.string.error), errorMessage, returnCommand = EventData.Error(dateUtil.now())))) // ignore return path
    }
}
