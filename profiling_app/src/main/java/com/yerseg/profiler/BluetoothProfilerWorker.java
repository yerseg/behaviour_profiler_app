package com.yerseg.profiler;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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

            if (!ProfilingService.isStopping) {
                try {
                    OneTimeWorkRequest refreshWork = new OneTimeWorkRequest.Builder(BluetoothProfilerWorker.class).build();
                    WorkManager.getInstance(mContext).enqueueUniqueWork(ProfilingService.PUSH_BT_SCAN_WORK_TAG, ExistingWorkPolicy.REPLACE, refreshWork);
                } catch (CancellationException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
