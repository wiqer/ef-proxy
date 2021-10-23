package com.wiqer.proxy.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;

import java.net.URI;

/**
 * 1. SimpleChannelInboundHandler 是 ChannelInboundHandlerAdapter
 * 2. HttpObject 客户端和服务器端相互通讯的数据被封装成 HttpObject
 * @author Administrator
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final Logger LOGGER = Logger.getLogger(HttpServerHandler.class);
    private final MultipleServicesHttpClient servicesHttpClient;
    public HttpServerHandler(MultipleServicesHttpClient servicesHttpClient){
        this.servicesHttpClient=servicesHttpClient;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject msg) throws Exception {
       final ChannelHandlerContext ctx=channelHandlerContext;

        LOGGER.info("对应的channel=" + ctx.channel() + " pipeline=" + ctx
        .pipeline() + " 通过pipeline获取channel" + ctx.pipeline().channel());

        LOGGER.info("当前ctx的handler=" + ctx.handler());

        //判断 msg 是不是 httprequest请求
        if(msg instanceof HttpRequest) {

            LOGGER.info("ctx 类型="+ctx.getClass());

            LOGGER.info("pipeline hashcode" + ctx.pipeline().hashCode() + " TestHttpServerHandler hash=" + this.hashCode());

            LOGGER.info("msg 类型=" + msg.getClass());
            LOGGER.info("客户端地址" + ctx.channel().remoteAddress());

            //获取到
            HttpRequest httpRequest = (HttpRequest) msg;
            //获取uri, 过滤指定的资源
            URI uri = new URI(httpRequest.uri());

            LOGGER.info("uri:"+uri.getPath());
            //DefaultHttpRequest request  =   new DefaultHttpRequest(HttpVersion.HTTP_1_1, httpRequest.method(),httpRequest.uri(), httpRequest.headers());

            servicesHttpClient.asyncProxy(servicesHttpClient.getConfig().getNodeAddress(), httpRequest, 10 * 1000, new StringInvokeCallback() {
                @Override
                public void operationComplete(StringResponseProcessor responseProcessor) {
                    ctx.writeAndFlush(responseProcessor.getResponseMessage());
                }
            });


        }
    }



}
