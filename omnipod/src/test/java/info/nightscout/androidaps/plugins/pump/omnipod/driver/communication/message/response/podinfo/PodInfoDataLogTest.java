package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo;

import org.joda.time.Duration;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.FaultEventCode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PodInfoDataLogTest {
    @Test
    public void testDecoding() {
        PodInfoDataLog podInfoDataLog = new PodInfoDataLog(ByteUtil.fromHexString("030100010001043c"), 8); // From https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift

        assertEquals(FaultEventCode.FAILED_FLASH_ERASE, podInfoDataLog.getFaultEventCode());
        assertTrue(Duration.standardMinutes(1).isEqual(podInfoDataLog.getTimeFaultEvent()));
        assertTrue(Duration.standardMinutes(1).isEqual(podInfoDataLog.getTimeSinceActivation()));
        assertEquals(4, podInfoDataLog.getDataChunkSize());
        assertEquals(60, podInfoDataLog.getMaximumNumberOfDwords());
    }
}
