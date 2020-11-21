package com.yerseg.profiler;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MutexHolder {
    private static volatile ReentrantLock mutex;

    public static ReentrantLock getMutex() {
        ReentrantLock localInstance = mutex;
        if (localInstance == null) {
            synchronized (ReentrantReadWriteLock.class) {
                localInstance = mutex;
                if (localInstance == null) {
                    mutex = localInstance = new ReentrantLock();
                }
            }
        }
        return localInstance;
    }
}
