package info.nightscout.interfaces.utils

import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned

object HtmlHelper {

    fun fromHtml(source: String): Spanned =
        try {
            Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY)
        } catch (e: Exception) {
            SpannableStringBuilder("")
        }
}