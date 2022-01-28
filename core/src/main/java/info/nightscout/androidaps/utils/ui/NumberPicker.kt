package info.nightscout.androidaps.utils.ui

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.View.OnTouchListener
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.shared.SafeParse
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.round

@SuppressLint("ClickableViewAccessibility")
open class NumberPicker(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs), View.OnKeyListener, OnTouchListener, View.OnClickListener {

    fun interface OnValueChangedListener {

        fun onValueChanged(value: Double)
    }

    var editText: EditText? = null
    private var minusButton: Button? = null
    private var plusButton: Button? = null
    var currentValue = 0.0
    var minValue = 0.0
    var maxValue = 1.0
    var step = 1.0
    var formatter: NumberFormat? = null
    var allowZero = false
    private var watcher: TextWatcher? = null
    var okButton: Button? = null
    protected var focused = false
    private var mUpdater: ScheduledExecutorService? = null
    private var mOnValueChangedListener: OnValueChangedListener? = null

    private var mHandler: Handler = Handler(Looper.getMainLooper(), Handler.Callback { msg: Message ->
        when (msg.what) {
            MSG_INC -> {
                inc(msg.arg1)
                return@Callback true
            }

            MSG_DEC -> {
                dec(msg.arg1)
                return@Callback true
            }
        }
        false
    })

    private inner class UpdateCounterTask(private val mInc: Boolean) : Runnable {

        private var repeated = 0
        private var multiplier = 1
        private val doubleLimit = 5
        override fun run() {
            val msg = Message()
            if (repeated % doubleLimit == 0) multiplier *= 2
            repeated++
            msg.arg1 = multiplier
            msg.arg2 = repeated
            if (mInc) {
                msg.what = MSG_INC
            } else {
                msg.what = MSG_DEC
            }
            mHandler.sendMessage(msg)
        }
    }

    protected open fun inflate(context: Context) {
        LayoutInflater.from(context).inflate(R.layout.number_picker_layout, this, true)
    }

