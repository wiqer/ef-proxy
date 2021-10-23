package com.wiqer.proxy.core;

import com.wiqer.proxy.base.SemaphoreReleaseOnlyOnce;
import io.netty.channel.Channel;

/**
 * @author Administrator
 */
public class NettyStringResponseProcessor extends StringResponseProcessor {
    private final Channel processChannel;

    public NettyStringResponseProcessor(Channel channel, String opaque, long timeout, StringInvokeCallback invokeCallback, SemaphoreReleaseOnlyOnce once) {
        super(opaque, timeout, invokeCallback, once);
        this.processChannel = channel;
    }

    public Channel getProcessChannel() {
        return processChannel;
    }
}
