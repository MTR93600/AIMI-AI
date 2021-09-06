package info.nightscout.androidaps.plugin.general.openhumans

internal data class OpenHumansState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val projectMemberId: String,
    val uploadOffset: Long
)
