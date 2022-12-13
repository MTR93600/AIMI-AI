package info.nightscout.plugins.sync.nsclient.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.nsclient.StoreDataForDb
import info.nightscout.plugins.sync.nsclient.data.NSMbg
import info.nightscout.plugins.sync.nsclient.extensions.therapyEventFromNsMbg
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class NSClientMbgWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var sp: SP
    @Inject lateinit var config: Config
    @Inject lateinit var storeDataForDb: StoreDataForDb

    override fun doWorkAndLog(): Result {
        val ret = Result.success()

        val acceptNSData = sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_therapy_events, false) || config.NSCLIENT
        if (!acceptNSData) return Result.success(workDataOf("Result" to "Sync not enabled"))

        val mbgArray = dataWorkerStorage.pickupJSONArray(inputData.getLong(DataWorkerStorage.STORE_KEY, -1))
            ?: return Result.failure(workDataOf("Error" to "missing input data"))
        for (i in 0 until mbgArray.length()) {
            val nsMbg = NSMbg(mbgArray.getJSONObject(i))
            if (!nsMbg.isValid()) continue
            storeDataForDb.therapyEvents.add(therapyEventFromNsMbg(nsMbg))
        }
        // storeDataForDb.storeTreatmentsToDb() don't do this. It will be stored along with other treatments
        return ret
    }
}