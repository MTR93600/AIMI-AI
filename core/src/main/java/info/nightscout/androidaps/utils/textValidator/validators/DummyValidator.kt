package info.nightscout.androidaps.utils.textValidator.validators

import android.widget.EditText

/**
 * This is a dummy validator. It just returns true on each input.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
class DummyValidator : Validator(null) {

    override fun isValid(editText: EditText): Boolean {
        return true
    }
}