package info.nightscout.androidaps.plugins.pump.combo;


import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.combo.R;
import info.nightscout.androidaps.interfaces.CommandQueue;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.combo.data.ComboErrorUtil;
import info.nightscout.androidaps.plugins.pump.combo.events.EventComboPumpUpdateGUI;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpState;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.events.EventQueueChanged;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class ComboFragment extends DaggerFragment {
    @Inject ComboPlugin comboPlugin;
    @Inject CommandQueue commandQueue;
    @Inject ResourceHelper rh;
    @Inject RxBus rxBus;
    @Inject DateUtil dateUtil;
    @Inject FabricPrivacy fabricPrivacy;
    @Inject AapsSchedulers aapsSchedulers;
    @Inject ComboErrorUtil errorUtil;

    private final CompositeDisposable disposable = new CompositeDisposable();
    SwipeRefreshLayout swipeRefresh;
    private TextView stateView;
    private TextView activityView;
    private TextView batteryView;
    private TextView reservoirView;
    private TextView lastConnectionView;
    private TextView lastBolusView;
    private TextView baseBasalRate;
    private TextView tempBasalText;
    private TextView bolusCount;
    private TextView tbrCount;
    private ColorStateList defaultStateTextColors = null;
    private ColorStateList defaultActivityColors = null;
    private ColorStateList defaultBatteryColors = null;
    private ColorStateList defaultReservoirColors = null;
    private ColorStateList defaultConnectionColors = null;

    private View errorCountDelimiter;
    private LinearLayout errorCountLayout;
    private TextView errorCountLabel;
    private TextView errorCountDots;
    private TextView errorCountValue;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.combopump_fragment, container, false);

        stateView = view.findViewById(R.id.combo_state);
        activityView = view.findViewById(R.id.combo_activity);
        batteryView = view.findViewById(R.id.combo_pumpstate_battery);
        reservoirView = view.findViewById(R.id.combo_insulinstate);
        lastBolusView = view.findViewById(R.id.combo_last_bolus);
        lastConnectionView = view.findViewById(R.id.combo_lastconnection);
        baseBasalRate = view.findViewById(R.id.combo_base_basal_rate);
        tempBasalText = view.findViewById(R.id.combo_temp_basal);
        bolusCount = view.findViewById(R.id.combo_bolus_count);
        tbrCount = view.findViewById(R.id.combo_tbr_count);

        errorCountDelimiter = view.findViewById(R.id.combo_connection_error_delimiter);
        errorCountLayout = view.findViewById(R.id.combo_connection_error_layout);
        errorCountLabel = view.findViewById(R.id.combo_connection_error_label);
        errorCountDots = view.findViewById(R.id.combo_connection_error_dots);
        errorCountValue = view.findViewById(R.id.combo_connection_error_value);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.carbsOrange, R.color.calcGreen, R.color.blue_default);
        swipeRefresh.setProgressBackgroundColorSchemeColor(ResourcesCompat.getColor(getResources(), R.color.black_alpha_10, null));

        this.swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                //do the refresh of data here
                commandQueue.readStatus("User request", new Callback() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> swipeRefresh.setRefreshing(false));
                    }
                });
                swipeRefresh.setRefreshing(false);
            }
        });

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(rxBus
                .toObservable(EventComboPumpUpdateGUI.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> updateGui(), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventQueueChanged.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> updateGui(), fabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    private void runOnUiThread(Runnable action) {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(action);
        }
    }

    public void updateGui() {

        if (this.defaultStateTextColors == null) {
            this.defaultStateTextColors = stateView.getTextColors();
        }
        // state
        stateView.setText(comboPlugin.getStateSummary());
        PumpState ps = comboPlugin.getPump().state;
        if (ps.insulinState == PumpState.EMPTY || ps.batteryState == PumpState.EMPTY
                || ps.activeAlert != null && ps.activeAlert.errorCode != null) {
            stateView.setTextColor(rh.gac(getContext(), R.attr.statuslightAlarm));
            stateView.setTypeface(null, Typeface.BOLD);
        } else if (comboPlugin.getPump().state.suspended
                || ps.activeAlert != null && ps.activeAlert.warningCode != null) {
            stateView.setTextColor(rh.gac(getContext(), R.attr.statuslightAlarm));
            stateView.setTypeface(null, Typeface.BOLD);
        } else {
            stateView.setTextColor(this.defaultStateTextColors);
            stateView.setTypeface(null, Typeface.NORMAL);
        }

        if (this.defaultActivityColors == null) {
            this.defaultActivityColors = activityView.getTextColors();
        }
        // activity
        String activity = comboPlugin.getPump().activity;
        if (activity != null) {
            activityView.setTextColor(this.defaultActivityColors);
            activityView.setTextSize(14);
            activityView.setText(activity);
        } else if (commandQueue.size() > 0) {
            activityView.setTextColor(this.defaultActivityColors);
            activityView.setTextSize(14);
            activityView.setText("");
        } else if (comboPlugin.isInitialized()) {
            activityView.setTextColor(this.defaultActivityColors);
            activityView.setTextSize(20);
            activityView.setText("{fa-bed}");
        } else {
            activityView.setTextColor(rh.gac(getContext(), R.attr.statuslightAlarm));
            activityView.setTextSize(14);
            activityView.setText(rh.gs(R.string.pump_unreachable));
        }

        if (comboPlugin.isInitialized()) {
            if (this.defaultBatteryColors == null) {
                this.defaultBatteryColors = batteryView.getTextColors();
            }
            // battery
            batteryView.setTextSize(20);
            if (ps.batteryState == PumpState.EMPTY) {
                batteryView.setText("{fa-battery-empty}");
                batteryView.setTextColor(rh.gac(getContext(), R.attr.statuslightAlarm));
            } else if (ps.batteryState == PumpState.LOW) {
                batteryView.setText("{fa-battery-quarter}");
                batteryView.setTextColor(rh.gac(getContext(), R.attr.statuslightWarning));
            } else {
                batteryView.setText("{fa-battery-full}");
                batteryView.setTextColor(this.defaultBatteryColors);
            }

            // reservoir
            int reservoirLevel = comboPlugin.getPump().reservoirLevel;
            if (reservoirLevel != -1) {
                reservoirView.setText(reservoirLevel + " " + rh.gs(R.string.insulin_unit_shortname));
            } else if (ps.insulinState == PumpState.LOW) {
                reservoirView.setText(rh.gs(R.string.combo_reservoir_low));
            } else if (ps.insulinState == PumpState.EMPTY) {
                reservoirView.setText(rh.gs(R.string.combo_reservoir_empty));
            } else {
                reservoirView.setText(rh.gs(R.string.combo_reservoir_normal));
            }

            if (this.defaultReservoirColors == null) {
                this.defaultReservoirColors = reservoirView.getTextColors();
            }
            if (ps.insulinState == PumpState.UNKNOWN) {
                reservoirView.setTextColor(this.defaultReservoirColors);
                reservoirView.setTypeface(null, Typeface.NORMAL);
            } else if (ps.insulinState == PumpState.LOW) {
                reservoirView.setTextColor(rh.gac(getContext(), R.attr.statuslightWarning));
                reservoirView.setTypeface(null, Typeface.BOLD);
            } else if (ps.insulinState == PumpState.EMPTY) {
                reservoirView.setTextColor(rh.gac(getContext(), R.attr.statuslightAlarm));
                reservoirView.setTypeface(null, Typeface.BOLD);
            } else {
                reservoirView.setTextColor(this.defaultReservoirColors);
                reservoirView.setTypeface(null, Typeface.NORMAL);
            }

            if (this.defaultConnectionColors == null) {
                this.defaultConnectionColors = lastConnectionView.getTextColors();
            }
            // last connection
            String minAgo = dateUtil.minAgo(rh, comboPlugin.getPump().lastSuccessfulCmdTime);
            long min = (System.currentTimeMillis() - comboPlugin.getPump().lastSuccessfulCmdTime) / 1000 / 60;
            if (comboPlugin.getPump().lastSuccessfulCmdTime + 60 * 1000 > System.currentTimeMillis()) {
                lastConnectionView.setText(R.string.combo_pump_connected_now);
                lastConnectionView.setTextColor(this.defaultConnectionColors);
            } else if (comboPlugin.getPump().lastSuccessfulCmdTime + 30 * 60 * 1000 < System.currentTimeMillis()) {
                lastConnectionView.setText(rh.gs(R.string.combo_no_pump_connection, min));
                lastConnectionView.setTextColor(rh.gac(getContext(), R.attr.statuslightAlarm));
            } else {
                lastConnectionView.setText(minAgo);
                lastConnectionView.setTextColor(this.defaultConnectionColors);
            }

            // last bolus
            Bolus bolus = comboPlugin.getPump().lastBolus;
            if (bolus != null) {
                long agoMsc = System.currentTimeMillis() - bolus.timestamp;
                double bolusMinAgo = agoMsc / 60d / 1000d;
                String unit = rh.gs(R.string.insulin_unit_shortname);
                String ago;
                if ((agoMsc < 60 * 1000)) {
                    ago = rh.gs(R.string.combo_pump_connected_now);
                } else if (bolusMinAgo < 60) {
                    ago = dateUtil.minAgo(rh, bolus.timestamp);
                } else {
                    ago = dateUtil.hourAgo(bolus.timestamp, rh);
                }
                lastBolusView.setText(rh.gs(R.string.combo_last_bolus, bolus.amount, unit, ago));
            } else {
                lastBolusView.setText("");
            }

            // base basal rate
            baseBasalRate.setText(rh.gs(R.string.pump_basebasalrate, comboPlugin.getBaseBasalRate()));

            // TBR
            String tbrStr = "";
            if (ps.tbrPercent != -1 && ps.tbrPercent != 100) {
                long minSinceRead = (System.currentTimeMillis() - comboPlugin.getPump().state.timestamp) / 1000 / 60;
                long remaining = ps.tbrRemainingDuration - minSinceRead;
                if (remaining >= 0) {
                    tbrStr = rh.gs(R.string.combo_tbr_remaining, ps.tbrPercent, remaining);
                }
            }
            tempBasalText.setText(tbrStr);

            // stats
            bolusCount.setText(String.valueOf(comboPlugin.getBolusesDelivered()));
            tbrCount.setText(String.valueOf(comboPlugin.getTbrsSet()));

            updateErrorDisplay(false);
        } else {
            updateErrorDisplay(true);
        }
    }

    private void updateErrorDisplay(boolean forceHide) {
        int errorCount = -1;

        if (!forceHide) {
            ComboErrorUtil.DisplayType displayType = errorUtil.getDisplayType();

            if (displayType== ComboErrorUtil.DisplayType.ON_ERROR || displayType== ComboErrorUtil.DisplayType.ALWAYS) {
                int errorCountInternal = errorUtil.getErrorCount();

                if (errorCountInternal>0) {
                    errorCount = errorCountInternal;
                } else if (displayType== ComboErrorUtil.DisplayType.ALWAYS) {
                    errorCount = 0;
                }
            }
        }

        if (errorCount >=0) {
            errorCountDelimiter.setVisibility(View.VISIBLE);
            errorCountLayout.setVisibility(View.VISIBLE);
            errorCountLabel.setVisibility(View.VISIBLE);
            errorCountDots.setVisibility(View.VISIBLE);
            errorCountValue.setVisibility(View.VISIBLE);
            errorCountValue.setText(errorCount==0 ?
                    "-" :
                    ""+errorCount);
        } else {
            errorCountDelimiter.setVisibility(View.GONE);
            errorCountLayout.setVisibility(View.GONE);
            errorCountLabel.setVisibility(View.GONE);
            errorCountDots.setVisibility(View.GONE);
            errorCountValue.setVisibility(View.GONE);
        }
    }
}
