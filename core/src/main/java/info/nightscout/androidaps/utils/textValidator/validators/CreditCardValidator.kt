package info.nightscout.androidaps.utils.textValidator.validators

import android.widget.EditText

/**
 * This validator takes care of validating the edittext. The input will be valid only if the number is a valid credit card.
 */
class CreditCardValidator(_customErrorMessage: String?) : Validator(_customErrorMessage) {

    override fun isValid(editText: EditText): Boolean {
        return try {
            validateCardNumber(editText.text.toString())
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /**
         * Validates the credit card number using the Luhn algorithm
         *
         * @param cardNumber the credit card number
         * @return
         */
        @Throws(NumberFormatException::class)
        fun validateCardNumber(cardNumber: String): Boolean {
            var sum = 0
            var digit: Int
            var addend: Int
            var doubled = false
            for (i in cardNumber.length - 1 downTo 0) {
                digit = cardNumber.substring(i, i + 1).toInt()
                if (doubled) {
                    addend = digit * 2
                    if (addend > 9) {
                        addend -= 9
                    }
                } else {
                    addend = digit
                }
                sum += addend
                doubled = !doubled
            }
            return sum % 10 == 0
        }
    }
}