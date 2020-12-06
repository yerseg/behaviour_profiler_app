package com.yerseg.profiler;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;

public class WifiProfilingWorker extends Worker {
    Context mContext;

    public WifiProfilingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Thread.sleep(ProfilingService.WIFI_STATS_UPDATE_FREQ);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        doActualWork();
        return Result.success();
    }

    private void doActualWork() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.startScan();

        String statResponseId = UUID.randomUUID().toString();
        String timestamp = Utils.GetTimeStamp(System.currentTimeMillis());
        WifiInfo currentInfo = wifiManager.getConnectionInfo();

        String connectionInfo = String.format(Locale.getDefault(), "%s,%s,CONN,%s,%d,%b,%d,%d,%s,%d,%d,%s\n",
                timestamp,
                statResponseId,
                currentInfo.getBSSID(),
                currentInfo.getFrequency(),
                currentInfo.getHiddenSSID(),
                currentInfo.getIpAddress(),
                currentInfo.getLinkSpeed(),
                currentInfo.getMacAddress(),
                currentInfo.getNetworkId(),
                currentInfo.getRssi(),
                currentInfo.getSSID());

        Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), ProfilingService.WIFI_STATS_FILE_NAME, connectionInfo);

        if (!ProfilingService.isStopping) {
            try {
                OneTimeWorkRequest refreshWork = new OneTimeWorkRequest.Builder(WifiProfilingWorker.class).build();
                WorkManager.getInstance(mContext).enqueueUniqueWork(ProfilingService.PUSH_WIFI_SCAN_WORK_TAG, ExistingWorkPolicy.REPLACE, refreshWork);
            } catch (CancellationException ex) {
                ex.printStackTrace();
            }
        }
    }
}