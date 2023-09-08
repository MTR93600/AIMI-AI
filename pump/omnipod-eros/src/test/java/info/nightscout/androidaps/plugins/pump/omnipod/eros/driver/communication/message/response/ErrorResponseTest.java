package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.PodProgressStatus;
import info.nightscout.pump.core.utils.ByteUtil;

class ErrorResponseTest {
    @Test
    void testGetRawData() {
        byte[] encodedData = ByteUtil.fromHexString("060314fa92");
        ErrorResponse errorResponse = new ErrorResponse(encodedData);

        Assertions.assertArrayEquals(encodedData, errorResponse.getRawData());
    }

    @Test
    void testGetRawDataWithLongerMessage() {
        byte[] encodedData = ByteUtil.fromHexString("060314fa9201");
        byte[] expected = ByteUtil.fromHexString("060314fa92");

        ErrorResponse errorResponse = new ErrorResponse(encodedData);

        Assertions.assertArrayEquals(expected, errorResponse.getRawData());
    }

    @Test
    void testBadNonce() {
        byte[] encodedData = ByteUtil.fromHexString("060314fa92");

        ErrorResponse errorResponse = new ErrorResponse(encodedData);
        Assertions.assertEquals(ErrorResponse.ERROR_RESPONSE_CODE_BAD_NONCE, errorResponse.getErrorResponseCode());
        // TODO add assertion on nonce search key (obtain captures first)
        Assertions.assertNull(errorResponse.getFaultEventCode());
        Assertions.assertNull(errorResponse.getPodProgressStatus());
    }

    @Test
    void testOtherError() {
        ErrorResponse errorResponse = new ErrorResponse(ByteUtil.fromHexString("0603101308"));
        Assertions.assertEquals(0x10, errorResponse.getErrorResponseCode());
        Assertions.assertEquals(FaultEventCode.MESSAGE_LENGTH_TOO_LONG, errorResponse.getFaultEventCode());
        Assertions.assertEquals(PodProgressStatus.ABOVE_FIFTY_UNITS, errorResponse.getPodProgressStatus());

        Assertions.assertNull(errorResponse.getNonceSearchKey());
    }
}
