package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataWorker @Inject constructor(
    private val context: Context
) {

    private val store = HashMap<Long, Any>()
    private var counter = 0L
    private val jobGroupName = "data"

    @Synchronized private fun store(value: Any): Long {
        store[counter] = value
        return counter++
    }

    @Synchronized fun pickupBundle(key: Long): Bundle? {
        val value = store[key]
        store.remove(key)
        return value as Bundle?
    }

    @Synchronized fun pickupObject(key: Long): Any? {
        val value = store[key]
        store.remove(key)
        return value
    }

    @Synchronized fun pickupString(key: Long): String? {
        val value = store[key]
        store.remove(key)
        return value as String?
    }

    @Synchronized fun pickupJSONArray(key: Long): JSONArray? {
        val value = store[key]
        store.remove(key)
        return value as JSONArray?
    }

    @Synchronized fun pickupJSONObject(key: Long): JSONObject? {
        val value = store[key]
        store.remove(key)
        return value as JSONObject?
    }

    fun storeInputData(value: Any, intent: Intent? = null) =
        Data.Builder()
            .putLong(STORE_KEY, store(value))
            .putString(ACTION_KEY, intent?.action).build()

    fun enqueue(request: OneTimeWorkRequest) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(jobGroupName, ExistingWorkPolicy.APPEND_OR_REPLACE, request)

    }

    companion object {

        const val STORE_KEY = "storeKey"
        const val ACTION_KEY = "action"
    }

}