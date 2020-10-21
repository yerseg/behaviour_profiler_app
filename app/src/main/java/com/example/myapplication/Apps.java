package com.example.myapplication;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;

public class Apps  {
    public void startProfiling()
    {

    }

    public void stopProfiling()
    {

    }

    private List<ActivityManager.RunningAppProcessInfo> getRunningAppProcessInfo()
    {
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        return am.getRunningAppProcesses();
    }

    Context mContext;
}
