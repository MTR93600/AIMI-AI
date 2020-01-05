package info.nightscout.androidaps.watchfaces;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import com.ustwo.clockwise.common.WatchMode;

import info.nightscout.androidaps.R;

public class Home2 extends BaseWatchFace {
    private long TapTime = 0;
    private WatchfaceZone LastZone = WatchfaceZone.NONE;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_home_2, null);
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
                    y >= chart.getTop() &&                           // 85% of chart height to leave some space for DOWN ZONE
                    y <= (chart.getTop() + 0.85 * chart.getHeight())) {                       // if double tap in chart
                TapZone = WatchfaceZone.CHART;
            } else if (x >= xlow &&
                    x  <= 2*xlow &&
                    y <= ylow) {                                    // if double tap on TOP
                TapZone = WatchfaceZone.TOP;
            } else if (x <= xlow &&
                    y >= ylow &&
                    y <= 2*ylow) {                                    // if double tap on LEFT
                TapZone = WatchfaceZone.LEFT;
            } else if (x >= 2*xlow &&
                    y >= ylow &&
                    y <= 2*ylow) {                                    // if double tap on RIGHT
                TapZone = WatchfaceZone.RIGHT;
            } else if (x >= xlow &&
                    x  <= 2*xlow &&
                    y >= ylow &&
                    y <= 2*ylow) {                                    // if double tap on CENTER
                TapZone = WatchfaceZone.CENTER;
            } else if (x >= xlow &&
                    x  <= 2*xlow &&
                    y >= 2*ylow) {                                  // // if double tap on DOWN (below chart)
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
        @ColorInt final int dividerTxtColor = dividerMatchesBg ?
                ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor) : Color.BLACK;
        @ColorInt final int dividerBatteryOkColor = ContextCompat.getColor(getApplicationContext(),
                dividerMatchesBg ? R.color.dark_gridColor : R.color.dark_uploaderBattery);
        @ColorInt final int dividerBgColor = ContextCompat.getColor(getApplicationContext(),
                dividerMatchesBg ? R.color.dark_background : R.color.dark_statusView);

        mLinearLayout.setBackgroundColor(dividerBgColor);
        mLinearLayout2.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mIOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_300));
        mIOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_300));
        mCOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_300));
        mCOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_300));
        mDay.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mMonth.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mLoop.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));

        setTextSizes();

        if (rawData.sgvLevel == 1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor));
        } else if (rawData.sgvLevel == 0) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor));
        } else if (rawData.sgvLevel == -1) {
            mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
            mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor));
        }

        if (ageLevel == 1) {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        } else {
            mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_TimestampOld));
        }

        if (rawData.batteryLevel == 1) {
            mUploaderBattery.setTextColor(dividerBatteryOkColor);
        } else {
            mUploaderBattery.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_uploaderBatteryEmpty));
        }
        mRigBattery.setTextColor(dividerTxtColor);
        mDelta.setTextColor(dividerTxtColor);
        mAvgDelta.setTextColor(dividerTxtColor);
        mBasalRate.setTextColor(dividerTxtColor);
        mBgi.setTextColor(dividerTxtColor);

        if (loopLevel == 1) {
            mLoop.setBackgroundResource(R.drawable.loop_green_25);
        } else {
            mLoop.setBackgroundResource(R.drawable.loop_red_25);
        }

        if (chart != null) {
            highColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_highColor);
            lowColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_lowColor);
            midColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_midColor);
            gridColor = ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
            basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
            carbColor =  ContextCompat.getColor(getApplicationContext(), R.color.dark_carbcolor);
            pointSize = 2;
            setupCharts();
        }
    }

    protected void setColorLowRes() {
        @ColorInt final int dividerTxtColor = dividerMatchesBg ?
                ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor) : Color.BLACK;
        @ColorInt final int dividerBgColor = ContextCompat.getColor(getApplicationContext(),
                dividerMatchesBg ? R.color.dark_background : R.color.dark_statusView);

        mLinearLayout.setBackgroundColor(dividerBgColor);
        mLinearLayout2.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_background));
        mLoop.setBackgroundResource(R.drawable.loop_grey_25);
        mLoop.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mTimestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_Timestamp));
        mDelta.setTextColor(dividerTxtColor);
        mAvgDelta.setTextColor(dividerTxtColor);
        mRigBattery.setTextColor(dividerTxtColor);
        mUploaderBattery.setTextColor(dividerTxtColor);
        mBasalRate.setTextColor(dividerTxtColor);
        mBgi.setTextColor(dividerTxtColor);
        mIOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mIOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mCOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mCOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mDay.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mMonth.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
        mTime.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.dark_mTime));
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
        setTextSizes();
    }

    protected void setColorBright() {

        if (getCurrentWatchMode() == WatchMode.INTERACTIVE) {

            @ColorInt final int dividerTxtColor = dividerMatchesBg ?  Color.BLACK :
                    ContextCompat.getColor(getApplicationContext(), R.color.dark_gridColor);
            @ColorInt final int dividerBgColor = ContextCompat.getColor(getApplicationContext(),
                    dividerMatchesBg ? R.color.light_background : R.color.light_stripe_background);

            mLinearLayout.setBackgroundColor(dividerBgColor);
            mLinearLayout2.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            mRelativeLayout.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.light_background));
            mTime.setTextColor(Color.BLACK);
            mIOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_steampunk));
            mIOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_steampunk));
            mCOB1.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_steampunk));
            mCOB2.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.grey_steampunk));
            mDay.setTextColor(Color.BLACK);
            mMonth.setTextColor(Color.BLACK);
            mLoop.setTextColor(Color.BLACK);

            setTextSizes();

            if (rawData.sgvLevel == 1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_highColor));
            } else if (rawData.sgvLevel == 0) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_midColor));
            } else if (rawData.sgvLevel == -1) {
                mSgv.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
                mDirection.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor));
            }

            if (ageLevel == 1) {
                mTimestamp.setTextColor(Color.BLACK);
            } else {
                mTimestamp.setTextColor(Color.RED);
            }

            if (rawData.batteryLevel == 1) {
                mUploaderBattery.setTextColor(dividerTxtColor);
            } else {
                mUploaderBattery.setTextColor(Color.RED);
            }
            mRigBattery.setTextColor(dividerTxtColor);
            mDelta.setTextColor(dividerTxtColor);
            mAvgDelta.setTextColor(dividerTxtColor);
            mBasalRate.setTextColor(dividerTxtColor);
            mBgi.setTextColor(dividerTxtColor);

            if (loopLevel == 1) {
                mLoop.setBackgroundResource(R.drawable.loop_green_25);
            } else {
                mLoop.setBackgroundResource(R.drawable.loop_red_25);
            }

            if (chart != null) {
                highColor = ContextCompat.getColor(getApplicationContext(), R.color.light_highColor);
                lowColor = ContextCompat.getColor(getApplicationContext(), R.color.light_lowColor);
                midColor = ContextCompat.getColor(getApplicationContext(), R.color.light_midColor);
                gridColor = ContextCompat.getColor(getApplicationContext(), R.color.light_gridColor);
                basalBackgroundColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_light);
                basalCenterColor = ContextCompat.getColor(getApplicationContext(), R.color.basal_dark);
                carbColor =  ContextCompat.getColor(getApplicationContext(), R.color.light_carbcolor);
                pointSize = 2;
                setupCharts();
            }
        } else {
            setColorDark();
        }
    }

    protected void setTextSizes() {
        // Adjust text size according to watchscreen resolution
        int hoursize = mRelativeLayout.getHeight()/9.5 > 30 ? (int)(mRelativeLayout.getHeight()/9.5) : 30;  // 42 for 400, 34 for 320 , 29 for 280 (original = 30)
        int svgsize = mRelativeLayout.getHeight()/8 > 38 ? mRelativeLayout.getHeight()/8 : 38;              // 50 for 400, 40 for 320 , 35 for 280 (original = 38)
        int smalltxt = mRelativeLayout.getHeight()/32 > 10 ? mRelativeLayout.getHeight()/32 : 10;           // 13 for 400, 10 for 320 , 9 for 280 (original = 10)
        int midtxt = mRelativeLayout.getHeight()/25 > 14 ? mRelativeLayout.getHeight()/25 : 14;             // 16 for 400, 13 for 320 , 11 for 280 (original = 14)
        int topmargin = mRelativeLayout.getHeight()>320 ? (320 - mRelativeLayout.getHeight())/10 : 0;       // top margin for hour needs to be adjust above 320px
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)mTime.getLayoutParams();
        if (mIOB1 != null && mIOB2 != null) {
            if (rawData.detailedIOB) {
                mIOB1.setTextSize(midtxt);
                mIOB2.setTextSize(smalltxt);
            } else {
                mIOB1.setTextSize(smalltxt);
                mIOB2.setTextSize(midtxt);
            }
        }
        mCOB1.setTextSize(smalltxt);
        mCOB2.setTextSize(midtxt);
        mDay.setTextSize(midtxt);
        mMonth.setTextSize(smalltxt);
        mTime.setTextSize((int) hoursize);
        params.setMargins(0, topmargin, 0, 0); //substitute parameters for left, top, right, bottom
        mTime.setLayoutParams(params);
        mSgv.setTextSize(svgsize);
    }
}
