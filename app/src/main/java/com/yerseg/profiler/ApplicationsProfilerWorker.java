package com.yerseg.profiler;

import android.app.usage.ConfigurationStats;
import android.app.usage.EventStats;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;

public class ApplicationsProfilerWorker extends Worker {
    Context mContext;

    public ApplicationsProfilerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Thread.sleep(ProfilingService.APP_STATS_UPDATE_FREQ);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        doActualWork();
        return Result.success();
    }

    private void doActualWork() {

        FileWriter.writeFile(getApplicationContext().getFilesDir(), ProfilingService.APP_STATS_FILE_NAME, getStatisticsForWritingToFile());

        try {
            OneTimeWorkRequest refreshWork = new OneTimeWorkRequest.Builder(ApplicationsProfilerWorker.class).build();
            WorkManager.getInstance(mContext).enqueueUniqueWork(ProfilingService.PUSH_APP_STAT_SCAN_WORK_TAG, ExistingWorkPolicy.REPLACE, refreshWork);
        } catch (CancellationException ex) {
            ex.printStackTrace();
        }

    }

    private String getStatisticsForWritingToFile()
    {
        long beginTime = java.lang.System.currentTimeMillis() - SystemClock.elapsedRealtime();
        long endTime = java.lang.System.currentTimeMillis();

        UsageStatsManager usageStatsManager = (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE);

        String statResponseId = UUID.randomUUID().toString();
        String timestamp = ProfilingService.GetTimeStamp(endTime);

        StringBuilder statistic = new StringBuilder();

        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

        for (UsageStats usageStats : usageStatsList)
        {
            statistic.append(String.format(Locale.getDefault(), "UsageStats,%s,%s,%s,%d,%d,%d,%d\n",
                    timestamp,
                    statResponseId,
                    usageStats.getPackageName(),
                    usageStats.getFirstTimeStamp(),
                    usageStats.getLastTimeStamp(),
                    usageStats.getLastTimeUsed(),
                    usageStats.getTotalTimeInForeground()));
        }

        List<ConfigurationStats> configurationStatsList = usageStatsManager.queryConfigurations(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

        for (ConfigurationStats configurationStats : configurationStatsList)
        {
            Configuration configuration = configurationStats.getConfiguration();
            statistic.append(String.format(Locale.getDefault(), "ConfigStats,%s,%s,%d,%d,%d,%d,%d,%d,%d,%f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%b,%b,%b\n",
                    timestamp,
                    statResponseId,
                    configurationStats.getActivationCount(),
                    configurationStats.getFirstTimeStamp(),
                    configurationStats.getLastTimeActive(),
                    configurationStats.getLastTimeStamp(),
                    configurationStats.getTotalTimeActive(),
                    configuration.colorMode,
                    configuration.densityDpi,
                    configuration.fontScale,
                    configuration.hardKeyboardHidden,
                    configuration.keyboard,
                    configuration.keyboardHidden,
                    configuration.mcc,
                    configuration.mnc,
                    configuration.navigation,
                    configuration.navigationHidden,
                    configuration.orientation,
                    configuration.screenHeightDp,
                    configuration.screenLayout,
                    configuration.screenWidthDp,
                    configuration.smallestScreenWidthDp,
                    configuration.touchscreen,
                    configuration.uiMode,
                    configuration.getLayoutDirection(),
                    configuration.getLocales().toLanguageTags(),
                    configuration.isScreenHdr(),
                    configuration.isScreenRound(),
                    configuration.isScreenWideColorGamut()));
        }

        List<EventStats> eventStatsList = usageStatsManager.queryEventStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime);

        for (EventStats eventStat : eventStatsList)
        {
            statistic.append(String.format(Locale.getDefault(), "EventStats,%s,%s,%d,%d,%d,%d,%d,%d\n",
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
    }
}
