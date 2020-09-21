package info.nightscout.androidaps.plugins.pump.omnipod.driver.manager;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.EnumSet;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.AcknowledgeAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.AssignAddressAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.CancelDeliveryAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.ConfigureAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.DeactivatePodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.GetPodInfoAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.GetStatusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.InsertCannulaAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.PrimeAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.SetBasalScheduleAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.SetTempBasalAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.SetupPodAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.PrimeService;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.CancelDeliveryCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.BeepType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CommandFailedAfterChangingDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.CommunicationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.DeliveryStatusVerificationFailedException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalDeliveryStatusException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPacketTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.NonceOutOfSyncException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.OmnipodException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.PodFaultException;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.SingleSubject;

public class OmnipodManager {
    private static final int ACTION_VERIFICATION_TRIES = 3;

    private final OmnipodRileyLinkCommunicationManager communicationService;
    private PodStateManager podStateManager;

    private ActiveBolusData activeBolusData;
    private SingleSubject<Boolean> bolusCommandExecutionSubject;

    private final Object bolusDataMutex = new Object();

    private AAPSLogger aapsLogger;

    public OmnipodManager(AAPSLogger aapsLogger,
                          SP sp,
                          OmnipodRileyLinkCommunicationManager communicationService,
                          PodStateManager podStateManager) {
        if (communicationService == null) {
            throw new IllegalArgumentException("Communication service cannot be null");
        }
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod State Manager can not be null");
        }
        this.aapsLogger = aapsLogger;
        this.communicationService = communicationService;

