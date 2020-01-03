package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;

import com.ustwo.clockwise.common.WatchMode;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

public class Home extends BaseWatchFace {

    private long TapTime = 0;
    private WatchfaceZone LastZone = WatchfaceZone.NONE;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_home, null);
        performViewSetup();
    }

    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {
        if (tapType == TAP_TYPE_TAP) {
            WatchfaceZone TapZone ;
            int xlow = mRelativeLayout.getWidth()/3;
            int ylow = chart.getTop()/2;

            if (x >= chart.getLeft() &&
                    x <= chart.getRight() &&
                    y >= chart.getTop() &&
                    y <= chart.getBottom()) {                       // if double tap in chart
                TapZone = WatchfaceZone.CHART;
            } else if (x >= xlow &&
                    x  <= 2*xlow &&
                    y <= ylow) {                                    // if double tap on TOP
                TapZone = WatchfaceZone.TOP;
            } else if (x <= xlow &&
                    y >= ylow) {                                    // if double tap on LEFT
                TapZone = WatchfaceZone.LEFT;
            } else if (x >= 2*xlow &&
                    y >= ylow) {                                    // if double tap on RIGHT
                TapZone = WatchfaceZone.RIGHT;
            } else if (x >= xlow &&
                    x  <= 2*xlow &&
                    y >= ylow &&
                    y <= 2*ylow) {                                    // if double tap on CENTER
                TapZone = WatchfaceZone.CENTER;
            } else if (x >= xlow &&
                    x  <= 2*xlow &&
                    y >= 2*ylow) {                                    // if double tap on DOWN (below chart)
                TapZone = WatchfaceZone.DOWN;
            } else {                                                // on all background (outside chart and Top, Down, left, right and center) access to main menu
                TapZone = WatchfaceZone.BACKGROUND;
            }
            if (eventTime - TapTime < 800 && LastZone == TapZone) {
                doTapAction(TapZone);
            }
            TapTime = eventTime;
            LastZone = TapZone;
        }
    }


    @Override
    protected WatchFaceStyle getWatchFaceStyle(){
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }


    protected void setColorDark() {
        mLinearLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                R.color.dark_background : R.color.dark_statusView));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        if (rawData.sgvLevel == 1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
        } else if (rawData.sgvLevel == 0) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else if (rawData.sgvLevel == -1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
        }

        if (ageLevel == 1) {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                    R.color.dark_gridColor : R.color.dark_mTimestamp1_home));
        } else {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_TimestampOld));
        }

        if (rawData.batteryLevel == 1) {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                    R.color.dark_gridColor : R.color.dark_uploaderBattery));
        } else {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBatteryEmpty));
        }

        mStatus.setTextColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                R.color.dark_gridColor : R.color.dark_mStatus_home));

        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
            pointSize = 2;
            setupCharts();
        }
    }

    protected void setColorLowRes() {
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor));
        mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor));
        mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_Timestamp));
        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark_lowres);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light_lowres);
            pointSize = 2;
            setupCharts();
        }

    }


    protected void setColorBright() {

        if (getCurrentWatchMode() == WatchMode.INTERACTIVE) {
            mLinearLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), dividerMatchesBg ?
                    R.color.light_background : R.color.light_stripe_background));
            mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            if (rawData.sgvLevel == 1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
            } else if (rawData.sgvLevel == 0) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
            } else if (rawData.sgvLevel == -1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDelta.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
            }

            if (ageLevel == 1) {
                mTimestamp.setTextColor(dividerMatchesBg ? Color.BLACK : Color.WHITE);
            } else {
                mTimestamp.setTextColor(Color.RED);
            }

            if (rawData.batteryLevel == 1) {
                mUploaderBattery.setTextColor(dividerMatchesBg ? Color.BLACK : Color.WHITE);
            } else {
                mUploaderBattery.setTextColor(Color.RED);
            }
            mStatus.setTextColor(dividerMatchesBg ? Color.BLACK : Color.WHITE);

            mTime.setTextColor(Color.BLACK);
            if (chart != null) {
                highColor = ContextCompat.getColor(getApplicationContext(), R.color.light_highColor);
                lowColor = ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor);
                midColor = ContextCompat.getColor(getApplicationContext(), R.color.light_midColor);
                gridColor = ContextCompat.getColor(getApplicationContext(), R.color.light_gridColor);
                basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
                basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
                pointSize = 2;
                setupCharts();
            }
        } else {
            setColorDark();
        }
    }
}
