package info.nightscout.androidaps.plugins.pump.common.hw.medlink.ble;

import android.os.AsyncTask;
import android.os.SystemClock;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.plugins.pump.common.hw.medlink.defs.MedLinkEncodingType;
import info.nightscout.pump.core.utils.ByteUtil;
import info.nightscout.pump.core.utils.ThreadUtil;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;

/**
 * Created by Dirceu on 06/10/20.
 */
public class MedLinkRFSpyReader {
    private final AAPSLogger aapsLogger;
    private static AsyncTask<Void, Void, Void> readerTask;
    private MedLinkBLE medLinkBle;
    private Semaphore waitForRadioData = new Semaphore(0, true);
    private LinkedBlockingQueue<byte[]> mDataQueue = new LinkedBlockingQueue<>();
    private int acquireCount = 0;
    private int releaseCount = 0;
    private boolean stopAtNull = true;
    private static boolean isRunning = false;


    MedLinkRFSpyReader(AAPSLogger aapsLogger, MedLinkBLE medLinkBle) {
        this.aapsLogger = aapsLogger;
        // xyz setRileyLinkBle(medLinkBle);
        this.medLinkBle = medLinkBle;
    }


    public void setMedLinkBle(MedLinkBLE medLinkBle) {
        if (readerTask != null) {
            readerTask.cancel(true);
        }
        this.medLinkBle = medLinkBle;
    }

    void setMedLinkEncodingType(MedLinkEncodingType encodingType) {
        aapsLogger.debug("setRileyLinkEncodingType: " + encodingType);
//        stopAtNull = !(encodingType == RileyLinkEncodingType.Manchester || //
//                encodingType == MedLinkEncodingType.FourByteSixByteLocal);
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
        isRunning = true;

        aapsLogger.debug(LTag.PUMPBTCOMM, "RFSpyReader starting");
//        readerTask = new AsyncTask<Void, Void, Void>() {
//
//            @Override
//            protected Void doInBackground(Void... voids) {
//                UUID serviceUUID = UUID.fromString(GattAttributes.SERVICE_UUID);
//                UUID radioDataUUID = UUID.fromString(GattAttributes.GATT_UUID);
//                BLECommOperationResult result;
//                while (isRunning) {
//                    try {
//                        acquireCount++;
//                        aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "waitForRadioData before acquired (count=" + acquireCount + ") at t="
//                                + SystemClock.uptimeMillis());
//                        waitForRadioData.acquire();
//                        aapsLogger.debug(LTag.PUMPBTCOMM, ThreadUtil.sig() + "waitForRadioData acquired (count=" + acquireCount + ") at t="
//                                + SystemClock.uptimeMillis());
//                        SystemClock.sleep(100);
//                        SystemClock.sleep(1);
//                        result = medLinkBle.readCharacteristicBlocking(serviceUUID, radioDataUUID);
//                        SystemClock.sleep(100);
//
//                        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
//                            if (stopAtNull) {
//                                // only data up to the first null is valid
//                                for (int i = 0; i < result.value.length; i++) {
//                                    if (result.value[i] == 0) {
//                                        result.value = ByteUtil.substring(result.value, 0, i);
//                                        break;
//                                    }
//                                }
//                            }
//                            mDataQueue.add(result.value);
//                        } else if (result.resultCode == BLECommOperationResult.RESULT_INTERRUPTED) {
//                            aapsLogger.error(LTag.PUMPBTCOMM, "Read operation was interrupted");
//                        } else if (result.resultCode == BLECommOperationResult.RESULT_TIMEOUT) {
//                            aapsLogger.error(LTag.PUMPBTCOMM, "Read operation on Radio Data timed out");
//                        } else if (result.resultCode == BLECommOperationResult.RESULT_BUSY) {
//                            aapsLogger.error(LTag.PUMPBTCOMM, "FAIL: RileyLinkBLE reports operation already in progress");
//                        } else if (result.resultCode == BLECommOperationResult.RESULT_NONE) {
//                            aapsLogger.error(LTag.PUMPBTCOMM, "FAIL: got invalid result code: " + result.resultCode);
//                        }
//                    } catch (InterruptedException e) {
//                        aapsLogger.error(LTag.PUMPBTCOMM, "Interrupted while waiting for data");
//                    }
//                }
//                return null;
//            }
//        }.execute();
    }

    public void stop() {
        isRunning = false;
    }

}
