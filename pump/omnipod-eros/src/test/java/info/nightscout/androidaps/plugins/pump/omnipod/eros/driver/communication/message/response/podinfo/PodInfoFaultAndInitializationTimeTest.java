package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import static com.google.common.truth.Truth.assertThat;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.pump.core.utils.ByteUtil;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

class PodInfoFaultAndInitializationTimeTest {
    @Test
    void testDecoding() {
        PodInfoFaultAndInitializationTime podInfoFaultAndInitializationTime = new PodInfoFaultAndInitializationTime(ByteUtil.fromHexString("059200010000000000000000091912170e")); // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        assertThat(podInfoFaultAndInitializationTime.getFaultEventCode()).isEqualTo(FaultEventCode.BAD_PUMP_REQ_2_STATE);
        assertThat(podInfoFaultAndInitializationTime.getTimeFaultEvent()).isEqualTo(Duration.standardMinutes(1));

        DateTime dateTime = podInfoFaultAndInitializationTime.getInitializationTime();
        assertThat(dateTime.getYear()).isEqualTo(2018);
        assertThat(dateTime.getMonthOfYear()).isEqualTo(9);
        assertThat(dateTime.getDayOfMonth()).isEqualTo(25);
        assertThat(dateTime.getHourOfDay()).isEqualTo(23);
        assertThat(dateTime.getMinuteOfHour()).isEqualTo(14);
    }
}