    protected fun initialize(context: Context) {
        // set layout view
        inflate(context)

        // init ui components
        minusButton = findViewById(R.id.decrement)
        minusButton?.id = generateViewId()
        plusButton = findViewById(R.id.increment)
        plusButton?.id = generateViewId()
        editText = findViewById(R.id.display)
        editText?.id = generateViewId()
        minusButton?.setOnTouchListener(this)
        minusButton?.setOnKeyListener(this)
        minusButton?.setOnClickListener(this)
        plusButton?.setOnTouchListener(this)
        plusButton?.setOnKeyListener(this)
        plusButton?.setOnClickListener(this)
        setTextWatcher(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (focused) currentValue = SafeParse.stringToDouble(editText?.text.toString())
                callValueChangedListener()
                okButton?.visibility = if (currentValue > maxValue || currentValue < minValue) INVISIBLE else VISIBLE
            }
        })
        editText?.setOnFocusChangeListener { _: View?, hasFocus: Boolean ->
            focused = hasFocus
            if (!focused) value // check min/max
            updateEditText()
        }
    }

    override fun setTag(tag: Any) {
        editText?.tag = tag
    }

    fun setOnValueChangedListener(onValueChangedListener: OnValueChangedListener?) {
        mOnValueChangedListener = onValueChangedListener
    }

    fun setTextWatcher(textWatcher: TextWatcher) {
        watcher = textWatcher
        editText?.addTextChangedListener(textWatcher)
        editText?.onFocusChangeListener = OnFocusChangeListener { _: View?, hasFocus: Boolean ->
            if (!hasFocus) {
                currentValue = SafeParse.stringToDouble(editText?.text.toString())
                if (currentValue > maxValue) {
                    currentValue = maxValue
                    ToastUtils.showToastInUiThread(context, context.getString(R.string.youareonallowedlimit))
                    updateEditText()
                    okButton?.visibility = VISIBLE
                }
                if (currentValue < minValue) {
                    currentValue = minValue
                    ToastUtils.showToastInUiThread(context, context.getString(R.string.youareonallowedlimit))
                    updateEditText()
                    okButton?.visibility = VISIBLE
                }
            }
        }
    }

    fun setParams(initValue: Double, minValue: Double, maxValue: Double, step: Double, formatter: NumberFormat?, allowZero: Boolean, okButton: Button?, textWatcher: TextWatcher?) {
        if (watcher != null) {
            editText?.removeTextChangedListener(watcher)
        }
        setParams(initValue, minValue, maxValue, step, formatter, allowZero, okButton)
        watcher = textWatcher
        if (textWatcher != null) editText?.addTextChangedListener(textWatcher)
    }

    fun setParams(initValue: Double, minValue: Double, maxValue: Double, step: Double, formatter: NumberFormat?, allowZero: Boolean, okButton: Button?) {
        currentValue = initValue
        this.minValue = minValue
        this.maxValue = maxValue
        this.step = step
        this.formatter = formatter
        this.allowZero = allowZero
        callValueChangedListener()
        this.okButton = okButton
        editText?.keyListener = DigitsKeyListenerWithComma.getInstance(minValue < 0, step != round(step))
        if (watcher != null) editText?.removeTextChangedListener(watcher)
        updateEditText()
        if (watcher != null) editText?.addTextChangedListener(watcher)
    }

    var value: Double
        get() {
            if (currentValue > maxValue) {
                currentValue = maxValue
                ToastUtils.showToastInUiThread(context, context.getString(R.string.youareonallowedlimit))
            }
            if (currentValue < minValue) {
                currentValue = minValue
                ToastUtils.showToastInUiThread(context, context.getString(R.string.youareonallowedlimit))
            }
            return currentValue
        }
        set(value) {
            if (watcher != null) editText?.removeTextChangedListener(watcher)
            currentValue = value
            callValueChangedListener()
            updateEditText()
            if (watcher != null) editText?.addTextChangedListener(watcher)
        }

    val text: String
        get() = editText?.text.toString()

    private fun inc(multiplier: Int) {
        currentValue += step * multiplier
        if (currentValue > maxValue) {
            currentValue = maxValue
            callValueChangedListener()
            ToastUtils.showToastInUiThread(context, context.getString(R.string.youareonallowedlimit))
            stopUpdating()
        }
        updateEditText()
    }

    private fun dec(multiplier: Int) {
        currentValue -= step * multiplier
        if (currentValue < minValue) {
            currentValue = minValue
            callValueChangedListener()
            ToastUtils.showToastInUiThread(context, context.getString(R.string.youareonallowedlimit))
            stopUpdating()
        }
        updateEditText()
    }

    protected open fun updateEditText() {
        if (currentValue == 0.0 && !allowZero) editText?.setText("") else editText?.setText(formatter?.format(currentValue))
    }

    private fun callValueChangedListener() {
        mOnValueChangedListener?.onValueChanged(currentValue)
    }

    private fun startUpdating(inc: Boolean) {
        if (mUpdater != null) {
            //log.debug("Another executor is still active");
            return
        }
        mUpdater = Executors.newSingleThreadScheduledExecutor()
        mUpdater?.scheduleAtFixedRate(
            UpdateCounterTask(inc), 200, 200,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopUpdating() {
        mUpdater?.shutdownNow()
        mUpdater = null
    }

    override fun onClick(v: View) {
        if (mUpdater == null) {
            val imm = context.getSystemService(Service.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editText?.windowToken, 0)
            editText?.clearFocus()
            if (v === plusButton) {
                inc(1)
            } else {
                dec(1)
            }
        }
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        val isKeyOfInterest = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
        val isReleased = event.action == KeyEvent.ACTION_UP
        val isPressed = (event.action == KeyEvent.ACTION_DOWN)
        if (isKeyOfInterest && isReleased) {
            stopUpdating()
        } else if (isKeyOfInterest && isPressed) {
            startUpdating(v === plusButton)
        }
        return false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val isReleased = event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL
        val isPressed = event.action == MotionEvent.ACTION_DOWN
        if (isReleased) {
            stopUpdating()
        } else if (isPressed) {
            startUpdating(v === plusButton)
        }
        return false
    }

    companion object {

        private const val MSG_INC = 0
        private const val MSG_DEC = 1
    }

    init {
        initialize(context)
    }
}