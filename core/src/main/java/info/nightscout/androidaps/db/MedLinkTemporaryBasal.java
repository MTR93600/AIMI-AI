package info.nightscout.androidaps.db;

import dagger.android.HasAndroidInjector;

/**
 * Created by Dirceu on 26/05/21.
 */
public class MedLinkTemporaryBasal extends TemporaryBasal{
    private double desiredRate =0d;
    private int desiredPct = 0;

    public MedLinkTemporaryBasal(HasAndroidInjector injector) {
        this.injector = injector;
        injector.androidInjector().inject(this);
    }

    public double getDesiredRate() {
        return desiredRate;
    }

    public void setDesiredRate(double desiredRate) {
        this.desiredRate = desiredRate;
    }

    public int getDesiredPct() {
        return desiredPct;
    }

    public void setDesiredPct(int desiredPct) {
        this.desiredPct = desiredPct;
    }



}
