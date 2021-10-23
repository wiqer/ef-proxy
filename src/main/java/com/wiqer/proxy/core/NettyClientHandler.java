package com.wiqer.proxy.core;



import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;


public class NettyClientHandler extends SimpleChannelInboundHandler<HttpObject> {
    private final MultipleServicesHttpClient client;

    public NettyClientHandler(final MultipleServicesHttpClient client) {
        this.client = client;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        client.processMessageReceived(ctx, msg);
    }

}
