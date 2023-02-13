package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities

import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Created by Dirceu on 01/01/21.
 */
class MedLinkStandardReturn<B> @JvmOverloads constructor(var answer: Supplier<Stream<String>>, var functionResult: B, private var errors: MutableList<ParsingError> = ArrayList()) {

    enum class ParsingError {
        ModelParsingError, ConnectionParsingError, Timeout, Interrupted, BasalParsingError, BasalComparisonError, Unreachable, ProfileParsingError, BolusParsingError
    }

    constructor(answer: Supplier<Stream<String>>, functionResult: B, errors: ParsingError) : this(answer, functionResult, ArrayList<ParsingError>()) {
        addError(errors)
    }

    fun getErrors(): List<ParsingError> {
        return errors
    }

    fun addError(error: ParsingError) {
        errors.add(error)
    }

    fun setErrors(errors: MutableList<ParsingError>) {
        this.errors = errors
    }

    fun getAnswer(): Stream<String> {
        return answer.get()
    }

    @JvmName("setAnswer1") fun setAnswer(answer: Supplier<Stream<String>>) {
        this.answer = answer
    }
}