package com.yerseg.profiler;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity {

    private final static int PERMISSIONS_REQUEST_ID = 1001;

    public static final String ACTION_LOCATION_SCANNING_SETTINGS = "android.settings.LOCATION_SCANNING_SETTINGS";

    private final static String LOCATION_SOURCE_SETTINGS_SHOWN = "com.yerseg.profiler.LOCATION_SOURCE_SETTINGS_SHOWN";
    private final static String IGNORE_BATTERY_OPTIMIZATION_SETTINGS_SHOWN = "com.yerseg.profiler.IGNORE_BATTERY_OPTIMIZATION_SETTINGS_SHOWN";
    private final static String APPLICATION_DETAILS_SETTINGS_SHOWN = "com.yerseg.profiler.APPLICATION_DETAILS_SETTINGS_SHOWN";
    private final static String LOCATION_SCANNING_SETTINGS_SHOWN = "com.yerseg.profiler.LOCATION_SCANNING_SETTINGS_SHOWN";
    private final static String REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_SHOWN = "com.yerseg.profiler.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_SHOWN";

    Intent mProfilingServiceIntent;
    boolean mIsPermissionsGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);

        Button startButton = findViewById(R.id.profilingStartButton);
        Button stopButton = findViewById(R.id.profilingStopButton);

        startButton.setOnClickListener(v -> {
            if (!mIsPermissionsGranted) {
                showLongToast("Grant permission please!");
                requestPermissions();
                return;
            }

            // Common location settings
            if (shouldShowSettingsActivity(LOCATION_SOURCE_SETTINGS_SHOWN)) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                showLongToast("Turn on all location services please!");
                startActivityForResult(intent, 1);
                markSettingsActivityShown(LOCATION_SOURCE_SETTINGS_SHOWN);
                return;
            }

            if (shouldShowSettingsActivity(REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_SHOWN)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                showLongToast("Allow the app to ignore battery optimizations please!");
                startActivityForResult(intent, 1);
                markSettingsActivityShown(REQUEST_IGNORE_BATTERY_OPTIMIZATIONS_SHOWN);
                return;
            }

            // App settings
            if (shouldShowSettingsActivity(APPLICATION_DETAILS_SETTINGS_SHOWN)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                showLongToast("Turn off battery optimizations for app please!");
                startActivityForResult(intent, 1);
                markSettingsActivityShown(APPLICATION_DETAILS_SETTINGS_SHOWN);
                return;
            }

            // Two switch
            if (shouldShowSettingsActivity(LOCATION_SCANNING_SETTINGS_SHOWN)) {
                Intent intent = new Intent(ACTION_LOCATION_SCANNING_SETTINGS);
                showLongToast("Turn on all switches please!");
                startActivityForResult(intent, 1);
                markSettingsActivityShown(LOCATION_SCANNING_SETTINGS_SHOWN);
                return;
            }

            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) {
                showLongToast("Turn on WiFi please!");
                return;
            }

            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                showLongToast("Turn on Bluetooth please!");
                return;
            }

            LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.isLocationEnabled() ||
                    !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                showLongToast("Turn on location services please!");
                return;
            }

            startService();
            // Can crash when click on stop button before service completely start
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        });

        stopButton.setOnClickListener(v -> {
            stopService();
            // Can crash when click on start button before service completely stop
            stopButton.setEnabled(false);
            startButton.setEnabled(true);
        });

        TextView textView = findViewById(R.id.textInstruction);
        textView.setVisibility(View.VISIBLE);

        ProgressBar sendZipProgressBar = findViewById(R.id.sendZipProgressBar);

        FloatingActionButton emailSendButton = findViewById(R.id.SendDataByEmailButton);
        emailSendButton.setOnClickListener(v -> {
            sendZipProgressBar.setVisibility(View.VISIBLE);
            emailSendButton.setEnabled(false);
            new Thread(() -> {
                onSendButtonClick();
                runOnUiThread(() -> {
                    sendZipProgressBar.setVisibility(View.GONE);
                    emailSendButton.setEnabled(true);
                });
            }).start();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        Button startButton = findViewById(R.id.profilingStartButton);
        startButton.setEnabled(!isProfilingServiceRunning());

        Button stopButton = findViewById(R.id.profilingStopButton);
        stopButton.setEnabled(isProfilingServiceRunning());

        requestPermissions();
    }

    private void startService() {
        Intent mProfilingServiceIntent = new Intent(this, ProfilingService.class).putExtra("inputExtra", "ServiceControl");
        ContextCompat.startForegroundService(this, mProfilingServiceIntent);
    }

    private void stopService() {
        if (mProfilingServiceIntent == null)
            mProfilingServiceIntent = new Intent(this, ProfilingService.class).putExtra("inputExtra", "ServiceControl");

        stopService(mProfilingServiceIntent);
    }

    private boolean isProfilingServiceRunning() {
        boolean isRunning = false;
        synchronized (this) {
            isRunning = ProfilingService.isRunning;
        }
        return isRunning;
    }

    private boolean isUsageStatsPermissionsGranted() {
        boolean granted = false;
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());

        if (mode == AppOpsManager.MODE_DEFAULT) {
            granted = (checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);
        } else {
            granted = (mode == AppOpsManager.MODE_ALLOWED);
        }

        return granted;
    }

    void requestPermissions() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.PACKAGE_USAGE_STATS,
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        }, PERMISSIONS_REQUEST_ID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        mIsPermissionsGranted = requestCode == PERMISSIONS_REQUEST_ID && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    private boolean shouldShowSettingsActivity(String preferencesKeyName) {
        SharedPreferences prefs = getSharedPreferences(getApplicationContext().getPackageName(), Context.MODE_PRIVATE);
        return !prefs.getBoolean(preferencesKeyName, false);
    }

    private void markSettingsActivityShown(String preferencesKeyName) {
        SharedPreferences prefs = getSharedPreferences(getApplicationContext().getPackageName(), Context.MODE_PRIVATE);
        prefs.edit().putBoolean(preferencesKeyName, true).apply();
    }

    private void showLongToast(String text) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show());
    }

    private void moveDataFilesToTempDirectory(String[] dataFilesNames) {
        for (String fileName : dataFilesNames) {
            try {
                Utils.moveFile(new File(Utils.getProfilingFilesDir(getApplicationContext()), fileName), new File(Utils.getTempDataFilesDir(getApplicationContext()), fileName));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private boolean deleteTempFiles() {
        boolean isAllFilesDeleted = true;
        File tempDir = Utils.getTempDataFilesDir(getApplicationContext());

        if (tempDir.exists()) {
            File[] tempFiles = tempDir.listFiles();
            if (tempFiles != null) {
                for (File tempFile : tempFiles) {
                    if (tempFile.exists()) {
                        if (!Utils.deleteFile(tempFile)) {
                            isAllFilesDeleted = false;
                        }
                    }
                }
            }
        }

        return !isAllFilesDeleted;
    }

    private void onSendButtonClick() {
        Log.d("Profiler [MainActivity]", String.format(Locale.getDefault(), "\t%d\tonSendButtonClick()", Process.myTid()));

        try {
            if (deleteTempFiles()) {
                Thread.sleep(300);
                if (deleteTempFiles()) {
                    Log.d("Profiler [MainActivity]", String.format(
                            Locale.getDefault(), "\t%d\tonSendButtonClick(), Msg: \"%s\"", Process.myTid(), "Temp directory is not clean!"));
                }
            }

            File tempDir = Utils.getTempDataFilesDir(getApplicationContext());

            try {
                moveDataFilesToTempDirectory(ProfilingService.STAT_FILE_NAMES);
            } catch (Exception e) {
                e.printStackTrace();
            }

            List<File> filesList = new LinkedList<>();

            for (String fileName : ProfilingService.STAT_FILE_NAMES) {
                File file = new File(tempDir, fileName);
                if (file.exists())
                    filesList.add(file);
            }

            File zip = Utils.createZip(filesList, tempDir);

            if (zip.exists()) {
                Intent sendStatsIntent = new Intent(Intent.ACTION_SEND);

                String[] to = {"cergei.kazmin@gmail.com"};
                Uri contentUri = FileProvider.getUriForFile(getApplicationContext(), "com.yerseg.profiler", zip);

                sendStatsIntent.setType("application/zip");
                sendStatsIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sendStatsIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                sendStatsIntent.putExtra(Intent.EXTRA_EMAIL, to);
                sendStatsIntent.putExtra(Intent.EXTRA_SUBJECT, "[IMP] Profiling stats");
                sendStatsIntent.putExtra(Intent.EXTRA_TEXT, "Sending profiling stats");
                sendStatsIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(sendStatsIntent, "Send stats");
                List<ResolveInfo> resolveInfoList = this.getPackageManager().queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resolveInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    this.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(ProfilingService.REMINDER_NOTIFICATION_ID);

                startActivity(chooser);
            }
        } catch (Exception ex) {
            showLongToast("ERROR! Sending failed! Try again!");
            ex.printStackTrace();
        }
    }
}
