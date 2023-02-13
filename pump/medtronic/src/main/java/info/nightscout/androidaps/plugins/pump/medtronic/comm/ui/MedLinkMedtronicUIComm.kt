package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble.data.MedLinkPumpMessage
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedLinkMedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedLinkMedtronicUtil
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

/**
 * Created by Dirceu on 25/09/20.
 * copied from [MedtronicUIComm]
 */
class MedLinkMedtronicUIComm(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val medtronicUtil: MedLinkMedtronicUtil,
    private val medtronicUIPostprocessor: MedLinkMedtronicUIPostprocessor,
    private val medtronicCommunicationManager: MedLinkMedtronicCommunicationManager
) {

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
    @Synchronized fun <B,C,D,E>executeCommandCP(pumpMessage1: MedLinkPumpMessage<B,C>, pumpMessage2: MedLinkPumpMessage<D, E>) {
         this.executeCommandCP(pumpMessage1)
        this.executeCommandCP(pumpMessage2)

    }

    @Synchronized fun <B,C>executeCommandCP(pumpMessage: MedLinkPumpMessage<B,C>): MedLinkMedtronicUITaskCp {
        aapsLogger.info(LTag.PUMP, "Execute Command: " + pumpMessage.firstCommand().code)
        val task = MedLinkMedtronicUITaskCp(injector, aapsLogger)
        medtronicUtil.currentCommand = pumpMessage.firstCommand()
        task.execute(medtronicCommunicationManager, medtronicUIPostprocessor,pumpMessage)
        if (!task.isReceived) {
            aapsLogger.warn(LTag.PUMP, "Reply not received for " + pumpMessage.firstCommand())
        }

//        task.postProcess(medtronicUIPostprocessor);
        return task
    }

    //    public synchronized MedLinkMedtronicUITask executeCommand(MedLinkMedtronicCommandType commandType, Object... parameters) {
    //
    //        aapsLogger.info(LTag.PUMP, "Execute Command: " + commandType.name());
    //
    //        MedLinkMedtronicUITask task = new MedLinkMedtronicUITask(injector, commandType, parameters);
    //
    //        medtronicUtil.setCurrentCommand(commandType.command);
    //
    //        task.execute(medtronicCommunicationManager, medtronicUIPostprocessor);
    //
    //        if (!task.isReceived()) {
    //            aapsLogger.warn(LTag.PUMP, "Reply not received for " + commandType);
    //        }
    //
    ////        task.postProcess(medtronicUIPostprocessor);
    //        return task;
    //
    //    }
    val invalidResponsesCount: Int
        get() = medtronicCommunicationManager.notConnectedCount
}