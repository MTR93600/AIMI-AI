package info.nightscout.androidaps.plugins.pump.common.hw.medlink;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Created by Dirceu on 26/11/20.
 */
public class BaseResultActivity implements Function<String,String> {
    private List<Function<String, String>> functions = new ArrayList<>();

    public void addResult(Function<String, String> toBeAdded ) {
        functions.add(toBeAdded);
    }

    @Override public String apply(String a) {
        return functions.stream().reduce(a, (f, element) -> element.apply(f), (left, right) -> left +"\n"+ right);

    }
}
