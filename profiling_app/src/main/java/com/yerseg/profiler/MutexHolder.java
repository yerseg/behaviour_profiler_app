package com.yerseg.profiler;

import java.util.concurrent.locks.ReentrantLock;

public class MutexHolder {
    private static volatile ReentrantLock mutex;

    public static ReentrantLock getMutex() {
        ReentrantLock localInstance = mutex;
        if (localInstance == null) {
            synchronized (ReentrantLock.class) {
                localInstance = mutex;
                if (localInstance == null) {
                    mutex = localInstance = new ReentrantLock();
                }
            }
        }
        return localInstance;
    }
}
