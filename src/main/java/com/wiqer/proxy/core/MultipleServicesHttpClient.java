package com.wiqer.proxy.core;

import com.wiqer.proxy.base.SemaphoreReleaseOnlyOnce;
import com.wiqer.proxy.exception.FailConnectException;
import com.wiqer.proxy.utils.IdUtils;
import com.wiqer.proxy.utils.IpUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 连接服务的客户端
 * @author Administrator
 */
public class MultipleServicesHttpClient implements ProxyStarter {
    private static final Logger LOGGER = Logger.getLogger(MultipleServicesHttpClient.class);

    private static final AtomicInteger requestId = new AtomicInteger(0);

    private static final long LOCK_TIMEOUT = 3000;

    private final Lock lockChannelTables = new ReentrantLock();
    private final ConcurrentMap<String, ChannelWrapper> channelTables = new ConcurrentHashMap<String, ChannelWrapper>();
    //堵塞器的容器
    protected final ConcurrentHashMap<Long, NettyResponseProcessor> responseTable = new ConcurrentHashMap<Long, NettyResponseProcessor>(256);

    //服务管道到处理器的映射
    protected final ConcurrentHashMap<String, NettyStringResponseProcessor> responseCanalTable = new ConcurrentHashMap<String, NettyStringResponseProcessor>(256);
    //并发度
    protected final Semaphore semaphoreAsync = new Semaphore(65535/64, true);;

    private final Bootstrap bootstrap;
    private final EventLoopGroup selector;
    private final EventExecutorGroup singleEventExecutor;

    private ClientConfig config;



    private final ExecutorService publicExecutor;

    public MultipleServicesHttpClient(ClientConfig config) {

        this();
        this.config=config;
    }

