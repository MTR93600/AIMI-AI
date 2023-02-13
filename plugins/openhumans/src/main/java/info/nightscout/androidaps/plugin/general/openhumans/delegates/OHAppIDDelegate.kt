package info.nightscout.androidaps.plugin.general.openhumans.delegates

import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHAppIDDelegate @Inject internal constructor(
    private val sp: SP
) {
    private var value: UUID? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): UUID {
        if (value == null) {
            val saved = sp.getStringOrNull("openhumans_appid", null)
            if (saved == null) {
                val generated = UUID.randomUUID()
                value = generated
                sp.putString("openhumans_appid", generated.toString())
            } else {
                value = UUID.fromString(saved)
            }
        }
        return value!!
    }
}