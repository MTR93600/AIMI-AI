package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.logging.LTag
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class LoadLastModificationWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Inject lateinit var rxBus: RxBus

    override suspend fun doWorkAndLog(): Result {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return Result.failure(workDataOf("Error" to "AndroidClient is null"))

        try {
            val lm = nsAndroidClient.getLastModified()
            nsClientV3Plugin.newestDataOnServer = lm
            aapsLogger.debug(LTag.NSCLIENT, "LAST MODIFIED: ${nsClientV3Plugin.newestDataOnServer}")
        } catch (error: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "Error: ", error)
            rxBus.send(EventNSClientNewLog("ERROR", error.localizedMessage))
            return Result.failure(workDataOf("Error" to error.localizedMessage))
        }
        return Result.success()
    }
}