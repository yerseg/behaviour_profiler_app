package com.yerseg.profiler;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
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

    private final static int REQUEST_ENABLE_BT = 1;
    private final static int PERMISSIONS_REQUEST_ID = 1001;

    Intent mProfilingServiceIntent;
    boolean mIsPermissionsGranted = false;

    //private ProgressBar sendZipProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);

        Button startButton = findViewById(R.id.profilingStartButton);
        Button stopButton = findViewById(R.id.profilingStopButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isUsageStatsPermissionsGranted()) {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivityForResult(intent, 1);
                }

                startService();
                // Can crash when click on stop button before service completely start
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService();
                // Can crash when click on start button before service completely stop
                stopButton.setEnabled(false);
                startButton.setEnabled(true);
            }
        });

        TextView textView = findViewById(R.id.textInstruction);
        textView.setVisibility(View.VISIBLE);

        ProgressBar sendZipProgressBar = findViewById(R.id.sendZipProgressBar);

        FloatingActionButton emailSendButton = findViewById(R.id.SendDataByEmailButton);
        emailSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendZipProgressBar.setVisibility(View.VISIBLE);
                emailSendButton.setEnabled(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        onSendButtonClick();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                sendZipProgressBar.setVisibility(View.GONE);
                                emailSendButton.setEnabled(true);
                            }
                        });
                    }
                }).start();
            }
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

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        return ProfilingService.isRunning;
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
                Manifest.permission.PACKAGE_USAGE_STATS
        }, PERMISSIONS_REQUEST_ID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1001 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mIsPermissionsGranted = true;
        } else {
            mIsPermissionsGranted = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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

    private void onSendButtonClick() {
        Log.d("Profiler [MainActivity]", String.format(Locale.getDefault(), "\t%d\tonSendButtonClick()", Process.myTid()));

        try {
            File tempDir = Utils.getTempDataFilesDir(getApplicationContext());
            if (tempDir.exists())
                tempDir.delete();

            MutexHolder.getMutex().lock();
            try {
                moveDataFilesToTempDirectory(ProfilingService.STAT_FILE_NAMES);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                MutexHolder.getMutex().unlock();
            }

            List<File> filesList = new LinkedList<File>();

            for (String fileName : ProfilingService.STAT_FILE_NAMES) {
                File file = new File(tempDir, fileName);
                if (file.exists())
                    filesList.add(file);
            }

            File zip = Utils.createZip(filesList, tempDir);

            if (zip.exists()) {
                Intent sendStatsIntent = new Intent(Intent.ACTION_SEND);

                String[] to = { "cergei.kazmin@gmail.com" };
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

                startActivity(chooser);
            }
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), "Sending failed! Try again!", Toast.LENGTH_LONG);
            ex.printStackTrace();
        }
    }
}
