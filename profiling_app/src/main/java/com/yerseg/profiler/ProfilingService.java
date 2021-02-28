package com.yerseg.profiler;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.EventStats;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProfilingService extends Service {

    public static final String NOTIFICATION_CHANNEL_ID = "com.yerseg.profiler.ProfilingService";

    public static final String PUSH_REMINDER_NOTIFICATION_WORK_TAG = "com.yerseg.profiler.REMINDER_NOTIFICATION_WORK";

    public static final int SERVICE_NOTIFICATION_ID = 1;
    public static final int REMINDER_NOTIFICATION_ID = 2;

    public static final int WIFI_STATS_UPDATE_FREQ = 5000;
    public static final int BLUETOOTH_STATS_UPDATE_FREQ = 5000;
    public static final int APP_STATS_UPDATE_FREQ = 5000;
    public static final int LOCATION_STATS_UPDATE_FREQ = 5000;

    public static final String PROFILING_STATS_DIRECTORY_NAME = "ProfilingData";
    public static final String PROFILING_STATS_TEMP_DIRECTORY_NAME = "ProfilingDataTemp";

    public static final String APP_STATS_FILE_NAME = "app.data";
    public static final String BLUETOOTH_STATS_FILE_NAME = "bt.data";
    public static final String LOCATION_STATS_FILE_NAME = "location.data";
    public static final String WIFI_STATS_FILE_NAME = "wifi.data";
    public static final String BROADCASTS_STATS_FILE_NAME = "broadcasts.data";

    public static final String[] STAT_FILE_NAMES = {
            APP_STATS_FILE_NAME,
            BLUETOOTH_STATS_FILE_NAME,
            LOCATION_STATS_FILE_NAME,
            WIFI_STATS_FILE_NAME,
            BROADCASTS_STATS_FILE_NAME
    };

    public static boolean isRunning = false;
    public static boolean isStopping = true;

    private HandlerThread mLocationProfilingThread;
    private HandlerThread mWifiProfilingThread;
    private HandlerThread mBluetoothProfilingThread;
    private HandlerThread mApplicationProfilingThread;

    private Handler mLocationProfilingThreadHandler;
    private Handler mWifiProfilingThreadHandler;
    private Handler mBluetoothProfilingThreadHandler;
    private Handler mApplicationProfilingThreadHandler;

    private BroadcastReceiver mWifiScanReceiver;
    private BroadcastReceiver mBluetoothBroadcastReceiver;
    private BroadcastReceiver mAnyBroadcastReceiver;

    private LocationCallback mLocationCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("Profiler [Service]", String.format(Locale.getDefault(), "\t%d\tonCreate()", Process.myTid()));

        synchronized (this) {
            isRunning = true;
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d("Profiler [Service]", String.format(Locale.getDefault(), "\t%d\tonStartCommand()", Process.myTid()));

        startLocationTracking();
        startWifiTracking();
        startBluetoothTracking();
        startApplicationsStatisticTracking();
        startAnyBroadcastsTracking();

        PeriodicWorkRequest notifyWorkRequest = new PeriodicWorkRequest.Builder(ReminderNotificationPeriodicWorker.class, Duration.ofHours(2)).setInitialDelay(Duration.ofMinutes(5)).build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(PUSH_REMINDER_NOTIFICATION_WORK_TAG, ExistingPeriodicWorkPolicy.REPLACE, notifyWorkRequest);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        synchronized (this) {
            isStopping = true;
        }

        Log.d("Profiler [Service]", String.format(Locale.getDefault(), "\t%d\tonDestroy()", Process.myTid()));
        super.onDestroy();

        stopLocationTracking();
        stopWifiTracking();
        stopBluetoothTracking();
        stopApplicationsStatisticTracking();
        stopAnyBroadcastsTracking();

        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(PUSH_REMINDER_NOTIFICATION_WORK_TAG);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(SERVICE_NOTIFICATION_ID);
        notificationManager.cancel(REMINDER_NOTIFICATION_ID);

        synchronized (this) {
            isRunning = false;
            isStopping = false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Profiler [Service]", String.format(Locale.getDefault(), "\t%d\tonBind()", Process.myTid()));
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Profiling Service",
                NotificationManager.IMPORTANCE_HIGH);

        notificationChannel.setLightColor(Color.BLUE);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder
                .setOngoing(true)
                .setContentTitle("Profiler")
                .setContentText("Profiling service is running and collecting statistics")
                .setPriority(NotificationManager.IMPORTANCE_MAX)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    private void startLocationTracking() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(LOCATION_STATS_UPDATE_FREQ);
        locationRequest.setFastestInterval(LOCATION_STATS_UPDATE_FREQ / 10);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (fusedLocationProviderClient != null) {
            int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if (permission == PackageManager.PERMISSION_GRANTED) {
                mLocationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult result) {
                        Log.d("Profiler [LocationStat]", String.format(Locale.getDefault(), "\t%d\tonLocationResult()", Process.myTid()));

                        try {
                            Location location = result.getLastLocation();
                            String locationStats = String.format(Locale.getDefault(), "%s;%f;%f;%f;%f\n",
                                    Utils.GetTimeStamp(System.currentTimeMillis()),
                                    location.getAccuracy(),
                                    location.getAltitude(),
                                    location.getLatitude(),
                                    location.getLongitude()
                            );

                            Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), LOCATION_STATS_FILE_NAME, locationStats);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                };

                mLocationProfilingThread = new HandlerThread("LocationProfilingThread", Process.THREAD_PRIORITY_FOREGROUND);
                mLocationProfilingThread.start();

                Looper looper = mLocationProfilingThread.getLooper();
                mLocationProfilingThreadHandler = new Handler(looper);

                fusedLocationProviderClient.requestLocationUpdates(locationRequest, mLocationCallback, looper);
            }
        }
    }

    private void startWifiTracking() {
        mWifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                    new Thread(() -> {
                        try {
                            final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            List<ScanResult> scanResults = wifiManager.getScanResults();

                            String statResponseId = UUID.randomUUID().toString();
                            String timestamp = Utils.GetTimeStamp(System.currentTimeMillis());

                            for (ScanResult result : scanResults) {
                                String wifiStats = String.format(Locale.getDefault(), "%s;%s;%s;%d;%d;%d\n",
                                        timestamp,
                                        statResponseId,
                                        result.BSSID,
                                        result.channelWidth,
                                        result.frequency,
                                        result.level);

                                Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), WIFI_STATS_FILE_NAME, wifiStats);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).start();
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mWifiScanReceiver, intentFilter);

        mWifiProfilingThread = new HandlerThread("WifiProfilingThread", Process.THREAD_PRIORITY_FOREGROUND);
        mWifiProfilingThread.start();

        Looper looper = mWifiProfilingThread.getLooper();
        mWifiProfilingThreadHandler = new Handler(looper);

        mWifiProfilingThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    //noinspection deprecation
                    wifiManager.startScan();

                    final String timestamp = Utils.GetTimeStamp(System.currentTimeMillis());
                    final WifiInfo currentInfo = wifiManager.getConnectionInfo();

                    final String connectionInfo = String.format(Locale.getDefault(), "%s;CONN;%s;%d;%d;%d;%d;%d;%s\n",
                            timestamp,
                            currentInfo.getBSSID(),
                            currentInfo.getFrequency(),
                            currentInfo.getIpAddress(),
                            currentInfo.getLinkSpeed(),
                            currentInfo.getNetworkId(),
                            currentInfo.getRssi(),
                            currentInfo.getSSID());

                    Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), ProfilingService.WIFI_STATS_FILE_NAME, connectionInfo);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                mWifiProfilingThreadHandler.postDelayed(this, WIFI_STATS_UPDATE_FREQ);
            }
        });
    }

    private void startBluetoothTracking() {
        mBluetoothBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    new Thread(() -> {
                        try {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            String bluetoothStats = String.format(Locale.getDefault(), "%s;%s;%d;%d;%d;%d\n",
                                    Utils.GetTimeStamp(System.currentTimeMillis()),
                                    device.getAddress(),
                                    device.getBluetoothClass().getMajorDeviceClass(),
                                    device.getBluetoothClass().getDeviceClass(),
                                    device.getBondState(),
                                    device.getType());

                            Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), BLUETOOTH_STATS_FILE_NAME, bluetoothStats);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).start();
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mBluetoothBroadcastReceiver, filter);

        mBluetoothProfilingThread = new HandlerThread("BluetoothProfilingThread", Process.THREAD_PRIORITY_FOREGROUND);
        mBluetoothProfilingThread.start();

        Looper looper = mBluetoothProfilingThread.getLooper();
        mBluetoothProfilingThreadHandler = new Handler(looper);

        mBluetoothProfilingThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                    if (bluetoothAdapter != null)
                        if (!bluetoothAdapter.isDiscovering())
                            BluetoothAdapter.getDefaultAdapter().startDiscovery();

                    BluetoothLeScanner btScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
                    btScanner.startScan(new ScanCallback() {
                        @Override
                        public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                            super.onScanResult(callbackType, result);

                            String resultStr = String.format(Locale.getDefault(), "%s;LE;%d;%d;%d;%d;%b;%b\n",
                                    Utils.GetTimeStamp(System.currentTimeMillis()),
                                    result.getAdvertisingSid(),
                                    result.getDataStatus(),
                                    result.getRssi(),
                                    result.getTxPower(),
                                    result.isConnectable(),
                                    result.isLegacy());

                            Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), ProfilingService.BLUETOOTH_STATS_FILE_NAME, resultStr);
                        }
                    });
                } catch(Exception ex) {
                    ex.printStackTrace();
                }

                mBluetoothProfilingThreadHandler.postDelayed(this, BLUETOOTH_STATS_UPDATE_FREQ);
            }
        });
    }

    private void startApplicationsStatisticTracking() {
        mApplicationProfilingThread = new HandlerThread("AppStatThread", Process.THREAD_PRIORITY_FOREGROUND);
        mApplicationProfilingThread.start();

        Looper looper = mApplicationProfilingThread.getLooper();
        mApplicationProfilingThreadHandler = new Handler(looper);

        mApplicationProfilingThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), ProfilingService.APP_STATS_FILE_NAME, getStatisticsForWritingToFile());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                mApplicationProfilingThreadHandler.postDelayed(this, APP_STATS_UPDATE_FREQ);
            }

            private String getStatisticsForWritingToFile() {
                try {
                    long beginTime = java.lang.System.currentTimeMillis() - SystemClock.elapsedRealtime();
                    long endTime = java.lang.System.currentTimeMillis();

                    UsageStatsManager usageStatsManager = (UsageStatsManager) getApplicationContext().getSystemService(Context.USAGE_STATS_SERVICE);

                    String statResponseId = UUID.randomUUID().toString();
                    String timestamp = Utils.GetTimeStamp(endTime);

                    StringBuilder statistic = new StringBuilder();

                    List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

                    for (UsageStats usageStats : usageStatsList) {
                        statistic.append(String.format(Locale.getDefault(), "%s;%s;UsageStats;%s;%d;%d;%d;%d\n",
                                timestamp,
                                statResponseId,
                                usageStats.getPackageName(),
                                usageStats.getFirstTimeStamp(),
                                usageStats.getLastTimeStamp(),
                                usageStats.getLastTimeUsed(),
                                usageStats.getTotalTimeInForeground()));
                    }

                    List<EventStats> eventStatsList = usageStatsManager.queryEventStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

                    for (EventStats eventStat : eventStatsList) {
                        statistic.append(String.format(Locale.getDefault(), "%s;%s;EventStats;%d;%d;%d;%d;%d;%d\n",
                                timestamp,
                                statResponseId,
                                eventStat.getCount(),
                                eventStat.getEventType(),
                                eventStat.getFirstTimeStamp(),
                                eventStat.getLastTimeStamp(),
                                eventStat.getLastEventTime(),
                                eventStat.getTotalTime()));
                    }

                    return statistic.toString();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return "";
                }
            }
        });
    }

    private void startAnyBroadcastsTracking() {
        mAnyBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String intentType = intent.toString();
                String intentAction = intent.getAction();

                new Thread(() -> {
                    try {
                        String broadcastStats = String.format(Locale.getDefault(), "%s;%s;%s\n",
                                Utils.GetTimeStamp(System.currentTimeMillis()),
                                intentType,
                                intentAction);

                        Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), BROADCASTS_STATS_FILE_NAME, broadcastStats);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
        };

        registerAnyBroadcastReceiver();
    }

    private void stopLocationTracking() {
        final FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (fusedLocationProviderClient != null) {
            fusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
        }

        mLocationProfilingThreadHandler.removeCallbacksAndMessages(null);
        mLocationProfilingThread.quitSafely();
    }

    private void stopWifiTracking() {
        if (mWifiScanReceiver != null)
            unregisterReceiver(mWifiScanReceiver);

        mWifiProfilingThreadHandler.removeCallbacksAndMessages(null);
        mWifiProfilingThread.quitSafely();
    }

    private void stopBluetoothTracking() {
        if (mBluetoothBroadcastReceiver != null)
            unregisterReceiver(mBluetoothBroadcastReceiver);

        mBluetoothProfilingThreadHandler.removeCallbacksAndMessages(null);
        mBluetoothProfilingThread.quitSafely();
    }

    private void stopApplicationsStatisticTracking() {
        mApplicationProfilingThreadHandler.removeCallbacksAndMessages(null);
        mApplicationProfilingThread.quitSafely();
    }

    private void stopAnyBroadcastsTracking() {
        if (mAnyBroadcastReceiver != null)
            unregisterReceiver(mAnyBroadcastReceiver);
    }

    private void registerAnyBroadcastReceiver() {
        registerBroadcastReceiverForActions();
        registerBroadcastReceiverForActionsWithDataType();
        registerBroadcastReceiverForActionsWithSchemes();
    }

    private void registerBroadcastReceiverForActions() {
        IntentFilter intentFilter = new IntentFilter();
        addAllKnownActions(intentFilter);
        registerReceiver(mAnyBroadcastReceiver, intentFilter);
    }

    private void registerBroadcastReceiverForActionsWithDataType() {
        IntentFilter intentFilter = new IntentFilter();

        try {
            intentFilter.addDataType("*/*");
        } catch (Exception ex) {
            Log.d("Profiler [Broadcasts Stat]", "Add data type \"*/*\" failed");
        }

        addAllKnownActions(intentFilter);
        registerReceiver(mAnyBroadcastReceiver, intentFilter);
    }

    private void registerBroadcastReceiverForActionsWithSchemes() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addDataScheme("package");
        intentFilter.addDataScheme("file");
        intentFilter.addDataScheme("geo");
        intentFilter.addDataScheme("market");
        intentFilter.addDataScheme("http");
        intentFilter.addDataScheme("tel");
        intentFilter.addDataScheme("mailto");
        intentFilter.addDataScheme("about");
        intentFilter.addDataScheme("https");
        intentFilter.addDataScheme("ftps");
        intentFilter.addDataScheme("ftp");
        intentFilter.addDataScheme("javascript");

        addAllKnownActions(intentFilter);
        registerReceiver(mAnyBroadcastReceiver, intentFilter);
    }

    private void addAllKnownActions(IntentFilter pIntentFilter) {
        String[] sysBroadcasts = getResources().getStringArray(R.array.anyBroadcasts);
        for (String sysBroadcast : sysBroadcasts) {
            pIntentFilter.addAction(sysBroadcast);
        }
    }
}