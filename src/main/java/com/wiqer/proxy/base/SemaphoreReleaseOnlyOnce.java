package com.wiqer.proxy.base;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;


public class SemaphoreReleaseOnlyOnce {
    private final AtomicBoolean released = new AtomicBoolean(false);

    private final Semaphore semaphore;

    public SemaphoreReleaseOnlyOnce(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    public void release() {
        if (semaphore != null) {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }

    public Semaphore getSemaphore() {
        return semaphore;
    }
}
