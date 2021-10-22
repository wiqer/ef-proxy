package com.wiqer.proxy.exception;

public class FailConnectException extends RuntimeException{

    public FailConnectException(String msg) {
        super(msg);
    }

    public FailConnectException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
