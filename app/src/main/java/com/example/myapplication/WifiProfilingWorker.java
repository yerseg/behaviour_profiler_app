package com.example.myapplication;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CancellationException;

public class WifiProfilingWorker extends Worker {
    Context mContext;

    public WifiProfilingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @NonNull
    @Override
    public Result doWork() {
        try {
            Thread.sleep(5000);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        doActualwork();
        return Result.success();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void doActualwork() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.startScan();

        try {
            OneTimeWorkRequest refreshWork = new OneTimeWorkRequest.Builder(WifiProfilingWorker.class).build();
            WorkManager.getInstance(mContext).enqueueUniqueWork(ProfilingService.PUSH_WIFI_SCAN_WORK_TAG, ExistingWorkPolicy.REPLACE, refreshWork);
        } catch (CancellationException ex) {
            ex.printStackTrace();
        }

    }


}