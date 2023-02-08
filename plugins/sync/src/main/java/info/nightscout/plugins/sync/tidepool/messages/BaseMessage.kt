package info.nightscout.plugins.sync.tidepool.messages

import info.nightscout.plugins.sync.tidepool.utils.GsonInstance
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

open class BaseMessage {
    private fun toS(): String {
        return GsonInstance.defaultGsonInstance().toJson(this) ?: "null"
    }

    fun getBody(): RequestBody {
        return this.toS().toRequestBody("application/json".toMediaTypeOrNull())
    }

}