    public MultipleServicesHttpClient() {

        this.bootstrap = new Bootstrap();
        this.singleEventExecutor=new NioEventLoopGroup(1);
        this.selector = new NioEventLoopGroup(1, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Client_selector_" + index.getAndIncrement());
            }
        });
        this.publicExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
            private AtomicInteger index = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Client_public_executor_" + index.getAndIncrement());
            }
        });

    }

    @Override
    public void start() {

        bootstrap.group(selector).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout())
                .option(ChannelOption.SO_SNDBUF, config.getSocketSndBufSize())
                .option(ChannelOption.SO_RCVBUF, config.getSocketRcvBufSize())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(
                                new HttpClientCodec()
                        );
                        pipeline.addLast( singleEventExecutor,new NettyClientHandler(MultipleServicesHttpClient.this));
                    }
                });
    }

    public HttpObject invokeSync(String addr, HttpRequest request, long timeout) throws Exception {
        long beginTime = System.currentTimeMillis();
        final Channel channel = this.createChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                long costTime = System.currentTimeMillis() - beginTime;
                if (costTime > timeout) {
                    throw new Exception("invokeSync call timeout!addr:"+addr);
                }
                HttpObject response = this.invokeSyncImpl(channel, request, timeout-costTime);
                return response;
            }catch (Exception e) {
                this.closeChannel(channel);
                throw e;
            }
        }else {
            this.closeChannel(channel);
            throw new Exception("Create channel exception " + addr);
        }
    }

    public void asyncProxy(String addr, HttpRequest request, long timeout, StringInvokeCallback invokeCallback) throws Exception {
        long beginTime = System.currentTimeMillis();
        final Channel channel = createChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                long costTime = System.currentTimeMillis() - beginTime;
                if (costTime > timeout) {
                    throw new Exception("invokeAsync call timeout!");
                }

                this.asyncProxyImpl(channel, request, timeout, invokeCallback);
            }catch (Exception e) {
                this.closeChannel(channel);
                throw e;
            }
        }else {
            this.closeChannel(channel);
            throw new Exception("Create channel exception " +addr);
        }
    }
    public void asyncProxyImpl(final Channel channel, final HttpRequest request, final long timeout, final StringInvokeCallback invokeCallback) throws Exception {
        final String serviceChannelId=  channel.id().asLongText();
        long beginTime = System.currentTimeMillis();
        boolean acquire = semaphoreAsync.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        long costTime = System.currentTimeMillis() - beginTime;
        if (acquire) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(semaphoreAsync);
            if (costTime > timeout) {
                once.release();
                throw new Exception("invoke async call timeout!");
            }

            final NettyStringResponseProcessor responseProcessor = new NettyStringResponseProcessor(channel, serviceChannelId, timeout-costTime, invokeCallback, once);
            responseCanalTable.put(serviceChannelId, responseProcessor);
            try {
                channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            responseProcessor.setSendRequestOK(true);
                            return;
                        }
                        requestFail(serviceChannelId);
                    }
                });
            }catch (Exception e) {
                once.release();
                throw new Exception(e);
            }
        }else {
            if (timeout <= 0) {
                throw new Exception("invoke too fast!");
            }else {
                throw new Exception("SemaphoreAsync overload!");
            }
        }
    }
    public void invokeAsync(String addr, HttpRequest request, long timeout, InvokeCallback invokeCallback) throws Exception {
        long beginTime = System.currentTimeMillis();
        final Channel channel = createChannel(addr);
        if (channel != null && channel.isActive()) {
            try {
                long costTime = System.currentTimeMillis() - beginTime;
                if (costTime > timeout) {
                    throw new Exception("invokeAsync call timeout!");
                }

                this.invokeAsyncImpl(channel, request, timeout, invokeCallback);
            }catch (Exception e) {
                this.closeChannel(channel);
                throw e;
            }
        }else {
            this.closeChannel(channel);
            throw new Exception("Create channel exception " +addr);
        }
    }
    public HttpObject invokeSyncImpl(final Channel channel, final HttpRequest request, final long timeout) throws Exception {
        final Long opaque =IdUtils.randomSnowFlowerId();
        try {
            final NettyResponseProcessor responseProcessor = new NettyResponseProcessor(channel, opaque, timeout, null, null);
            //先把堵塞器添加到map中 key是opaque
            responseTable.put(opaque, responseProcessor);
            channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    if (channelFuture.isSuccess()) {
                        responseProcessor.setSendRequestOK(true);
                        return;
                    }else {
                        responseProcessor.setSendRequestOK(false);
                        responseProcessor.setCause(channelFuture.cause());
                        responseProcessor.putResponse(null);
                    }
                }
            });

            //进入堵塞
            HttpObject response = responseProcessor.waitResponse(timeout);
            if (response == null) {
                if (responseProcessor.isSendRequestOK()) {
                    throw new Exception("Send request is success, but response is timeout!");
                }else {
                    throw new Exception("Send request is success, but response is fail!");
                }
            }

            return response;
        }finally {
            responseTable.remove(opaque);
        }
    }

    public void invokeAsyncImpl(final Channel channel, final HttpRequest request, final long timeout, final InvokeCallback invokeCallback) throws Exception {
        final Long opaque = IdUtils.randomSnowFlowerId();
        long beginTime = System.currentTimeMillis();
        boolean acquire = semaphoreAsync.tryAcquire(timeout, TimeUnit.MILLISECONDS);
        long costTime = System.currentTimeMillis() - beginTime;
        if (acquire) {
            final SemaphoreReleaseOnlyOnce once = new SemaphoreReleaseOnlyOnce(semaphoreAsync);
            if (costTime > timeout) {
                once.release();
                throw new Exception("invoke async call timeout!");
            }

            final NettyResponseProcessor responseProcessor = new NettyResponseProcessor(channel, opaque, timeout-costTime, invokeCallback, once);
            responseTable.put(opaque, responseProcessor);
            try {
                channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            responseProcessor.setSendRequestOK(true);
                            return;
                        }
                        requestFail(opaque);
                    }
                });
            }catch (Exception e) {
                once.release();
                throw new Exception(e);
            }
        }else {
            if (timeout <= 0) {
                throw new Exception("invoke too fast!");
            }else {
                throw new Exception("SemaphoreAsync overload!");
            }
        }
    }
    private void requestFail(final Long opaque) {
        NettyResponseProcessor responseProcessor = responseTable.get(opaque);
        if (responseProcessor != null) {
            responseProcessor.setSendRequestOK(false);
            responseProcessor.putResponse(null);
            try {
                executeInvokeCallback(responseProcessor);
            }catch (Throwable e) {
                LOGGER.error("Request fail " + e.getMessage());
            }finally {
                responseProcessor.release();
            }
        }
    }
    private void requestFail(final String opaque) {
        NettyStringResponseProcessor responseProcessor = responseCanalTable.get(opaque);
        if (responseProcessor != null) {
            responseProcessor.setSendRequestOK(false);
            responseProcessor.putResponse(null);
            try {
                executeInvokeCallback(responseProcessor);
            }catch (Throwable e) {
                LOGGER.error("Request fail " + e.getMessage());
            }finally {
                responseProcessor.release();
            }
        }
    }
    public ExecutorService getCallbackExecutor() {
        return publicExecutor;
    }
    private void executeInvokeCallback(final NettyResponseProcessor responseProcessor) {
        boolean flag = false;
        ExecutorService executor = this.getCallbackExecutor();
        if (executor != null) {
            try {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            responseProcessor.executeInvokeCallback();
                        }catch (Throwable e) {
                            LOGGER.error(e.getMessage());
                        }finally {
                            responseProcessor.release();
                        }
                    }
                });
            }catch(Exception e) {
                LOGGER.error(e.getMessage());
                flag = true;
            }
        }else {
            flag = true;
        }

        if (flag) {
            try {
                responseProcessor.executeInvokeCallback();
            }catch (Throwable e) {
                LOGGER.error(e.getMessage());
            }finally {
                responseProcessor.release();
            }
        }
    }

    private void executeInvokeCallback(final NettyStringResponseProcessor responseProcessor) {
        boolean flag = false;
        ExecutorService executor = this.getCallbackExecutor();
        if (executor != null) {
            try {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            responseProcessor.executeInvokeCallback();
                        }catch (Throwable e) {
                            LOGGER.error(e.getMessage());
                        }finally {
                            responseProcessor.release();
                        }
                    }
                });
            }catch(Exception e) {
                LOGGER.error(e.getMessage());
                flag = true;
            }
        }else {
            flag = true;
        }

        if (flag) {
            try {
                responseProcessor.executeInvokeCallback();
            }catch (Throwable e) {
                LOGGER.error(e.getMessage());
            }finally {
                responseProcessor.release();
            }
        }
    }
    public void processMessageReceived(ChannelHandlerContext ctx, HttpObject msg) {
        final HttpObject message = msg;
        /**
         * 将消息返回服务端
         */
        final String serviceChannelId=   ctx.channel().id().asLongText();
        final NettyStringResponseProcessor processor=   responseCanalTable.get(serviceChannelId);
        if (processor != null) {
            responseCanalTable.remove(serviceChannelId);
            boolean flag = false;
            ExecutorService executor = this.getCallbackExecutor();
            if (executor != null) {
                try {
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                processor.executeInvokeCallback();
                            } catch (Throwable e) {
                                LOGGER.error(e.getMessage());
                            } finally {
                                processor.release();
                            }
                        }
                    });
                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                    flag = true;
                }
            } else {
                flag = true;
            }

            if (flag) {
                try {
                    processor.executeInvokeCallback();
                } catch (Throwable e) {
                    LOGGER.error(e.getMessage());
                } finally {
                    processor.release();
                }
            }
        }
    }
    private void processResponseMessage(ChannelHandlerContext ctx, HttpObject msg) {
        final Long opaque = IdUtils.randomSnowFlowerId();
        NettyResponseProcessor responseProcessor = responseTable.get(opaque);
        if (responseProcessor != null) {
            responseProcessor.setResponseMessage(msg);
            responseTable.remove(opaque);

            if (responseProcessor.getInvokeCallback() != null) {
                executeInvokeCallback(responseProcessor);
            }else {
                responseProcessor.putResponse(msg);
                responseProcessor.release();
            }
        }else {
            LOGGER.warn("receive response, but not matched any request by " + ctx.channel().remoteAddress().toString());
        }
    }
    @Override
    public void close() {
        try {
            selector.shutdownGracefully();
        }catch (Exception ignored) {
            LOGGER.warn( "Exception!", ignored);
        }
    }
    private Channel createChannel(final String addr) throws Exception {
        ChannelWrapper cw = channelTables.get(addr);
        if (cw != null && cw.isOK()) {
            return cw.getChannel();
        }

        if (lockChannelTables.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
            try {
                boolean create = false;
                cw = channelTables.get(addr);
                if (cw != null) {
                    if (cw.isOK()) {
                        return cw.getChannel();
                    }else if (!cw.getChannelFuture().isDone()) {
                        create = false;
                    }else {
                        channelTables.remove(addr);
                        create = true;
                    }
                }else {
                    create = true;
                }

                if (create) {
                    ChannelFuture channelFuture = bootstrap.connect(IpUtils.addr2SocketAddress(addr));
                    cw = new ChannelWrapper(channelFuture);
                    channelTables.put(addr, cw);
                }
            }catch (Exception e) {
                LOGGER.error("Create channel exception: " + e);
            }finally {
                lockChannelTables.unlock();
            }
        }else {
            LOGGER.warn("Create channel timeout: " + LOCK_TIMEOUT);
        }

        if (cw != null) {
            ChannelFuture channelFuture = cw.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(config.getConnectTimeout())) {
                if (cw.isOK()) {
                    return cw.getChannel();
                }else {
                    throw new FailConnectException("Connect " + addr + " fail " + channelFuture.cause());
                }
            }else {
                LOGGER.warn("Connect " + addr + " timeout.");
            }
        }

        return null;
    }
    public void closeChannel(final Channel channel) {
        if (channel == null) {
            return;
        }

        try {
            if (lockChannelTables.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
                try {
                    String remoteAddr = null;
                    for (Map.Entry<String, ChannelWrapper> entry : channelTables.entrySet()) {
                        String key = entry.getKey();
                        ChannelWrapper value = entry.getValue();
                        if (value != null && value.getChannel() != null) {
                            if (value.getChannel() == channel) {
                                remoteAddr = key;
                                break;
                            }
                        }
                    }

                    if (remoteAddr != null) {
                        channelTables.remove(remoteAddr);
                        channel.close();
                    }
                }catch(Exception e) {
                    LOGGER.error("Close channel exception: " + e.getMessage());
                }finally {
                    lockChannelTables.unlock();
                }
            }else {
                LOGGER.warn("Close channel timeout: " + LOCK_TIMEOUT);
            }
        }catch (Exception e) {
            LOGGER.error("Close channel exception: " + e.getMessage());
        }
    }

    static class ChannelWrapper {
        private final ChannelFuture channelFuture;

        public ChannelWrapper(final ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        public boolean isOK() {
            return channelFuture.channel().isActive();
        }

        public boolean isWritable() {
            return channelFuture.channel().isWritable();
        }

        private Channel getChannel() {
            return channelFuture.channel();
        }

        public ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }

    public ClientConfig getConfig() {
        return config;
    }

    public void setConfig(ClientConfig config) {
        this.config = config;
    }
}
