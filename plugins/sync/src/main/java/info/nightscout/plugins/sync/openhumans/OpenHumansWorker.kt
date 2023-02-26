package info.nightscout.plugins.sync.openhumans

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

import javax.inject.Inject

class OpenHumansWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    init {
        (applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    @Inject
    lateinit var logger: AAPSLogger

    @Inject
    lateinit var openHumansUploader: OpenHumansUploaderPlugin

    override suspend fun doWork(): Result {
        return try {
            logger.info(LTag.OHUPLOADER, "Starting upload")
            setForeground(openHumansUploader.createForegroundInfo(id))
            openHumansUploader.uploadData()
            logger.info(LTag.OHUPLOADER, "Upload finished")
            Result.success()
        } catch (e: Exception) {
            logger.error(LTag.OHUPLOADER, "OH Uploader failed", e)
            Result.failure()
        }
    }
}