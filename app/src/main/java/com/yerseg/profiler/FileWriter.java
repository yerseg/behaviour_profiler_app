package com.yerseg.profiler;

import android.os.Process;
import android.util.Log;

import java.io.File;
import java.util.Locale;

public class FileWriter {
    public static void writeFile(File directory, String fileName, String data)
    {
        Log.d("Profiler [FileWriter]", String.format(Locale.getDefault(), "\t%d\twriteFile()", Process.myTid()));

        File directoryFile = new File(directory, "ProfilingData");
        if (!directoryFile.exists()) {
            directoryFile.mkdir();
        }

        try {
            File file = new File(directoryFile, fileName);
            java.io.FileWriter writer = new java.io.FileWriter(file, true);

            MutexHolder.getMutex().lock();
            writer.append(data);
            writer.flush();
            MutexHolder.getMutex().unlock();

            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            MutexHolder.getMutex().unlock();
        }
    }
}
