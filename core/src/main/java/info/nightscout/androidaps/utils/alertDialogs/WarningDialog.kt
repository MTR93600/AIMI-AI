package info.nightscout.androidaps.utils.alertDialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.SystemClock
import androidx.annotation.StringRes
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.extensions.runOnUiThread

// if you need error dialog - duplicate to ErrorDialog and make it and use: AppThemeErrorDialog & R.drawable.ic_header_error instead

object WarningDialog {

    @SuppressLint("InflateParams")
    fun showWarning(context: Context, title: String, message: String, @StringRes positiveButton: Int = -1, ok: (() -> Unit)? = null, cancel: (() -> Unit)? = null) {
        var okClicked = false
        val builder = AlertDialogHelper.Builder(context, R.style.AppThemeWarningDialog)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title, R.drawable.ic_header_warning, R.style.AppThemeWarningDialog))
            .setNegativeButton(R.string.dismiss) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    if (cancel != null) {
                        runOnUiThread(Runnable {
                            cancel()
                        })
                    }
                }
            }

        if (positiveButton != -1) {
            builder.setPositiveButton(positiveButton) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    if (ok != null) {
                        runOnUiThread(Runnable {
                            ok()
                        })
                    }
                }
            }
        }

        val dialog = builder.show()
        dialog.setCanceledOnTouchOutside(true)
    }

}