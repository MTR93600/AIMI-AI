package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors;

import info.nightscout.androidaps.plugins.pump.insight.exceptions.AppLayerException;

public abstract class AppLayerErrorException extends AppLayerException {

    private final int errorCode;

    public AppLayerErrorException(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        return "Error code: " + errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
