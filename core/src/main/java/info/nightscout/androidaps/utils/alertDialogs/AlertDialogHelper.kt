package info.nightscout.androidaps.utils.alertDialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil

object AlertDialogHelper {

    @Suppress("FunctionName")
    fun Builder(context: Context, @StyleRes themeResId: Int = ThemeUtil.getActualTheme()) =
        MaterialAlertDialogBuilder(ContextThemeWrapper(context, themeResId))

    fun buildCustomTitle(context: Context, title: String,
                         @DrawableRes iconResource: Int = R.drawable.ic_check_while_48dp,
                         @StyleRes themeResId: Int = ThemeUtil.getActualTheme(),
                         @LayoutRes layoutResource: Int = R.layout.dialog_alert_custom_title): View? {
        val titleLayout = LayoutInflater.from(ContextThemeWrapper(context, themeResId)).inflate(layoutResource, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = title
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(iconResource)
        titleLayout.findViewById<View>(R.id.alertdialog_title).isSelected = true
        return titleLayout
    }

}