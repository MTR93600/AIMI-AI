package info.nightscout.plugins.sync.tidepool.comm

import info.nightscout.plugins.sync.tidepool.messages.AuthReplyMessage
import info.nightscout.plugins.sync.tidepool.messages.DatasetReplyMessage
import okhttp3.Headers

class Session(
    val authHeader: String?,
    private val sessionTokenHeader: String,
    val service: TidepoolApiService?
) {

    internal var token: String? = null
    internal var authReply: AuthReplyMessage? = null
    internal var datasetReply: DatasetReplyMessage? = null
    internal var start: Long = 0
    internal var end: Long = 0

    @Volatile
    internal var iterations: Int = 0

    fun populateHeaders(headers: Headers) {
        if (this.token == null) {
            this.token = headers[sessionTokenHeader]
        }
    }

    fun populateBody(obj: Any?) {
        when (obj) {
            is AuthReplyMessage    -> authReply = obj

            is List<*>             ->
                (obj as? List<*>?)?.getOrNull(0)?.let {
                    if (it is DatasetReplyMessage) datasetReply = it
                }

            is DatasetReplyMessage -> datasetReply = obj
        }
    }
}
