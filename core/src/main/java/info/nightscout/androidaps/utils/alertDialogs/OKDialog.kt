package info.nightscout.androidaps.utils.alertDialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.text.Spanned
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.utils.AppUtils
import info.nightscout.androidaps.utils.BlurView

object OKDialog {

    private var alert: AlertDialog? = null

    fun setAlertDialogBackground(context: Context, result: Bitmap?) {
        val activity = context as Activity
        val draw = BitmapDrawable(activity.resources, result)
        val window = alert?.window
        window?.setBackgroundDrawable(draw)
        window?.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER)
        alert?.show()
        alert!!.setCanceledOnTouchOutside(false)
    }

    fun blur(activity: Activity) {
        // apply blur effect
        Thread(Runnable {
            AppUtils.screenShot(activity){bitmap ->
                val blurBitmap: Bitmap? = BlurView(activity.application).blurBackground(bitmap, 10)
                runOnUiThread {
                    setAlertDialogBackground( activity, blurBitmap)
                }
            }
        }).start()
    }


    @SuppressLint("InflateParams")
    fun show(context: Context, title: String, message: String, runnable: Runnable? = null) {
        var okClicked = false
        var notEmptyTitle = title
        if (notEmptyTitle.isEmpty()) notEmptyTitle = context.getString(R.string.message)

        val builder = AlertDialogHelper.Builder(context, R.style.DialogTheme)
        with(builder){
            setCustomTitle(AlertDialogHelper.buildCustomTitle(context, notEmptyTitle))
            setMessage(message)
            setPositiveButton(context.getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(runnable)
                }
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

    @SuppressLint("InflateParams")
    fun show(activity: FragmentActivity, title: String, message: Spanned, runnable: Runnable? = null) {
        var okClicked = false
        var notEmptyTitle = title
        if (notEmptyTitle.isEmpty()) notEmptyTitle = activity.getString(R.string.message)

        val builder = AlertDialogHelper.Builder(activity, R.style.DialogTheme)
        with(builder){
            setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, notEmptyTitle))
            setMessage(message)
            setPositiveButton(activity.getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runnable?.let { activity.runOnUiThread(it) }
                }
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

    fun showConfirmation(activity: FragmentActivity, message: String, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    fun showConfirmation(activity: FragmentActivity, message: Spanned, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(activity: FragmentActivity, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        val builder = AlertDialogHelper.Builder(activity, R.style.DialogTheme)
        with(builder){
            setMessage(message)
            setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, title))
            setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    ok?.let { activity.runOnUiThread(it) }
                }
            }
            setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    cancel?.let { activity.runOnUiThread(it) }
                }
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(activity: FragmentActivity, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        val builder = AlertDialogHelper.Builder(activity, R.style.DialogTheme)
        with(builder) {
            setMessage(message)
            setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, title))
            setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    ok?.let { activity.runOnUiThread(it) }
                }
            }
            setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    cancel?.let { activity.runOnUiThread(it) }
                }
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

    fun showConfirmation(context: Context, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(context: Context, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        val builder = AlertDialogHelper.Builder(context, R.style.DialogTheme)
        with(builder) {
            setMessage(message)
            setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
                .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                    if (okClicked) return@setPositiveButton
                    else {
                        okClicked = true
                        dialog.dismiss()
                        SystemClock.sleep(100)
                        runOnUiThread(ok)
                    }
                }
            setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(cancel)
                }
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

    fun showConfirmation(context: Context, message: String, ok: Runnable?, cancel: Runnable? = null) {
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(context: Context, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        val builder = AlertDialogHelper.Builder(context, R.style.DialogTheme)
        with(builder){
            setMessage(message)
            setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
            setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(ok)
                }
            }
            setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(cancel)
                }
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(context: Context, title: String, message: String, ok: DialogInterface.OnClickListener?, cancel: DialogInterface.OnClickListener? = null) {
        var okClicked = false
        val builder = AlertDialogHelper.Builder(context, R.style.DialogTheme)
        with(builder){
            setMessage(message)
            setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
            setPositiveButton(android.R.string.ok) { dialog: DialogInterface, which: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    ok?.onClick(dialog, which)
                }
            }
            setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    cancel?.onClick(dialog, which)
                }
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

    @SuppressLint("InflateParams")
    fun showYesNoCancel(context: Context, title: String, message: String, yes: Runnable?, no: Runnable? = null) {
        var okClicked = false
        val builder = AlertDialogHelper.Builder(context, R.style.DialogTheme)

        with(builder){
            setMessage(message)
            setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
            setPositiveButton(R.string.yes) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(yes)
                }
            }
            setNegativeButton(R.string.no) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(no)
                }
            }
            setNeutralButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
        }
        val alert = builder.create()
        with( alert ){
            show()
            setCanceledOnTouchOutside(false)
        }
    }

}
