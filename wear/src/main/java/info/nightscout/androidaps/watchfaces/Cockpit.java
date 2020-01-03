package info.nightscout.androidaps.watchfaces;

import android.content.Intent;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.LayoutInflater;
import android.view.View;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.menus.MainMenuActivity;

/**
 * Created by andrew-warrington on 18/11/2017.
 * Updated by Philoul on dec 2019(onTapCommand)
 */

public class Cockpit extends BaseWatchFace {

    private long TapTime = 0;
    private WatchfaceZone LastZone = WatchfaceZone.NONE;

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        layoutView = inflater.inflate(R.layout.activity_cockpit, null);
        performViewSetup();
    }

    @Override
    protected void onTapCommand(int tapType, int x, int y, long eventTime) {
        if (tapType == TAP_TYPE_TAP) {
            WatchfaceZone TapZone ;
            int xlow = mRelativeLayout.getWidth()/3;
            int ylow = mRelativeLayout.getHeight()/3;

            if (x >= xlow &&
                    x  <= 2*xlow &&
                    y >= 2*ylow) {                                  // if double tap on DOWN
                TapZone = WatchfaceZone.DOWN;
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
    protected WatchFaceStyle getWatchFaceStyle() {
        return new WatchFaceStyle.Builder(this).setAcceptsTapEvents(true).build();
    }

    protected void setColorDark() {

        mRelativeLayout.setBackgroundResource(R.drawable.airplane_cockpit_outside_clouds);
        setTextSizes();

        if (mHighLight != null && mLowLight != null) {
            if (rawData.sgvLevel == 1) {
                mHighLight.setBackgroundResource(R.drawable.airplane_led_yellow_lit);
                mLowLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
            } else if (rawData.sgvLevel == 0) {
                mHighLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
                mLowLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
            } else if (rawData.sgvLevel == -1) {
                mHighLight.setBackgroundResource(R.drawable.airplane_led_grey_unlit);
                mLowLight.setBackgroundResource(R.drawable.airplane_led_red_lit);
            }
        }

        if (loopLevel == 1) {
            mLoop.setBackgroundResource(R.drawable.loop_green_25);
        } else {
            mLoop.setBackgroundResource(R.drawable.loop_red_25);
        }

        invalidate();

    }

    protected void setColorLowRes() {
        mRelativeLayout.setBackgroundResource(R.drawable.airplane_cockpit_outside_clouds_lowres);

    }

    protected void setColorBright() {
        setColorDark();
    }

    protected void setTextSizes() {

        if (mIOB2 != null) {
            if (rawData.detailedIOB) {
                if (bIsRound) {
                    mIOB2.setTextSize(10);
                } else {
                    mIOB2.setTextSize(9);
                }
            } else {
                if (bIsRound) {
                    mIOB2.setTextSize(13);
                } else {
                    mIOB2.setTextSize(12);
                }
            }
        }

        if ((mUploaderBattery.getVisibility() != View.GONE) && (mRigBattery.getVisibility() != View.GONE)) {
            if (bIsRound) {
                mUploaderBattery.setTextSize(12);
                mRigBattery.setTextSize(12);
            } else {
                mUploaderBattery.setTextSize(10);
                mRigBattery.setTextSize(10);
            }
        } else {
            if (bIsRound) {
                mUploaderBattery.setTextSize(13);
                mRigBattery.setTextSize(13);
            } else {
                mUploaderBattery.setTextSize(12);
                mRigBattery.setTextSize(12);
            }
        }
    }
}