package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FaultEventCode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PodInfoFaultAndInitializationTimeTest {
    @Test
    public void testDecoding() {
        PodInfoFaultAndInitializationTime podInfoFaultAndInitializationTime = new PodInfoFaultAndInitializationTime(ByteUtil.fromHexString("059200010000000000000000091912170e")); // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        assertEquals(FaultEventCode.BAD_PUMP_REQ_2_STATE, podInfoFaultAndInitializationTime.getFaultEventCode());
        assertTrue(Duration.standardMinutes(1).isEqual(podInfoFaultAndInitializationTime.getTimeFaultEvent()));

        DateTime dateTime = podInfoFaultAndInitializationTime.getInitializationTime();
        assertEquals(2018, dateTime.getYear());
        assertEquals(9, dateTime.getMonthOfYear());
        assertEquals(25, dateTime.getDayOfMonth());
        assertEquals(23, dateTime.getHourOfDay());
        assertEquals(14, dateTime.getMinuteOfHour());
    }
}