        this.podStateManager = podStateManager;
    }

    public synchronized Single<SetupActionResult> pairAndPrime() {
        logStartingCommandExecution("pairAndPrime");

        try {
            if (!podStateManager.isPodInitialized() || podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PAIRING_COMPLETED)) {
                // Always send both 0x07 and 0x03 on retries
                try {
                    communicationService.executeAction(
                            new AssignAddressAction(podStateManager));
                } catch (IllegalPacketTypeException ex) {
                    if (ex.getActual() == PacketType.ACK && podStateManager.isPodInitialized()) {
                        // When we already assigned the address before, it's possible to only get an ACK here
                        aapsLogger.debug("Received ACK instead of response in AssignAddressAction. Ignoring because we already assigned the address successfully");
                    } else {
                        throw ex;
                    }
                }

                try {
                    communicationService.executeAction(new SetupPodAction(podStateManager));
                } catch (IllegalPacketTypeException ex) {
                    if (PacketType.ACK.equals(ex.getActual())) {
                        // TODO is this true for the SetupPodCommand?
                        // Pod is already configured
                        aapsLogger.debug("Received ACK instead of response in SetupPodAction. Ignoring");
                    }
                }
            } else if (podStateManager.getPodProgressStatus().isAfter(PodProgressStatus.PRIMING)) {
                throw new IllegalPodProgressException(PodProgressStatus.PAIRING_COMPLETED, podStateManager.getPodProgressStatus());
            }

            // Make sure we have an up to date PodProgressStatus
            getPodStatus();

            communicationService.executeAction(new PrimeAction(new PrimeService(), podStateManager));
        } finally {
            logCommandExecutionFinished("pairAndPrime");
        }

        long delayInSeconds = calculateBolusDuration(OmnipodConstants.POD_PRIME_BOLUS_UNITS, OmnipodConstants.POD_PRIMING_DELIVERY_RATE).getStandardSeconds();

        return Single.timer(delayInSeconds, TimeUnit.SECONDS) //
                .map(o -> verifySetupAction(PodProgressStatus.PRIMING_COMPLETED)) //
                .observeOn(Schedulers.io());
    }

    public synchronized Single<SetupActionResult> insertCannula(
            BasalSchedule basalSchedule, Duration expirationReminderTimeBeforeShutdown, Integer lowReservoirAlertUnits) {
        if (!podStateManager.isPodInitialized() || podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
            throw new IllegalPodProgressException(PodProgressStatus.PRIMING_COMPLETED, !podStateManager.isPodInitialized() ? null : podStateManager.getPodProgressStatus());
        }

        // Make sure we have the latest PodProgressStatus
        getPodStatus();

        if (podStateManager.getPodProgressStatus().isAfter(PodProgressStatus.INSERTING_CANNULA)) {
            throw new IllegalPodProgressException(PodProgressStatus.PRIMING_COMPLETED, podStateManager.getPodProgressStatus());
        }

        logStartingCommandExecution("insertCannula [basalSchedule=" + basalSchedule + "]");

        try {
            communicationService.executeAction(new InsertCannulaAction(podStateManager, basalSchedule, expirationReminderTimeBeforeShutdown, lowReservoirAlertUnits));
        } finally {
            logCommandExecutionFinished("insertCannula");
        }

        long delayInSeconds = calculateBolusDuration(OmnipodConstants.POD_CANNULA_INSERTION_BOLUS_UNITS, OmnipodConstants.POD_CANNULA_INSERTION_DELIVERY_RATE).getStandardSeconds();

        return Single.timer(delayInSeconds, TimeUnit.SECONDS) //
                .map(o -> verifySetupAction(PodProgressStatus.ABOVE_FIFTY_UNITS)) //
                .observeOn(Schedulers.io());
    }

    public synchronized StatusResponse getPodStatus() {
        if (!podStateManager.isPodInitialized()) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, null);
        }

        logStartingCommandExecution("getPodStatus");

        try {
            return communicationService.executeAction(new GetStatusAction(podStateManager));
        } finally {
            logCommandExecutionFinished("getPodStatus");
        }
    }

    public synchronized PodInfoResponse getPodInfo(PodInfoType podInfoType) {
        assertReadyForDelivery();

        logStartingCommandExecution("getPodInfo");

        try {
            return communicationService.executeAction(new GetPodInfoAction(podStateManager, podInfoType));
        } finally {
            logCommandExecutionFinished("getPodInfo");
        }
    }

    public synchronized StatusResponse configureAlerts(List<AlertConfiguration> alertConfigurations) {
        assertReadyForDelivery();
        logStartingCommandExecution("configureAlerts");
        try {
            StatusResponse statusResponse = executeAndVerify(() -> communicationService.executeAction(new ConfigureAlertsAction(podStateManager, alertConfigurations)));
            ConfigureAlertsAction.updateConfiguredAlerts(podStateManager, alertConfigurations);
            return statusResponse;
        } finally {
            logCommandExecutionFinished("configureAlerts");
        }
    }

    public synchronized StatusResponse acknowledgeAlerts() {
        assertReadyForDelivery();

        logStartingCommandExecution("acknowledgeAlerts");

        try {
            return executeAndVerify(() -> communicationService.executeAction(new AcknowledgeAlertsAction(podStateManager, podStateManager.getActiveAlerts())));
        } finally {
            logCommandExecutionFinished("acknowledgeAlerts");
        }
    }

    // CAUTION: cancels all delivery
    // CAUTION: suspends and then resumes delivery. An OmnipodException[certainFailure=false] indicates that the pod is or might be suspended
    public synchronized void setBasalSchedule(BasalSchedule schedule, boolean acknowledgementBeep) {
        assertReadyForDelivery();

        logStartingCommandExecution("setBasalSchedule [basalSchedule=" + schedule + ", acknowledgementBeep=" + acknowledgementBeep + "]");

        try {
            boolean wasSuspended = podStateManager.isSuspended();
            if (!wasSuspended) {
                suspendDelivery(acknowledgementBeep);
            }

            // Store the new Basal schedule after successfully suspending delivery, so that if setting the Basal schedule fails,
            // And we later try to resume delivery, the new schedule is used
            podStateManager.setBasalSchedule(schedule);

            try {
                executeAndVerify(() -> communicationService.executeAction(new SetBasalScheduleAction(podStateManager, schedule,
                        false, podStateManager.getScheduleOffset(), acknowledgementBeep)));
            } catch (OmnipodException ex) {
                if (ex.isCertainFailure()) {
                    if (!wasSuspended) {
                        throw new CommandFailedAfterChangingDeliveryStatusException("Suspending delivery succeeded but setting the new basal schedule did not", ex);
                    }
                    throw ex;
                }

                // verifyDeliveryStatus will throw an exception if verification fails
                if (!verifyDeliveryStatus(DeliveryStatus.NORMAL, ex)) {
                    if (!wasSuspended) {
                        throw new CommandFailedAfterChangingDeliveryStatusException("Suspending delivery succeeded but setting the new basal schedule did not", ex);
                    }
                    ex.setCertainFailure(true);
                    throw ex;
                }
            }
        } finally {
            logCommandExecutionFinished("setBasalSchedule");
        }
    }

    // CAUTION: cancels temp basal and then sets new temp basal. An OmnipodException[certainFailure=false] indicates that the pod might have cancelled the previous temp basal, but did not set a new temp basal
    public synchronized void setTemporaryBasal(double rate, Duration duration, boolean acknowledgementBeep, boolean completionBeep) {
        assertReadyForDelivery();

        logStartingCommandExecution("setTemporaryBasal [rate=" + rate + ", duration=" + duration + ", acknowledgementBeep=" + acknowledgementBeep + ", completionBeep=" + completionBeep + "]");

        boolean cancelCurrentTbr = podStateManager.isTempBasalRunning();

        try {
            if (cancelCurrentTbr) {
                try {
                    cancelDelivery(EnumSet.of(DeliveryType.TEMP_BASAL), acknowledgementBeep);
                } catch (OmnipodException ex) {
                    if (ex.isCertainFailure()) {
                        throw ex;
                    }

                    // verifyDeliveryStatus will throw an exception if verification fails
                    if (!verifyDeliveryStatus(DeliveryStatus.NORMAL, ex)) {
                        ex.setCertainFailure(true);
                        throw ex;
                    }
                }
            }

            try {
                executeAndVerify(() -> communicationService.executeAction(new SetTempBasalAction(
                        podStateManager, rate, duration, acknowledgementBeep, completionBeep)));
                podStateManager.setTempBasal(DateTime.now().minus(OmnipodConstants.AVERAGE_TEMP_BASAL_COMMAND_COMMUNICATION_DURATION), rate, duration, true);
            } catch (OmnipodException ex) {
                if (ex.isCertainFailure()) {
                    if (cancelCurrentTbr) {
                        throw new CommandFailedAfterChangingDeliveryStatusException("Failed to set new TBR while cancelling old TBR succeeded", ex);
                    }
                    throw ex;
                }

                // verifyDeliveryStatus will throw an exception if verification fails
                try {
                    if (verifyDeliveryStatus(DeliveryStatus.TEMP_BASAL_RUNNING, ex)) {
                        podStateManager.setTempBasal(DateTime.now().minus(OmnipodConstants.AVERAGE_TEMP_BASAL_COMMAND_COMMUNICATION_DURATION), rate, duration, true);
                    } else {
                        if (cancelCurrentTbr) {
                            throw new CommandFailedAfterChangingDeliveryStatusException("Failed to set new TBR while cancelling old TBR succeeded", ex);
                        }

                        ex.setCertainFailure(true);
                        throw ex;
                    }
                } catch (CommandFailedAfterChangingDeliveryStatusException ex2) {
                    // Don't set temp basal in Pod State for this Exception
                    throw ex2;
                } catch (OmnipodException ex2) {
                    if (!ex2.isCertainFailure()) {
                        // We're not sure that setting the new TBR failed, so we assume that it succeeded
                        // If it didn't, PodStateManager.updateFromResponse() will fix the state
                        // upon receiving the next StatusResponse
                        podStateManager.setTempBasal(DateTime.now().minus(OmnipodConstants.AVERAGE_TEMP_BASAL_COMMAND_COMMUNICATION_DURATION), rate, duration, false);
                    }
                    throw ex2;
                }
            }
        } finally {
            logCommandExecutionFinished("setTemporaryBasal");
        }
    }

    public synchronized void cancelTemporaryBasal(boolean acknowledgementBeep) {
        cancelDelivery(EnumSet.of(DeliveryType.TEMP_BASAL), acknowledgementBeep);
    }

    private synchronized StatusResponse cancelDelivery(EnumSet<DeliveryType> deliveryTypes, boolean acknowledgementBeep) {
        assertReadyForDelivery();

        logStartingCommandExecution("cancelDelivery [deliveryTypes=" + deliveryTypes + ", acknowledgementBeep=" + acknowledgementBeep + "]");

        try {
            return executeAndVerify(() -> {
                StatusResponse statusResponse = communicationService.executeAction(new CancelDeliveryAction(podStateManager, deliveryTypes, acknowledgementBeep));
                aapsLogger.info(LTag.PUMPCOMM, "Status response after cancel delivery[types={}]: {}", deliveryTypes.toString(), statusResponse.toString());
                return statusResponse;
            });
        } finally {
            logCommandExecutionFinished("cancelDelivery");
        }
    }

    // Returns a SingleSubject that returns when the bolus has finished.
    // When a bolus is cancelled, it will return after cancellation and report the estimated units delivered
    // Only throws OmnipodException[certainFailure=false]
    public synchronized BolusCommandResult bolus(Double units, boolean acknowledgementBeep, boolean completionBeep, BiConsumer<Double, Integer> progressIndicationConsumer) {
        assertReadyForDelivery();

        logStartingCommandExecution("bolus [units=" + units + ", acknowledgementBeep=" + acknowledgementBeep + ", completionBeep=" + completionBeep + "]");

        bolusCommandExecutionSubject = SingleSubject.create();

        CommandDeliveryStatus commandDeliveryStatus = CommandDeliveryStatus.SUCCESS;

        try {
            executeAndVerify(() -> communicationService.executeAction(new BolusAction(podStateManager, units, acknowledgementBeep, completionBeep)));
        } catch (OmnipodException ex) {
            if (ex.isCertainFailure()) {
                bolusCommandExecutionSubject.onSuccess(false);
                bolusCommandExecutionSubject = null;
                throw ex;
            }

            // Catch uncertain exceptions as we still want to report bolus progress indication
            aapsLogger.error(LTag.PUMPCOMM, "Caught exception[certainFailure=false] in bolus", ex);
            commandDeliveryStatus = CommandDeliveryStatus.UNCERTAIN_FAILURE;
        }

        DateTime estimatedBolusStartDate = DateTime.now().minus(OmnipodConstants.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION);
        Duration estimatedBolusDuration = calculateBolusDuration(units, OmnipodConstants.POD_BOLUS_DELIVERY_RATE);
        Duration estimatedRemainingBolusDuration = estimatedBolusDuration.minus(OmnipodConstants.AVERAGE_BOLUS_COMMAND_COMMUNICATION_DURATION);

        podStateManager.setLastBolus(estimatedBolusStartDate, units, estimatedBolusDuration, commandDeliveryStatus == CommandDeliveryStatus.SUCCESS);

        CompositeDisposable disposables = new CompositeDisposable();

        if (progressIndicationConsumer != null) {

            int numberOfProgressReports = Math.max(20, Math.min(100, (int) Math.ceil(units) * 10));
            long progressReportInterval = estimatedRemainingBolusDuration.getMillis() / numberOfProgressReports;

            disposables.add(Flowable.intervalRange(0, numberOfProgressReports + 1, 0, progressReportInterval, TimeUnit.MILLISECONDS) //
                    .observeOn(Schedulers.io()) //
                    .subscribe(count -> {
                        int percentage = (int) ((double) count / numberOfProgressReports * 100);
                        double estimatedUnitsDelivered = activeBolusData == null ? 0 : activeBolusData.estimateUnitsDelivered();
                        progressIndicationConsumer.accept(estimatedUnitsDelivered, percentage);
                    }));
        }

        SingleSubject<BolusDeliveryResult> bolusCompletionSubject = SingleSubject.create();

        synchronized (bolusDataMutex) {
            activeBolusData = new ActiveBolusData(units, estimatedBolusStartDate, commandDeliveryStatus, bolusCompletionSubject, disposables);
        }

        // Return successful command execution AFTER storing activeBolusData
        //  Otherwise, hasActiveBolus() would return false and the caller would not cancel the bolus.
        bolusCommandExecutionSubject.onSuccess(true);
        bolusCommandExecutionSubject = null;

        disposables.add(Completable.complete() //
                .delay(estimatedRemainingBolusDuration.getMillis() + 250, TimeUnit.MILLISECONDS) //
                .observeOn(Schedulers.io()) //
                .doOnComplete(() -> {
                    synchronized (bolusDataMutex) {
                        double bolusNotDelivered = 0.0d;

                        for (int i = 0; i < ACTION_VERIFICATION_TRIES; i++) {
                            try {
                                // Retrieve a status response in order to update the pod state
                                StatusResponse statusResponse = getPodStatus();
                                if (statusResponse.getDeliveryStatus().isBolusing()) {
                                    throw new IllegalDeliveryStatusException(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
                                } else {
                                    break;
                                }
                            } catch (PodFaultException ex) {
                                // Subtract units not delivered in case of a Pod failure
                                bolusNotDelivered = ex.getFaultEvent().getBolusNotDelivered();

                                aapsLogger.debug(LTag.PUMPCOMM, "Caught PodFaultException in bolus completion verification", ex);
                                break;
                            } catch (Exception ex) {
                                aapsLogger.debug(LTag.PUMPCOMM, "Ignoring exception in bolus completion verification", ex);
                            }
                        }

                        if (hasActiveBolus()) {
                            activeBolusData.bolusCompletionSubject.onSuccess(new BolusDeliveryResult(units - bolusNotDelivered));
                            activeBolusData = null;
                        }
                    }
                })
                .subscribe());

        logCommandExecutionFinished("bolus");

        return new BolusCommandResult(commandDeliveryStatus, bolusCompletionSubject);
    }

    public synchronized void cancelBolus(boolean acknowledgementBeep) {
        assertReadyForDelivery();

        synchronized (bolusDataMutex) {
            if (activeBolusData == null) {
                throw new IllegalDeliveryStatusException(DeliveryStatus.BOLUS_IN_PROGRESS, podStateManager.getLastDeliveryStatus());
            }

            logStartingCommandExecution("cancelBolus [acknowledgementBeep=" + acknowledgementBeep + "]");

            try {
                StatusResponse statusResponse = cancelDelivery(EnumSet.of(DeliveryType.BOLUS), acknowledgementBeep);
                discardActiveBolusData(statusResponse.getBolusNotDelivered());
            } catch (PodFaultException ex) {
                discardActiveBolusData(ex.getFaultEvent().getBolusNotDelivered());
                throw ex;
            } finally {
                logCommandExecutionFinished("cancelBolus");
            }
        }
    }

    private void discardActiveBolusData(double bolusNotDelivered) {
        synchronized (bolusDataMutex) {
            double unitsDelivered = activeBolusData.getUnits() - bolusNotDelivered;
            podStateManager.setLastBolus(activeBolusData.getStartDate(), unitsDelivered, new Duration(activeBolusData.getStartDate(), DateTime.now()), activeBolusData.getCommandDeliveryStatus() == CommandDeliveryStatus.SUCCESS);
            activeBolusData.getDisposables().dispose();
            activeBolusData.getBolusCompletionSubject().onSuccess(new BolusDeliveryResult(unitsDelivered));
            activeBolusData = null;
        }
    }

    public synchronized void suspendDelivery(boolean acknowledgementBeep) {
        assertReadyForDelivery();

        logStartingCommandExecution("suspendDelivery");

        try {
            cancelDelivery(EnumSet.allOf(DeliveryType.class), acknowledgementBeep);
        } catch (OmnipodException ex) {
            if (ex.isCertainFailure()) {
                throw ex;
            }

            // verifyDeliveryStatus will throw an exception if verification fails
            if (!verifyDeliveryStatus(DeliveryStatus.SUSPENDED, ex)) {
                ex.setCertainFailure(true);
                throw ex;
            }
        } finally {
            logCommandExecutionFinished("suspendDelivery");
        }
    }

    // CAUTION: cancels all delivery
    // CAUTION: suspends and then resumes delivery. An OmnipodException[certainFailure=false] indicates that the pod is or might be suspended
    public synchronized void setTime(boolean acknowledgementBeeps) {
        assertReadyForDelivery();

        logStartingCommandExecution("setTime [acknowledgementBeeps=" + acknowledgementBeeps + "]");

        try {
            DateTimeZone oldTimeZone = podStateManager.getTimeZone();

            try {
                // Joda seems to cache the default time zone, so we use the JVM's
                DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getDefault()));
                podStateManager.setTimeZone(DateTimeZone.getDefault());

                setBasalSchedule(podStateManager.getBasalSchedule(), acknowledgementBeeps);
            } catch (OmnipodException ex) {
                podStateManager.setTimeZone(oldTimeZone);
                throw ex;
            }
        } finally {
            logCommandExecutionFinished("setTime");
        }
    }

    public synchronized void deactivatePod() {
        if (!podStateManager.isPodInitialized()) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, null);
        }

        logStartingCommandExecution("deactivatePod");

        // Try to get pulse log for diagnostics
        try {
            PodInfoResponse podInfoResponse = communicationService.executeAction(new GetPodInfoAction(podStateManager, PodInfoType.RECENT_PULSE_LOG));
            PodInfoRecentPulseLog pulseLogInfo = (PodInfoRecentPulseLog) podInfoResponse.getPodInfo();
            aapsLogger.info(LTag.PUMPCOMM, "Retrieved pulse log from the pod: {}", pulseLogInfo.toString());
        } catch (Exception ex) {
            aapsLogger.warn(LTag.PUMPCOMM, "Failed to retrieve pulse log from the pod", ex);
        }

        try {
            // Always send acknowledgement beeps here. Matches the PDM's behavior
            communicationService.executeAction(new DeactivatePodAction(podStateManager, true));
        } catch (PodFaultException ex) {
            aapsLogger.info(LTag.PUMPCOMM, "Ignoring PodFaultException in deactivatePod", ex);
        } finally {
            logCommandExecutionFinished("deactivatePod");
        }

        podStateManager.discardState();
    }

    public OmnipodRileyLinkCommunicationManager getCommunicationService() {
        return communicationService;
    }

    public DateTime getTime() {
        return podStateManager.getTime();
    }

    public boolean hasActiveBolus() {
        synchronized (bolusDataMutex) {
            return activeBolusData != null;
        }
    }

    public SingleSubject<Boolean> getBolusCommandExecutionSubject() {
        return bolusCommandExecutionSubject;
    }

    private boolean isPodRunning() {
        return podStateManager.isPodRunning();
    }

    // Only works for commands with nonce resyncable message blocks
    private StatusResponse executeAndVerify(Supplier<StatusResponse> supplier) {
        logStartingCommandExecution("verifyCommand");
        try {
            return supplier.get();
        } catch (Exception originalException) {
            if (isCertainFailure(originalException)) {
                throw originalException;
            } else {
                aapsLogger.warn(LTag.PUMPCOMM, "Caught exception in executeAndVerify. Verifying command by using cancel none command to verify nonce", originalException);

                try {
                    StatusResponse statusResponse = communicationService.sendCommand(StatusResponse.class, podStateManager,
                            new CancelDeliveryCommand(podStateManager.getCurrentNonce(), BeepType.NO_BEEP, DeliveryType.NONE), false);
                    aapsLogger.info(LTag.PUMPCOMM, "Command status resolved to SUCCESS. Status response after cancelDelivery[types=DeliveryType.NONE]: {}", statusResponse);

                    return statusResponse;
                } catch (NonceOutOfSyncException verificationException) {
                    aapsLogger.error(LTag.PUMPCOMM, "Command resolved to FAILURE (CERTAIN_FAILURE)", verificationException);

                    if (originalException instanceof OmnipodException) {
                        ((OmnipodException) originalException).setCertainFailure(true);
                        throw originalException;
                    } else {
                        OmnipodException newException = new CommunicationException(CommunicationException.Type.UNEXPECTED_EXCEPTION, originalException);
                        newException.setCertainFailure(true);
                        throw newException;
                    }
                } catch (Exception verificationException) {
                    aapsLogger.error(LTag.PUMPCOMM, "Command unresolved (UNCERTAIN_FAILURE)", verificationException);
                    throw originalException;
                }
            }
        } finally {
            logCommandExecutionFinished("verifyCommand");
        }
    }

    private void assertReadyForDelivery() {
        if (!isPodRunning()) {
            throw new IllegalPodProgressException(PodProgressStatus.ABOVE_FIFTY_UNITS, podStateManager.hasPodState() ? podStateManager.getPodProgressStatus() : null);
        }
    }

    private SetupActionResult verifySetupAction(PodProgressStatus expectedPodProgressStatus) {
        SetupActionResult result = null;
        for (int i = 0; ACTION_VERIFICATION_TRIES > i; i++) {
            try {
                StatusResponse statusResponse = getPodStatus();

                if (statusResponse.getPodProgressStatus().equals(expectedPodProgressStatus)) {
                    result = new SetupActionResult(SetupActionResult.ResultType.SUCCESS);
                    break;
                } else {
                    result = new SetupActionResult(SetupActionResult.ResultType.FAILURE) //
                            .podProgressStatus(statusResponse.getPodProgressStatus());
                    break;
                }
            } catch (Exception ex) {
                result = new SetupActionResult(SetupActionResult.ResultType.VERIFICATION_FAILURE) //
                        .exception(ex);
            }
        }
        return result;
    }

    /**
     * @param expectedStatus    expected delivery status
     * @param verificationCause the Exception causing us to verify the delivery status
     * @return true if the Pod's status matches the expected status, otherwise false
     * @throws DeliveryStatusVerificationFailedException in case reading the Pod status fails
     */
    private boolean verifyDeliveryStatus(DeliveryStatus expectedStatus, Throwable verificationCause) {
        aapsLogger.debug(LTag.PUMPCOMM, "Attempting to verify delivery status (expected={})", expectedStatus);
        for (int i = 0; 3 > i; i++) {
            try {
                StatusResponse podStatus = getPodStatus();
                aapsLogger.debug(LTag.PUMPCOMM, "Resolved delivery status (expected={}, actual={})", expectedStatus, podStatus.getDeliveryStatus());
                return podStatus.getDeliveryStatus().equals(expectedStatus);
            } catch (Exception ex) {
                aapsLogger.debug(LTag.PUMPCOMM, "Ignoring exception thrown in getPodStatus() during attempt to verify delivery status: {}: {}",
                        ex.getClass().getSimpleName(), ex.getMessage());
            }
        }
        aapsLogger.warn(LTag.PUMPCOMM, "Failed to verify delivery status");
        throw new DeliveryStatusVerificationFailedException(expectedStatus, verificationCause);
    }

    private void logStartingCommandExecution(String action) {
        aapsLogger.debug(LTag.PUMPCOMM, "Starting command execution for action: " + action);
    }

    private void logCommandExecutionFinished(String action) {
        aapsLogger.debug(LTag.PUMPCOMM, "Command execution finished for action: " + action);
    }

    private static Duration calculateBolusDuration(double units, double deliveryRate) {
        // TODO take current (temp) basal into account
        //  Be aware that the Pod possibly doesn't have a Basal Schedule yet
        return Duration.standardSeconds((long) Math.ceil(units / deliveryRate));
    }

    public static Duration calculateBolusDuration(double units) {
        return calculateBolusDuration(units, OmnipodConstants.POD_BOLUS_DELIVERY_RATE);
    }

    public static boolean isCertainFailure(Exception ex) {
        return ex instanceof OmnipodException && ((OmnipodException) ex).isCertainFailure();
    }

    public static class BolusCommandResult {
        private final CommandDeliveryStatus commandDeliveryStatus;
        private final SingleSubject<BolusDeliveryResult> deliveryResultSubject;

        BolusCommandResult(CommandDeliveryStatus commandDeliveryStatus, SingleSubject<BolusDeliveryResult> deliveryResultSubject) {
            this.commandDeliveryStatus = commandDeliveryStatus;
            this.deliveryResultSubject = deliveryResultSubject;
        }

        public CommandDeliveryStatus getCommandDeliveryStatus() {
            return commandDeliveryStatus;
        }

        public SingleSubject<BolusDeliveryResult> getDeliveryResultSubject() {
            return deliveryResultSubject;
        }
    }

    public static class BolusDeliveryResult {
        private final double unitsDelivered;

        BolusDeliveryResult(double unitsDelivered) {
            this.unitsDelivered = unitsDelivered;
        }

        public double getUnitsDelivered() {
            return unitsDelivered;
        }
    }

    public enum CommandDeliveryStatus {
        SUCCESS,
        CERTAIN_FAILURE,
        UNCERTAIN_FAILURE
    }

    private static class ActiveBolusData {
        private final double units;
        private final DateTime startDate;
        private final CommandDeliveryStatus commandDeliveryStatus;
        private final SingleSubject<BolusDeliveryResult> bolusCompletionSubject;
        private final CompositeDisposable disposables;

        private ActiveBolusData(double units, DateTime startDate, CommandDeliveryStatus commandDeliveryStatus, SingleSubject<BolusDeliveryResult> bolusCompletionSubject, CompositeDisposable disposables) {
            this.units = units;
            this.startDate = startDate;
            this.commandDeliveryStatus = commandDeliveryStatus;
            this.bolusCompletionSubject = bolusCompletionSubject;
            this.disposables = disposables;
        }

        double getUnits() {
            return units;
        }

        DateTime getStartDate() {
            return startDate;
        }

        CommandDeliveryStatus getCommandDeliveryStatus() {
            return commandDeliveryStatus;
        }

        CompositeDisposable getDisposables() {
            return disposables;
        }

        SingleSubject<BolusDeliveryResult> getBolusCompletionSubject() {
            return bolusCompletionSubject;
        }

        double estimateUnitsDelivered() {
            long elapsedMillis = new Duration(startDate, DateTime.now()).getMillis();
            long totalDurationMillis = (long) (units / OmnipodConstants.POD_BOLUS_DELIVERY_RATE * 1000);
            double factor = (double) elapsedMillis / totalDurationMillis;
            double estimatedUnits = Math.min(1D, factor) * units;

            int roundingDivisor = (int) (1 / OmnipodConstants.POD_PULSE_SIZE);
            return (double) Math.round(estimatedUnits * roundingDivisor) / roundingDivisor;
        }
    }
}
