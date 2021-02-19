package com.yerseg.profiler;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
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
import android.util.Log;

import androidx.annotation.Nullable;
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

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;

public class ProfilingService extends Service {

    public static final String NOTIFICATION_CHANNEL_ID = "com.yerseg.profiler.ProfilingService";
    public static final String PUSH_WIFI_SCAN_WORK_TAG = "com.yerseg.profiler.WIFI_SCAN_WORK";
    public static final String PUSH_BT_SCAN_WORK_TAG = "com.yerseg.profiler.BT_SCAN_WORK";
    public static final String PUSH_APP_STAT_SCAN_WORK_TAG = "com.yerseg.profiler.APP_STAT_SCAN_WORK";

    public static final int WIFI_STATS_UPDATE_FREQ = 5000;
    public static final int BLUETOOTH_STATS_UPDATE_FREQ = 5000;
    public static final int APP_STATS_UPDATE_FREQ = 5000;
    public static final int LOCATION_STATS_UPDATE_FREQ = 5000;

    public static final String PROFILING_STATS_DIRECTORY_NAME = "ProfilingData";
    public static final String PROFILING_STATS_TEMP_DIRECTORY_NAME = "ProfilingDataTemp";

    public static final String APP_STATS_FILE_NAME = "app.data";
    public static final String BLUETOOTH_STATS_FILE_NAME = "bt.data";
    public static final String LOCATION_STATS_FILE_NAME = "location.data";
    public static final String SCREEN_STATE_STATS_FILE_NAME = "screen.data";
    public static final String WIFI_STATS_FILE_NAME = "wifi.data";

    public static final String[] STAT_FILE_NAMES = {
            APP_STATS_FILE_NAME,
            BLUETOOTH_STATS_FILE_NAME,
            LOCATION_STATS_FILE_NAME,
            SCREEN_STATE_STATS_FILE_NAME,
            WIFI_STATS_FILE_NAME
    };

    public static boolean isRunning = false;
    public static boolean isStopping = true;

    private HandlerThread mLocationProfilingThread;
    private HandlerThread mWifiProfilingThread;
    private HandlerThread mBluetoothProfilingThread;

    private Handler mLocationProfilingThreadHandler;
    private Handler mWifiProfilingThreadHandler;
    private Handler mBluetoothProfilingThreadHandler;

    private BroadcastReceiver mScreenStatusBroadcastReceiver;
    private BroadcastReceiver mWifiScanReceiver;
    private BroadcastReceiver mBluetoothBroadcastReceiver;

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

        startScreenStateTracking();
        startLocationTracking();
        startWifiTracking();
        startBluetoothTracking();
        startApplicationsStatisticTracking();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        synchronized (this) {
            isStopping = true;
        }

        Log.d("Profiler [Service]", String.format(Locale.getDefault(), "\t%d\tonDestroy()", Process.myTid()));
        super.onDestroy();

