package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits
import org.json.JSONObject

data class NSEffectiveProfileSwitch(
    override val date: Long,
    override val device: String? = null,
    override val identifier: String? = null,
    override val units: NsUnits? = null,
    override val srvModified: Long? = null,
    override val srvCreated: Long? = null,
    override val utcOffset: Long,
    override val subject: String? = null ,
    override var isReadOnly: Boolean = false,
    override val isValid: Boolean,
    override val eventType: EventType,
    override val notes: String?,
    override val pumpId: Long?,
    override val endId: Long?,
    override val pumpType: String?,
    override val pumpSerial: String?,
    override var app: String? = null,
    val profileJson: JSONObject,
    val originalProfileName: String,
    val originalCustomizedName: String,
    val originalTimeshift: Long,
    val originalPercentage: Int,
    val originalDuration: Long,
    val originalEnd: Long

) : NSTreatment