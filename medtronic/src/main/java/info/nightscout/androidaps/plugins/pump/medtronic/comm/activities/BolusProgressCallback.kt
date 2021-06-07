package info.nightscout.androidaps.plugins.pump.medtronic.comm.activities

import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.BaseStringAggregatorCallback
import info.nightscout.androidaps.plugins.pump.common.hw.medlink.activities.MedLinkStandardReturn
import java.util.function.Supplier
import java.util.stream.Stream

class BolusProgressCallback(env:Event): BaseStringAggregatorCallback() {

    override fun apply(answer: Supplier<Stream<String>>): MedLinkStandardReturn<String> {
        // var ans = answer.get()
        // ans.
        return super.apply(answer)
    }
}