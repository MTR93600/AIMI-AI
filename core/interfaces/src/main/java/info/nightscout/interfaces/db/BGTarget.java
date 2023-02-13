package info.nightscout.interfaces.db;

/**
 * Created by Dirceu on 02/02/21.
 */
public class BGTarget {
    public int lowTarget;
    public int highTarget;
    public String from;

    public BGTarget(Integer lowTarget, Integer highTarget, String from) {
        this.lowTarget = lowTarget;
        this.highTarget = highTarget;
        this.from = from;
    }
}
