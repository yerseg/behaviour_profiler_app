package com.yerseg.profiler;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;

public class MainActivity extends FragmentActivity {

    private final static int REQUEST_ENABLE_BT = 1;
    private final static int PERMISSIONS_REQUEST_ID = 1001;

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

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isUsageStatsPermissionsGranted())
                {
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

        FloatingActionButton sendDataByEmailButton = findViewById(R.id.SendDataByEmailButton);
        sendDataByEmailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        TextView textView = findViewById(R.id.textInstruction);
        textView.setVisibility(View.VISIBLE);

        FloatingActionButton emailSendButton = findViewById(R.id.SendDataByEmailButton);
        emailSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendFilesByEmail();
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

    private boolean isUsageStatsPermissionsGranted()
    {
        boolean granted = false;
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());

        if (mode == AppOpsManager.MODE_DEFAULT) {
            granted = (checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);
        } else {
            granted = (mode == AppOpsManager.MODE_ALLOWED);
        }

        return  granted;
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
        }
        else {
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

    private File getProfilingFilesDir() {
        File filesDirFile = getApplicationContext().getFilesDir();

        File directoryFile = new File(filesDirFile, ProfilingService.PROFILING_STATS_DIRECTORY_NAME);
        if (!directoryFile.exists()) {
            directoryFile.mkdir();
        }

        return directoryFile;
    }

    private void sendFilesByEmail() {
        String[] dataFilesNames = {
                ProfilingService.APP_STATS_FILE_NAME,
                ProfilingService.BLUETOOTH_STATS_FILE_NAME,
                ProfilingService.LOCATION_STATS_FILE_NAME,
                ProfilingService.WIFI_STATS_FILE_NAME
        };

        File directory = getProfilingFilesDir();
        if (directory.exists())
        {
            for (String fileName : dataFilesNames)
            {
                File file = new File(directory, fileName);
                if (file.exists())
                {
                    Uri path = Uri.fromFile(file);
                    Intent emailIntent = new Intent(Intent.ACTION_SEND);

                    emailIntent.setType("vnd.android.cursor.dir/email");
                    String to[] = {"cergei.kazmin@gmail.com"};
                    emailIntent.putExtra(Intent.EXTRA_EMAIL, to);

                    emailIntent.putExtra(Intent.EXTRA_STREAM, path);

                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Subject");
                    startActivity(Intent.createChooser(emailIntent , "Send email..."));
                }
            }
        }
    }
}