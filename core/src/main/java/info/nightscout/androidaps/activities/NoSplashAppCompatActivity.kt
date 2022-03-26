package info.nightscout.androidaps.activities

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.events.EventThemeSwitch
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.androidaps.utils.locale.LocaleHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject


open class NoSplashAppCompatActivity : DaggerAppCompatActivityWithResult() {
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus

    private val compositeDisposable = CompositeDisposable()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(ThemeUtil.getThemeId(sp.getInt("theme", ThemeUtil.THEME_DEFAULT)))

        compositeDisposable.add(rxBus.toObservable(EventThemeSwitch::class.java).subscribe {
            setTheme(ThemeUtil.getThemeId(sp.getInt("theme", ThemeUtil.THEME_DEFAULT)))
            recreate()
        })

        if ( sp.getBoolean(R.string.key_use_dark_mode, true)) {
            val cd = ColorDrawable(sp.getInt("darkBackgroundColor", ContextCompat.getColor(this, R.color.background_dark)))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        } else {
            val cd = ColorDrawable(sp.getInt("lightBackgroundColor", ContextCompat.getColor(this, R.color.background_light)))
            if ( !sp.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable( cd)
        }
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}