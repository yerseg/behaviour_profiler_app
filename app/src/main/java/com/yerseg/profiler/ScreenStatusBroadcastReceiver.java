package com.yerseg.profiler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ScreenStatusBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
            Log.d("ScreenState", "TURN ON!");
        }
        else if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
            Log.d("ScreenState", "TURN OFF!");
        }
    }
}
