package info.nightscout.androidaps.setupwizard.elements

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.setupwizard.SWTextValidator
import info.nightscout.androidaps.utils.CryptoUtil

class SWEditEncryptedPassword(injector: HasAndroidInjector, private val cryptoUtil: CryptoUtil) : SWItem(injector, Type.STRING) {

    private var validator: SWTextValidator = SWTextValidator(String::isNotEmpty)
    private var updateDelay = 0L
    private var button: Button? = null
    private var editText: EditText? = null
    private var editText2: EditText? = null
    private var l: TextView? = null
    private var c: TextView? = null
    private var c2: TextView? = null

    override fun generateDialog(layout: LinearLayout) {
        val context = layout.context
        val isPasswordSet = sp.contains(R.string.key_master_password) && sp.getString(R.string.key_master_password, "") != ""

        button = Button(context)
        button?.setText(R.string.unlock_settings)
        button?.setOnClickListener {
            scanForActivity(context)?.let { activity ->
                passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, {
                    button?.visibility = View.GONE
                    editText?.visibility = View.VISIBLE
                    editText2?.visibility = View.VISIBLE
                    l?.visibility = View.VISIBLE
                    c?.visibility = View.VISIBLE
                    c2?.visibility = View.VISIBLE
                })
            }
        }
        button?.visibility = isPasswordSet.toVisibility()
        layout.addView(button)

        label?.let {
            l = TextView(context)
            l?.id = View.generateViewId()
            l?.setText(it)
            l?.setTypeface(l?.typeface, Typeface.BOLD)
            layout.addView(l)
        }

        comment?.let {
            c = TextView(context)
            c?.id = View.generateViewId()
            c?.setText(it)
            c?.setTypeface(c?.typeface, Typeface.ITALIC)
            c?.visibility = isPasswordSet.not().toVisibility()
            layout.addView(c)
        }

        editText = EditText(context)
        editText?.id = View.generateViewId()
        editText?.inputType = InputType.TYPE_CLASS_TEXT
        editText?.maxLines = 1
        editText?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        editText?.visibility = isPasswordSet.not().toVisibility()
        layout.addView(editText)

        c2 = TextView(context)
        c2?.id = View.generateViewId()
        c2?.setText(R.string.confirm)
        c2?.visibility = isPasswordSet.not().toVisibility()
        layout.addView(c2)

        editText2 = EditText(context)
        editText2?.id = View.generateViewId()
        editText2?.inputType = InputType.TYPE_CLASS_TEXT
        editText2?.maxLines = 1
        editText2?.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        editText2?.visibility = isPasswordSet.not().toVisibility()
        layout.addView(editText2)

        super.generateDialog(layout)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                sp.remove(preferenceId)
                scheduleChange(updateDelay)
                if (validator.isValid(editText?.text.toString()) && validator.isValid(editText2?.text.toString()) && editText?.text.toString() == editText2?.text.toString())
                    save(s.toString(), updateDelay)
            }

            override fun afterTextChanged(s: Editable) {}
        }
        editText?.addTextChangedListener(watcher)
        editText2?.addTextChangedListener(watcher)
    }

    fun preferenceId(preferenceId: Int): SWEditEncryptedPassword {
        this.preferenceId = preferenceId
        return this
    }

    fun validator(validator: SWTextValidator): SWEditEncryptedPassword {
        this.validator = validator
        return this
    }

    override fun save(value: String, updateDelay: Long) {
        sp.putString(preferenceId, cryptoUtil.hashPassword(value))
        scheduleChange(updateDelay)
    }
}