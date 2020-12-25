package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedLinkMedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil;

/**
 * Created by Dirceu on 25/09/20.
 * copied from {@link MedtronicUIComm}
 */
public class MedLinkMedtronicUIComm {
    private final HasAndroidInjector injector;
    private final AAPSLogger aapsLogger;
    private final MedLinkMedtronicUtil medtronicUtil;
    private final MedLinkMedtronicCommunicationManager medtronicCommunicationManager;
    private final MedLinkMedtronicUIPostprocessor medtronicUIPostprocessor;

    public MedLinkMedtronicUIComm(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            MedLinkMedtronicUtil medtronicUtil,
            MedLinkMedtronicUIPostprocessor medtronicUIPostprocessor,
            MedLinkMedtronicCommunicationManager medtronicCommunicationManager
    ) {
        this.injector = injector;
        this.aapsLogger = aapsLogger;
        this.medtronicUtil = medtronicUtil;
        this.medtronicUIPostprocessor = medtronicUIPostprocessor;
        this.medtronicCommunicationManager = medtronicCommunicationManager;
    }
//
//    public synchronized MedLinkMedtronicUITaskCp executeCommand(MedLinkCommandType commandType, Object... parameters) {
//        aapsLogger.info(LTag.PUMP, "Execute Command: " + commandType.name());
//
//        MedLinkMedtronicUITaskCp task = new MedLinkMedtronicUITaskCp(injector, commandType, parameters);
//
//        medtronicUtil.setCurrentCommand(commandType);
//
//        task.execute(medtronicCommunicationManager);
//
//        if (!task.isReceived()) {
//            aapsLogger.warn(LTag.PUMP, "Reply not received for " + commandType);
//        }
//
//        task.postProcess(medtronicUIPostprocessor);
//
//        return task;
//
//    }
    public synchronized MedLinkMedtronicUITask executeCommand(MedLinkMedtronicCommandType commandType, Object... parameters) {

        aapsLogger.info(LTag.PUMP, "Execute Command: " + commandType.name());

        MedLinkMedtronicUITask task = new MedLinkMedtronicUITask(injector, commandType, parameters);

        medtronicUtil.setCurrentCommand(commandType.command);

        task.execute(medtronicCommunicationManager, medtronicUIPostprocessor);

        if (!task.isReceived()) {
            aapsLogger.warn(LTag.PUMP, "Reply not received for " + commandType);
        }

//        task.postProcess(medtronicUIPostprocessor);
        return task;

    }

    public int getInvalidResponsesCount() {
        return medtronicCommunicationManager.getNotConnectedCount();
    }
}
