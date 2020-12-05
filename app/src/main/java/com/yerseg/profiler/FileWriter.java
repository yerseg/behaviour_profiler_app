package com.yerseg.profiler;

import android.os.Process;
import android.util.Log;

import java.io.File;
import java.util.Locale;

public class FileWriter {
    public static void writeFile(File directory, String fileName, String data)
    {
        Log.d("Profiler [FileWriter]", String.format(Locale.getDefault(), "\t%d\twriteFile()", Process.myTid()));

        try {
            MutexHolder.getMutex().lock();

            File file = new File(directory, fileName);
            java.io.FileWriter writer = new java.io.FileWriter(file, true);

            writer.append(data);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            MutexHolder.getMutex().unlock();
        }
    }
}