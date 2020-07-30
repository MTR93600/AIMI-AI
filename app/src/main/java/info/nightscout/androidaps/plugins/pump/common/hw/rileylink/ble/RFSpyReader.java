package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble;

import android.os.AsyncTask;
import android.os.SystemClock;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;

/**
 * Created by geoff on 5/26/16.
 */
public class RFSpyReader {

    private final AAPSLogger aapsLogger;
    private static AsyncTask<Void, Void, Void> readerTask;
    private RileyLinkBLE rileyLinkBle;
    private Semaphore waitForRadioData = new Semaphore(0, true);
    private LinkedBlockingQueue<byte[]> mDataQueue = new LinkedBlockingQueue<>();
    private int acquireCount = 0;
    private int releaseCount = 0;
    private boolean stopAtNull = true;


    RFSpyReader(AAPSLogger aapsLogger, RileyLinkBLE rileyLinkBle) {
        this.rileyLinkBle = rileyLinkBle;
        this.aapsLogger = aapsLogger;
    }


    public void setRileyLinkBle(RileyLinkBLE rileyLinkBle) {
        if (readerTask != null) {
            readerTask.cancel(true);
        }
        this.rileyLinkBle = rileyLinkBle;
    }

    void setRileyLinkEncodingType(RileyLinkEncodingType encodingType) {
        stopAtNull = !(encodingType == RileyLinkEncodingType.Manchester || //
                encodingType == RileyLinkEncodingType.FourByteSixByteRileyLink);
    }


    // This timeout must be coordinated with the length of the RFSpy radio operation or Bad Things Happen.
    byte[] poll(int timeout_ms) {
        aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "Entering poll at t==" + SystemClock.uptimeMillis() + ", timeout is " + timeout_ms
                + " mDataQueue size is " + mDataQueue.size());

        if (mDataQueue.isEmpty()) {
            try {
                // block until timeout or data available.
                // returns null if timeout.
                byte[] dataFromQueue = mDataQueue.poll(timeout_ms, TimeUnit.MILLISECONDS);
                if (dataFromQueue != null) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Got data [" + ByteUtil.shortHexString(dataFromQueue) + "] at t=="
                            + SystemClock.uptimeMillis());
                } else {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Got data [null] at t==" + SystemClock.uptimeMillis());
                }
                return dataFromQueue;
            } catch (InterruptedException e) {
                aapsLogger.error(LTag.PUMPBTCOMM, "poll: Interrupted waiting for data");
            }
        }

        return null;
    }


    // Call this from the "response count" notification handler.
    void newDataIsAvailable() {
        releaseCount++;

        aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "waitForRadioData released(count=" + releaseCount + ") at t="
                + SystemClock.uptimeMillis());
        waitForRadioData.release();
    }


    public void start() {
        readerTask = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                UUID serviceUUID = UUID.fromString(GattAttributes.SERVICE_RADIO);
                UUID radioDataUUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA);
                BLECommOperationResult result;
                while (true) {
                    try {
                        acquireCount++;
                        waitForRadioData.acquire();
                        aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "waitForRadioData acquired (count=" + acquireCount + ") at t="
                                + SystemClock.uptimeMillis());
                        SystemClock.sleep(100);
                        SystemClock.sleep(1);
                        result = rileyLinkBle.readCharacteristic_blocking(serviceUUID, radioDataUUID);
                        SystemClock.sleep(100);

                        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
                            if (stopAtNull) {
                                // only data up to the first null is valid
                                for (int i = 0; i < result.value.length; i++) {
                                    if (result.value[i] == 0) {
                                        result.value = ByteUtil.substring(result.value, 0, i);
                                        break;
                                    }
                                }
                            }
                            mDataQueue.add(result.value);
                        } else if (result.resultCode == BLECommOperationResult.RESULT_INTERRUPTED) {
                            aapsLogger.error(LTag.PUMPBTCOMM, "Read operation was interrupted");
                        } else if (result.resultCode == BLECommOperationResult.RESULT_TIMEOUT) {
                            aapsLogger.error(LTag.PUMPBTCOMM, "Read operation on Radio Data timed out");
                        } else if (result.resultCode == BLECommOperationResult.RESULT_BUSY) {
                            aapsLogger.error(LTag.PUMPBTCOMM, "FAIL: RileyLinkBLE reports operation already in progress");
                        } else if (result.resultCode == BLECommOperationResult.RESULT_NONE) {
                            aapsLogger.error(LTag.PUMPBTCOMM, "FAIL: got invalid result code: " + result.resultCode);
                        }
                    } catch (InterruptedException e) {
                        aapsLogger.error(LTag.PUMPBTCOMM, "Interrupted while waiting for data");
                    }
                }
            }
        }.execute();
    }
}
