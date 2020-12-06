package com.yerseg.profiler;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CancellationException;

public class BluetoothProfilerWorker extends Worker {

    Context mContext;

    public BluetoothProfilerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Thread.sleep(ProfilingService.BLUETOOTH_STATS_UPDATE_FREQ);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        doActualWork();
        return Result.success();
    }

    private void doActualWork() {
        if (!BluetoothAdapter.getDefaultAdapter().isDiscovering())
            BluetoothAdapter.getDefaultAdapter().startDiscovery();

        /*BluetoothLeScanner btScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        btScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                String resultStr = String.format(Locale.getDefault(), "%d,%d,%d,%d,%b,%b",
                        result.getAdvertisingSid(),
                        result.getDataStatus(),
                        result.getRssi(),
                        result.getTxPower(),
                        result.isConnectable(),
                        result.isLegacy());

                writeFileOnInternalStorage("bt.data", resultStr);
            }
        });*/

        try {
            OneTimeWorkRequest refreshWork = new OneTimeWorkRequest.Builder(BluetoothProfilerWorker.class).build();
            WorkManager.getInstance(mContext).enqueueUniqueWork(ProfilingService.PUSH_BT_SCAN_WORK_TAG, ExistingWorkPolicy.REPLACE, refreshWork);
        } catch (CancellationException ex) {
            ex.printStackTrace();
        }
    }

    /*private void writeFileOnInternalStorage(String fileName, String body) {
        Log.d("PS_MON", String.format(Locale.getDefault(), "Profiling Service: writeFileOnInternalStorage(), thread = %d", Process.myTid()));
        File directory = new File(getApplicationContext().getFilesDir(), "ProfilingData");
        if (!directory.exists()) {
            directory.mkdir();
        }

        try {
            File file = new File(directory, fileName);
            FileWriter writer = new FileWriter(file, true);

            Date d = new Date();
            SimpleDateFormat format;
            format = new SimpleDateFormat("dd.MM.yyyy hh:mm:ss");

            MutexHolder.getMutex().lock();
            writer.append(format.format(d).concat(";"));
            writer.append(body.concat("\n"));
            writer.flush();
            MutexHolder.getMutex().unlock();

            writer.close();
        } catch (Exception e) {
            MutexHolder.getMutex().unlock();
            e.printStackTrace();
        }
    }*/
}
