package info.nightscout.androidaps.utils.textValidator.validators

import java.util.regex.Pattern

/**
 * Used for validating the user input using a regexp.
 *
 * @author Andrea Baccega <me></me>@andreabaccega.com>
 */
open class RegexpValidator(message: String?, _regexp: String?) : PatternValidator(message, Pattern.compile(_regexp))