        stopScreenStateTracking();
        stopLocationTracking();
        stopWifiTracking();
        stopBluetoothTracking();
        stopApplicationsStatisticTracking();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(1);

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
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Profiler App is running")
                .setPriority(NotificationManager.IMPORTANCE_MAX)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        startForeground(1, notification);
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
                        Location location = result.getLastLocation();
                        String locationStats = String.format(Locale.getDefault(), "%s,%f,%f,%f,%f,%s\n",
                                Utils.GetTimeStamp(System.currentTimeMillis()),
                                location.getAccuracy(),
                                location.getAltitude(),
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getProvider()
                        );

                        Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), LOCATION_STATS_FILE_NAME, locationStats);
                    }
                };

                HandlerThread thread = new HandlerThread("LocationProfilingThread", Process.THREAD_PRIORITY_FOREGROUND);
                thread.start();

                Looper looper = thread.getLooper();
                mLocationProfilingThreadHandler = new Handler(looper);

                fusedLocationProviderClient.requestLocationUpdates(locationRequest, mLocationCallback, looper);
            }
        }
    }

    private void startWifiTracking() {
        final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mWifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            List<ScanResult> scanResults = wifiManager.getScanResults();

                            String statResponseId = UUID.randomUUID().toString();
                            String timestamp = Utils.GetTimeStamp(System.currentTimeMillis());

                            for (ScanResult result : scanResults) {
                                String wifiStats = String.format(Locale.getDefault(), "%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%s,%d,%s,%b,%b\n",
                                        timestamp,
                                        statResponseId,
                                        result.BSSID,
                                        result.SSID,
                                        result.capabilities,
                                        result.centerFreq0,
                                        result.centerFreq1,
                                        result.channelWidth,
                                        result.frequency,
                                        result.level,
                                        result.operatorFriendlyName.length(),
                                        result.timestamp,
                                        result.venueName,
                                        result.is80211mcResponder(),
                                        result.isPasspointNetwork());

                                Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), WIFI_STATS_FILE_NAME, wifiStats);
                            }
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

                mWifiProfilingThreadHandler.postDelayed(this, 5000);
            }
        });
    }

    private void startBluetoothTracking() {
        mBluetoothBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String bluetoothStats = String.format(Locale.getDefault(), "%s,%s,%s,%d,%d,%d,%d\n",
                                    Utils.GetTimeStamp(System.currentTimeMillis()),
                                    device.getName(),
                                    device.getAddress(),
                                    device.getBluetoothClass().getMajorDeviceClass(),
                                    device.getBluetoothClass().getDeviceClass(),
                                    device.getBondState(),
                                    device.getType());

                            Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), BLUETOOTH_STATS_FILE_NAME, bluetoothStats);
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
                String statResponseId = UUID.randomUUID().toString();
                final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter != null) {
                    if (!bluetoothAdapter.isDiscovering())
                        BluetoothAdapter.getDefaultAdapter().startDiscovery();

                    String timestamp = Utils.GetTimeStamp(System.currentTimeMillis());
                    Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

                    for (BluetoothDevice device : bondedDevices) {
                        String bluetoothStats = String.format(Locale.getDefault(), "%s,%s,BONDED,%s,%s,%d,%d,%d,%d\n",
                                timestamp,
                                statResponseId,
                                device.getName(),
                                device.getAddress(),
                                device.getBluetoothClass().getMajorDeviceClass(),
                                device.getBluetoothClass().getDeviceClass(),
                                device.getBondState(),
                                device.getType());

                        Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), ProfilingService.BLUETOOTH_STATS_FILE_NAME, bluetoothStats);
                    }
                }

                final BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                if (bluetoothManager != null) {
                    String timestamp = Utils.GetTimeStamp(System.currentTimeMillis());
                    List<BluetoothDevice> gattDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);

                    for (BluetoothDevice device : gattDevices) {
                        String bluetoothStats = String.format(Locale.getDefault(), "%s,%s,GATT,%s,%s,%d,%d,%d,%d\n",
                                timestamp,
                                statResponseId,
                                device.getName(),
                                device.getAddress(),
                                device.getBluetoothClass().getMajorDeviceClass(),
                                device.getBluetoothClass().getDeviceClass(),
                                device.getBondState(),
                                device.getType());

                        Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), ProfilingService.BLUETOOTH_STATS_FILE_NAME, bluetoothStats);
                    }

                    timestamp = Utils.GetTimeStamp(System.currentTimeMillis());
                    List<BluetoothDevice> gattServerDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);

                    for (BluetoothDevice device : gattServerDevices) {
                        String bluetoothStats = String.format(Locale.getDefault(), "%s,%s,GATT_SERVER,%s,%s,%d,%d,%d,%d\n",
                                timestamp,
                                statResponseId,
                                device.getName(),
                                device.getAddress(),
                                device.getBluetoothClass().getMajorDeviceClass(),
                                device.getBluetoothClass().getDeviceClass(),
                                device.getBondState(),
                                device.getType());

                        Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), ProfilingService.BLUETOOTH_STATS_FILE_NAME, bluetoothStats);
                    }
                }

                BluetoothLeScanner btScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
                btScanner.startScan(new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                        super.onScanResult(callbackType, result);

                        String resultStr = String.format(Locale.getDefault(), "%s,LE,%d,%d,%d,%d,%b,%b",
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

                mBluetoothProfilingThreadHandler.postDelayed(this, 5000);
            }
        });
    }

    private void startApplicationsStatisticTracking() {
        OneTimeWorkRequest refreshWork = new OneTimeWorkRequest.Builder(ApplicationsProfilerWorker.class).build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(PUSH_APP_STAT_SCAN_WORK_TAG, ExistingWorkPolicy.KEEP, refreshWork);
    }

    private void startScreenStateTracking() {
        mScreenStatusBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String intentActionName = "";

                if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                    intentActionName = "ACTION_USER_PRESENT";
                } else if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
                    intentActionName = "ACTION_SHUTDOWN";
                } else if (intent.getAction().equals(Intent.ACTION_DREAMING_STARTED)) {
                    intentActionName = "ACTION_DREAMING_STARTED";
                } else if (intent.getAction().equals(Intent.ACTION_DREAMING_STOPPED)) {
                    intentActionName = "ACTION_DREAMING_STOPPED";
                } else if (intent.getAction().equals(Intent.ACTION_REBOOT)) {
                    intentActionName = "ACTION_REBOOT";
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    intentActionName = "ACTION_SCREEN_OFF";
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    intentActionName = "ACTION_SCREEN_ON";
                } else if (intent.getAction().equals(Intent.ACTION_USER_UNLOCKED)) {
                    intentActionName = "ACTION_USER_UNLOCKED";
                }

                final String finalIntentActionName = intentActionName;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String screenStats = String.format(Locale.getDefault(), "%s,%s\n", Utils.GetTimeStamp(System.currentTimeMillis()), finalIntentActionName);
                        Utils.FileWriter.writeFile(Utils.getProfilingFilesDir(getApplicationContext()), SCREEN_STATE_STATS_FILE_NAME, screenStats);
                    }
                }).run();
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        intentFilter.addAction(Intent.ACTION_DREAMING_STARTED);
        intentFilter.addAction(Intent.ACTION_DREAMING_STOPPED);
        intentFilter.addAction(Intent.ACTION_REBOOT);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_USER_UNLOCKED);

        registerReceiver(mScreenStatusBroadcastReceiver, intentFilter);
    }

    void stopScreenStateTracking() {
        if (mScreenStatusBroadcastReceiver != null)
            unregisterReceiver(mScreenStatusBroadcastReceiver);
    }

    void stopLocationTracking() {
        FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (fusedLocationProviderClient != null) {
            fusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
        }

        mLocationProfilingThreadHandler.removeCallbacksAndMessages(null);
        mLocationProfilingThread.quitSafely();
    }

    void stopWifiTracking() {
        if (mWifiScanReceiver != null)
            unregisterReceiver(mWifiScanReceiver);

        mWifiProfilingThreadHandler.removeCallbacksAndMessages(null);
        mWifiProfilingThread.quitSafely();
    }

    void stopBluetoothTracking() {
        if (mBluetoothBroadcastReceiver != null)
            unregisterReceiver(mBluetoothBroadcastReceiver);

        mBluetoothProfilingThreadHandler.removeCallbacksAndMessages(null);
        mBluetoothProfilingThread.quitSafely();
    }

    void stopApplicationsStatisticTracking() {
        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(PUSH_APP_STAT_SCAN_WORK_TAG);
    }
}