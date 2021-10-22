package com.wiqer.proxy.core;

import com.wiqer.proxy.channel.DefaultChannelSelectStrategy;
import com.wiqer.proxy.channel.LocalChannelOption;
import com.wiqer.proxy.channel.single.SingleSelectChannelOption;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import org.apache.log4j.Logger;

import java.net.InetSocketAddress;

public class HttpProxyServer implements ProxyStarter {
    private static final Logger LOGGER = Logger.getLogger(HttpProxyServer.class);
    private final ServerBootstrap serverBootstrap=new ServerBootstrap();
    private final EventExecutorGroup singleEventExecutor;
    private final LocalChannelOption channelOption;
    private final WebConfig config;
    private final MultipleServicesHttpClient servicesHttpClient;
    public HttpProxyServer()
    {
        this.servicesHttpClient=new MultipleServicesHttpClient();
        config=new HttpServerConfig();
        channelOption=new DefaultChannelSelectStrategy().select();
        this.singleEventExecutor=new NioEventLoopGroup(1);
    }
    public HttpProxyServer(LocalChannelOption channelOption, WebConfig config)
    {
        this.servicesHttpClient=new MultipleServicesHttpClient();
        this.channelOption=channelOption;
        this.singleEventExecutor=new NioEventLoopGroup(1);
        this.config =config;
    }
    public HttpProxyServer(LocalChannelOption channelOption, WebConfig config,MultipleServicesHttpClient servicesHttpClient)
    {
        this.servicesHttpClient=servicesHttpClient;
        this.channelOption=channelOption;
        this.singleEventExecutor=new NioEventLoopGroup(1);
        this.config =config;
    }
    public static void main(String[] args)
    {
        new HttpProxyServer(new SingleSelectChannelOption(),new HttpServerConfig()).start();
    }

    @Override
    public void start()
    {
        start0();
    }

    @Override
    public void close()
    {
        try {
            channelOption.boss().shutdownGracefully();
            channelOption.selectors().shutdownGracefully();
            singleEventExecutor.shutdownGracefully();
        }catch (Exception ignored) {
            LOGGER.warn( "Exception!", ignored);
        }
    }
    public void start0() {


        serverBootstrap.group(channelOption.boss(), channelOption.selectors())
                .channel(channelOption.getChannelClass())
                .handler(new LoggingHandler(LogLevel.INFO))
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                //false
                .option(ChannelOption.SO_KEEPALIVE,true)
//                .childOption(ChannelOption.TCP_NODELAY, true)
//                .childOption(ChannelOption.SO_SNDBUF, 65535)
//                .childOption(ChannelOption.SO_RCVBUF, 65535)
                .localAddress(new InetSocketAddress(config.getNodeAddress(), config.getNodePort()))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline channelPipeline = socketChannel.pipeline();
                        channelPipeline.addLast(
                                //1. HttpServerCodec 是netty 提供的处理http的 编-解码器
                                new HttpServerCodec()

                        );
                        channelPipeline.addLast(singleEventExecutor,new HttpServerHandler( servicesHttpClient)) ;
                    }
                });

        try {
            ChannelFuture sync = serverBootstrap.bind().sync();
            LOGGER.info(sync.channel().localAddress().toString());
        } catch (InterruptedException e) {
//
            LOGGER.warn( "Interrupted!", e);
            throw new RuntimeException(e);
        }

    }
}
