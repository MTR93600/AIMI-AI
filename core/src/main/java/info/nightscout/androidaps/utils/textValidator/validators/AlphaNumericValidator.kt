package info.nightscout.androidaps.utils.textValidator.validators

class AlphaNumericValidator(message: String?) : RegexpValidator(message, "[a-zA-Z0-9\u00C0-\u00FF \\./-\\?]*") 