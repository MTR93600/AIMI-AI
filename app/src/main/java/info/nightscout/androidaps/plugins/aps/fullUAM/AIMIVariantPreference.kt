package info.nightscout.androidaps.plugins.aps.fullUAM

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DropDownPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
class AIMIVariantPreference(context: Context, attrs: AttributeSet?)
    : DropDownPreference(context, attrs) {

    @Inject lateinit var sp: SP

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)

        val entries = Vector<CharSequence>()
        entries.add(AIMIDefaults.variant)

        val list = context.assets.list("AIMI/")
        list?.forEach {
            if (!it.endsWith(".js"))
                entries.add(it)
        }

        entryValues = entries.toTypedArray()
        setEntries(entries.toTypedArray())
        setDefaultValue(sp.getString(R.string.key_aimi_variant, AIMIDefaults.variant))
    }
}