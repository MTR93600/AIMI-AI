package info.nightscout.androidaps.utils.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import info.nightscout.androidaps.core.databinding.NumberPickerLayoutVerticalBinding

class NumberPickerVertical(context: Context, attrs: AttributeSet? = null) : NumberPicker(context, attrs) {

    override fun inflate(context: Context) {
        val inflater = LayoutInflater.from(context)
        val bindLayout = NumberPickerLayoutVerticalBinding.inflate(inflater, this, true)
        binding = NumberPickerViewAdapter(null, bindLayout)
    }
}
