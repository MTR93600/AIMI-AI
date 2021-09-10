package info.nightscout.androidaps.plugin.general.openhumans.delegates

import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHCounterDelegate @Inject internal constructor(
    private val sp: SP
) {
    private var value = try {
            sp.getLong("openhumans_counter", 1)
        } catch(e:Exception) {
            sp.putLong("openhumans_counter", 1)
            1L
        }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
        this.value = value
        sp.putLong("openhumans_counter", value)
    }
}