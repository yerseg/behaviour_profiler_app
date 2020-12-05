package com.yerseg.profiler;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {

    public static class Zipper {
        private static final int BUFFER = 2048;

        public static void zip(List<File> files, File zipFile) {
            try {
                BufferedInputStream origin = null;
                FileOutputStream dest = new FileOutputStream(zipFile);
                ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

                byte data[] = new byte[BUFFER];

                for (File file : files) {
                    Log.v("Compress", "Adding: " + file);

                    FileInputStream fi = new FileInputStream(file);
                    origin = new BufferedInputStream(fi, BUFFER);
                    ZipEntry entry = new ZipEntry(file.getName());
                    out.putNextEntry(entry);

                    int count = -1;
                    while ((count = origin.read(data, 0, BUFFER)) != -1) {
                        out.write(data, 0, count);
                    }

                    origin.close();
                }

                out.finish();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String GetTimeStamp(long time) {
        return new SimpleDateFormat("dd.MM.yyyy_HH:mm:ss.SSS").format(new Date(time));
    }

    public static File getProfilingFilesDir(Context context) {
        File filesDirFile = context.getFilesDir();

        File directoryFile = new File(filesDirFile, ProfilingService.PROFILING_STATS_DIRECTORY_NAME);
        if (!directoryFile.exists()) {
            directoryFile.mkdir();
        }

        return directoryFile;
    }

    public static File getTempDataFilesDir(Context context) {
        File filesDirFile = context.getFilesDir();

        File directoryFile = new File(filesDirFile, ProfilingService.PROFILING_STATS_TEMP_DIRECTORY_NAME);
        if (!directoryFile.exists()) {
            directoryFile.mkdir();
        }

        return directoryFile;
    }

    public static void moveFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);

            if (src.exists()) {
                src.delete();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }

    public static File createZip(List<File> files, File tempDir) {
        String zipName = String.format(Locale.getDefault(), "report_%s_%s.zip",
                Utils.GetTimeStamp(System.currentTimeMillis()),
                UUID.randomUUID().toString());

        File zipFile = new File(tempDir, zipName);
        Zipper.zip(files, zipFile);
        return zipFile;
    }
}
