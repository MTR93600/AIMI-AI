package info.nightscout.androidaps.plugins.pump.omnipod.driver.definition;

import java.util.Locale;

public class FirmwareVersion {
    private final int major;
    private final int minor;
    private final int patch;

    public FirmwareVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%d.%d.%d", major, minor, patch);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }
}
