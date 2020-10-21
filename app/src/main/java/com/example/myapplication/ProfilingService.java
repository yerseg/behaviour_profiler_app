package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Locale;

public class ProfilingService extends Service {

    public static final String NOTIFICATION_CHANNEL_ID = "ProfilingService";
    static String PUSH_WIFI_SCAN_WORK_TAG = "WIFI_SCAN_WORK";

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        requestLocationUpdates();
        final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.registerScanResultsCallback(getMainExecutor(), new WifiManager.ScanResultsCallback() {
            @Override
            public void onScanResultsAvailable() {
                List<ScanResult> scanResults = wifiManager.getScanResults();
                for (ScanResult result : scanResults) {
                    String resultStr = String.format(Locale.getDefault(), "%s,%s,%s,%d,%d,%d,%d,%d,%s,%d,%s,%d,%b,%b",
                            result.BSSID,
                            result.SSID,
                            result.capabilities,
                            result.centerFreq0,
                            result.centerFreq1,
                            result.channelWidth,
                            result.frequency,
                            result.level,
                            result.operatorFriendlyName,
                            result.timestamp,
                            result.venueName,
                            result.getWifiStandard(),
                            result.is80211mcResponder(),
                            result.isPasspointNetwork());
                    writeFileOnInternalStorage("wifi.data", resultStr);
                    Log.d("OUT", resultStr);
                }
            }
        });

        OneTimeWorkRequest refreshWork = new OneTimeWorkRequest.Builder(WifiProfilingWorker.class).build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(PUSH_WIFI_SCAN_WORK_TAG, ExistingWorkPolicy.KEEP, refreshWork);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Profiling Service Notification Channel",
                NotificationManager.IMPORTANCE_HIGH);

        notificationChannel.setLightColor(Color.BLUE);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("title")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        startForeground(1, notification);
    }

    private void requestLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult result) {
                    Location location = result.getLastLocation();
                    Log.d("Location Service:", String.format("location update, accuracy =  %f, altitude = %f, latitude = %f, longitude = %f, provider = %s",
                            location.getAccuracy(),
                            location.getAltitude(),
                            location.getLatitude(),
                            location.getLongitude(),
                            location.getProvider()
                    ));
                }
            }, Looper.myLooper());
        }
    }

    private void writeFileOnInternalStorage(String fileName, String body) {
        File directory = new File(getApplicationContext().getFilesDir(), "ProfilingData");
        if (!directory.exists()) {
            directory.mkdir();
        }

        try {
            File file = new File(directory, fileName);
            FileWriter writer = new FileWriter(file);

            MutexHolder.getMutex().lock();
            writer.append(body);
            writer.flush();
            MutexHolder.getMutex().unlock();

            writer.close();
        } catch (Exception e) {
            MutexHolder.getMutex().unlock();
            e.printStackTrace();
        }
    }
}
