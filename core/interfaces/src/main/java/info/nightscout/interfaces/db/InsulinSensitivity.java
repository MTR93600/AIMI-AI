package info.nightscout.interfaces.db;

/**
 * Created by Dirceu on 02/02/21.
 */
public class InsulinSensitivity {
    public int sensitivity;
    public String from;

    public InsulinSensitivity(Integer ratio, String from) {
        this.sensitivity = ratio;
        this.from = from;
    }
}
