package com.wiqer.proxy.core;



import com.wiqer.proxy.base.SemaphoreReleaseOnlyOnce;
import io.netty.channel.Channel;


public class NettyResponseProcessor extends ResponseProcessor {
    private final Channel processChannel;

    public NettyResponseProcessor(Channel channel, int opaque, long timeout, InvokeCallback invokeCallback, SemaphoreReleaseOnlyOnce once) {
        super(opaque, timeout, invokeCallback, once);
        this.processChannel = channel;
    }

    public Channel getProcessChannel() {
        return processChannel;
    }
}
