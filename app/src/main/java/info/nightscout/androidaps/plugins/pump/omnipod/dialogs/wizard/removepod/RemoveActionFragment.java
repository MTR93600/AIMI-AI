package info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.removepod;

import android.os.Bundle;
import android.view.View;

import com.tech.freak.wizardpager.model.Page;

import java.util.UUID;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.dialogs.wizard.initpod.InitActionFragment;

/**
 * Created by andy on 29/11/2019
 */
public class RemoveActionFragment extends InitActionFragment implements PodInitReceiver {
    private static final String ARG_KEY = "key";

    public static RemoveActionFragment create(String key, PodInitActionType podInitActionType) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);

        RemoveActionFragment fragment = new RemoveActionFragment();
        fragment.setArguments(args);
        fragment.setPodInitActionType(podInitActionType);
        return fragment;
    }

    public RemoveActionFragment() {
        this.instance = this;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
    }

    public void actionOnReceiveResponse(String result) {
        System.out.println("ACTION: actionOnReceiveResponse: " + result);

        boolean isOk = callResult.success;

        progressBar.setVisibility(View.GONE);

        if (!isOk) {
            errorView.setVisibility(View.VISIBLE);
            errorView.setText(callResult.comment);

            retryButton.setVisibility(View.VISIBLE);
        }

        mPage.setActionCompleted(isOk);

        mPage.getData().putString(Page.SIMPLE_DATA_KEY, UUID.randomUUID().toString());
        mPage.notifyDataChanged();
    }


    @Override
    public void returnInitTaskStatus(PodInitActionType podInitActionType, boolean isSuccess, String errorMessage) {
        if (podInitActionType.isParent()) {
            for (PodInitActionType actionType : mapCheckBoxes.keySet()) {
                setCheckBox(actionType, isSuccess);
            }
        } else {
            setCheckBox(podInitActionType, isSuccess);
        }
    }


}
