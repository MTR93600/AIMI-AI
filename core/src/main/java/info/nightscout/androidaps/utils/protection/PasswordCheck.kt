package info.nightscout.androidaps.utils.protection

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.utils.CryptoUtil
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.AlertDialogHelper
import info.nightscout.shared.sharedPreferences.SP
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// since androidx.autofill.HintConstants are not available
const val AUTOFILL_HINT_NEW_PASSWORD = "newPassword"

@Singleton
class PasswordCheck @Inject constructor(
    private val sp: SP,
    private val cryptoUtil: CryptoUtil,
    private val fileListProvider: PrefFileListProvider,
    private val activePlugin: ActivePlugin
) {

    /**
    Asks for "managed" kind of password, checking if it is valid.
     */
    @SuppressLint("InflateParams")
    fun queryPassword(context: Context, @StringRes labelId: Int, @StringRes preference: Int, ok: ((String) -> Unit)?, cancel: (() -> Unit)? = null, fail: (() -> Unit)? = null, pinInput: Boolean = false) {
        val password = sp.getString(preference, "")
        if (password == "") {
            ok?.invoke("")
            return
        }
        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = MaterialAlertDialogBuilder(context, R.style.DialogTheme)
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText

        userInput2.visibility = View.GONE
        if (pinInput) {
            userInput.setHint(R.string.pin_hint)
            userInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val autoFillHintPasswordKind = context.getString(preference)
        userInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "aaps_${autoFillHintPasswordKind}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        fun validatePassword(): Boolean {
            val enteredPassword = userInput.text.toString()
            if (cryptoUtil.checkPassword(enteredPassword, password)) {
                val im = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                im.hideSoftInputFromWindow(userInput.windowToken, 0)
                ok?.invoke(enteredPassword)
                return true
            }
            val msg = if (pinInput) R.string.wrongpin else R.string.wrongpassword
            ToastUtils.errorToast(context, context.getString(msg))
            fail?.invoke()
            return false
        }

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(R.string.ok)) { _, _ -> validatePassword() }
            .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
                cancel?.invoke()
                dialog.cancel()
            }

        val alert = alertDialogBuilder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            show()
        }

        userInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (validatePassword())
                    alert.dismiss()
                true
            } else {
                false
            }
        }
    }

    @SuppressLint("InflateParams")
    fun setPassword(context: Context, @StringRes labelId: Int, @StringRes preference: Int, ok: ((String) -> Unit)? = null, cancel: (() -> Unit)? = null, clear: (() -> Unit)? = null, pinInput: Boolean = false) {
        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = MaterialAlertDialogBuilder(context, R.style.DialogTheme)
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText
        if (pinInput) {
            userInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            userInput2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            userInput.setHint(R.string.pin_hint)
            userInput2.setHint(R.string.pin_hint)
        }
        val autoFillHintPasswordKind = context.getString(preference)
        userInput.setAutofillHints(AUTOFILL_HINT_NEW_PASSWORD, "aaps_${autoFillHintPasswordKind}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                val enteredPassword2 = userInput2.text.toString()
                if (enteredPassword != enteredPassword2) {
                    val msg = if (pinInput) R.string.pin_dont_match else R.string.passwords_dont_match
                    ToastUtils.errorToast(context, context.getString(msg))
                } else if (enteredPassword.isNotEmpty()) {
                    sp.putString(preference, cryptoUtil.hashPassword(enteredPassword))
                    val msg = if (pinInput) R.string.pin_set else R.string.password_set
                    ToastUtils.okToast(context, context.getString(msg))
                    ok?.invoke(enteredPassword)
                } else {
                    if (sp.contains(preference)) {
                        sp.remove(preference)
                        val msg = if (pinInput) R.string.pin_cleared else R.string.password_cleared
                        ToastUtils.graphicalToast(context, context.getString(msg), R.drawable.ic_toast_delete_confirm)
                        clear?.invoke()
                    } else {
                        val msg = if (pinInput) R.string.pin_not_changed else R.string.password_not_changed
                        ToastUtils.warnToast(context, context.getString(msg))
                        cancel?.invoke()
                    }
                }

            }
            .setNegativeButton(context.getString(R.string.cancel)
            ) { dialog, _ ->
                val msg = if (pinInput) R.string.pin_not_changed else R.string.password_not_changed
                ToastUtils.infoToast(context, context.getString(msg))
                cancel?.invoke()
                dialog.cancel()
            }

        alertDialogBuilder.create().show()
    }

    /**
    Prompt free-form password, with additional help and warning messages.
    Preference ID (preference) is used only to generate ID for password managers,
    since this query does NOT check validity of password.
     */
    @SuppressLint("InflateParams")
    fun queryAnyPassword(context: Context, @StringRes labelId: Int, @StringRes preference: Int, @StringRes passwordExplanation: Int?,
                         @StringRes passwordWarning: Int?, ok: ((String) -> Unit)?, cancel: (() -> Unit)? = null) {

        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = MaterialAlertDialogBuilder(context, R.style.DialogTheme)
        alertDialogBuilder.setView(promptsView)
        passwordExplanation?.let { alertDialogBuilder.setMessage(it) }

        passwordWarning?.let {
            val extraWarning: TextView = promptsView.findViewById<View>(R.id.password_prompt_extra_message) as TextView
            extraWarning.text = context.getString(it)
            extraWarning.visibility = View.VISIBLE
        }

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText

        userInput2.visibility = View.GONE

        val autoFillHintPasswordKind = context.getString(preference)
        userInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "aaps_${autoFillHintPasswordKind}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        fun validatePassword() {
            val enteredPassword = userInput.text.toString()
            ok?.invoke(enteredPassword)
        }

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(R.string.ok)) { _, _ -> validatePassword() }
            .setNegativeButton(context.getString(R.string.cancel)
            ) { dialog, _ ->
                cancel?.invoke()
                dialog.cancel()
            }

        val alert = alertDialogBuilder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            show()
        }

        userInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validatePassword()
                alert.dismiss()
                true
            } else {
                false
            }
        }
    }

    /**
     * Check for existing PasswordReset file and
     * reset password to SN of active pump if file exists
     */
    fun passwordResetCheck(context: Context) {
        val passwordReset = File(fileListProvider.ensureExtraDirExists(), "PasswordReset")
        if (passwordReset.exists()) {
            val sn = activePlugin.activePump.serialNumber()
            sp.putString(R.string.key_master_password, cryptoUtil.hashPassword(sn))
            passwordReset.delete()
            ToastUtils.okToast(context, context.getString(R.string.password_set))
        }
    }
}
