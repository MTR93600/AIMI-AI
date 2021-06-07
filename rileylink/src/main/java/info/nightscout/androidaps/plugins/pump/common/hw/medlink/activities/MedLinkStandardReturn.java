package info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Created by Dirceu on 01/01/21.
 */
public class MedLinkStandardReturn<B> {

    public enum ParsingError{
        ModelParsingError, ConnectionParsingError, Timeout, Interrupted, BasalParsingError,
        BasalComparisonError, Unreachable, ProfileParsingError, BolusParsingError
    }

    private Supplier<Stream<String>> answer;
    private B functionResult;
    private List<ParsingError> errors;

    public MedLinkStandardReturn(Supplier<Stream<String>> answer, B functionResult) {
        this(answer,functionResult, new ArrayList<>());
    }

    public MedLinkStandardReturn(Supplier<Stream<String>> answer, B functionResult, List<ParsingError> errors) {
        this.answer = answer;
        this.functionResult = functionResult;
        this.errors = errors;
    }

    public MedLinkStandardReturn(Supplier<Stream<String>> answer, B functionResult, ParsingError errors) {
        this(answer, functionResult, new ArrayList<>());
        addError(errors);
    }


    public List<ParsingError> getErrors() {
        return errors;
    }

    public void addError(ParsingError error) {
        this.errors.add(error);
    }

    public void setErrors(List<ParsingError> errors) {
        this.errors = errors;
    }

    public B getFunctionResult() {
        return functionResult;
    }

    public void setFunctionResult(B functionResult) {
        this.functionResult = functionResult;
    }

    public Stream<String> getAnswer() {
        return answer.get();
    }

    public void setAnswer(Supplier<Stream<String>> answer) {
        this.answer = answer;
    }
}
