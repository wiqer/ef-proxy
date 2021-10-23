package com.wiqer.proxy.core;

import com.wiqer.proxy.base.SemaphoreReleaseOnlyOnce;
import io.netty.handler.codec.http.HttpObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class StringResponseProcessor {
    private final String opaque;
    private final long timeout;
    private final StringInvokeCallback invokeCallback;
    private final long beginTimestamp = System.currentTimeMillis();
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final SemaphoreReleaseOnlyOnce once;
    private final AtomicBoolean executeCallbackOnlyOnce = new AtomicBoolean(false);

    private volatile HttpObject responseMessage;
    private volatile boolean sendRequestOK = true;
    private volatile Throwable cause;

    public StringResponseProcessor(String opaque, long timeout, StringInvokeCallback invokeCallback, SemaphoreReleaseOnlyOnce once) {
        this.opaque = opaque;
        this.timeout = timeout;
        this.invokeCallback = invokeCallback;
        this.once = once;
    }

    public void executeInvokeCallback() {
        if (invokeCallback != null) {
            if (executeCallbackOnlyOnce.compareAndSet(false, true)) {
                invokeCallback.operationComplete(this);
            }
        }
    }

    public void release() {
        if (once != null) {
            once.release();
        }
    }

    public boolean isTimeout() {
        long diff = System.currentTimeMillis() - beginTimestamp;
        return diff > timeout;
    }

    //countDownLatch堵塞
    public HttpObject waitResponse(final long timeout) throws InterruptedException {
        countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
        return responseMessage;
    }
    //countDownLatch唤醒
    public void putResponse(HttpObject responseMessage) {
        this.responseMessage = responseMessage;
        countDownLatch.countDown();
    }

    public long getBeginTimestamp() {
        return beginTimestamp;
    }

    public boolean isSendRequestOK() {
        return sendRequestOK;
    }

    public void setSendRequestOK(boolean sendRequestOK) {
        this.sendRequestOK = sendRequestOK;
    }

    public long getTimeout() {
        return timeout;
    }

    public StringInvokeCallback getInvokeCallback() {
        return invokeCallback;
    }

    public Throwable getCause() {
        return cause;
    }

    public void setCause(Throwable cause) {
        this.cause = cause;
    }

    public HttpObject getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(HttpObject responseMessage) {
        this.responseMessage = responseMessage;
    }

    public String getOpaque() {
        return opaque;
    }

    @Override
    public String toString() {
        return "ResponseProcessor [responseMessage=" + responseMessage
                + ", sendRequestOK=" + sendRequestOK
                + ", cause=" + cause
                + ", opaque=" + opaque
                + ", timeout=" + timeout
                + ", invokeCallback=" + invokeCallback
                + ", beginTimestamp=" + beginTimestamp
                + ", countDownLatch=" + countDownLatch + "]";
    }
}
