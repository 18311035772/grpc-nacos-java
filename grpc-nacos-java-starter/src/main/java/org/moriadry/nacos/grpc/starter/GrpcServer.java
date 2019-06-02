package org.moriadry.nacos.grpc.starter;

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.util.MutableHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hosts embedded gRPC server
 */
public class GrpcServer {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected int port;

    /**
     * Is it already started
     */
    protected volatile boolean started;

    /**
     * grpc server
     */
    public io.grpc.Server server;

    /**
     * service registry
     */
    protected MutableHandlerRegistry handlerRegistry = new MutableHandlerRegistry();

    /**
     * The mapping relationship between BindableService and ServerServiceDefinition
     */
    protected ConcurrentHashMap<BindableService, ServerServiceDefinition> serviceInfo = new ConcurrentHashMap<BindableService,
            ServerServiceDefinition>();

    /**
     * invoker count
     */
    protected AtomicInteger invokerCnt = new AtomicInteger();

    public void init(int port) {
        this.port = port;
        this.server = NettyServerBuilder.forPort(port).fallbackHandlerRegistry(handlerRegistry).build();
    }

    public void init(int port, Object ref) {
        this.port = port;
        BindableService bindableService = (BindableService) ref;
        server = ServerBuilder.forPort(port).addService(bindableService).build();
    }

    public void start() {
        if (started) {
         return;
        }

        synchronized (this) {
            try {
                server.start();
                logger.info("grpc server started at port {}", port);
            } catch (IOException e) {
                logger.error("grpc server started error, msg: ", e.getMessage());
            }
        }
    }

    public void registerService(Object ref) {
        BindableService bindableService = (BindableService) ref;
        ServerServiceDefinition serverServiceDefinition = bindableService.bindService();
        serviceInfo.put(bindableService, serverServiceDefinition);

        try {
            handlerRegistry.addService(serverServiceDefinition);
            invokerCnt.incrementAndGet();
        } catch (Exception e) {
            logger.error("Register grpc service error ", e);
        }
    }

    public void unregisterService(Object ref) {
        try {
            ServerServiceDefinition serverServiceDefinition = serviceInfo.get(ref);
            handlerRegistry.removeService(serverServiceDefinition);
            invokerCnt.decrementAndGet();
        } catch (Exception e) {
            logger.error("Unregister grpc service error ", e);
        }
        if (invokerCnt.get() == 0) {
            stop();
        }
    }

    public void stop() {
        if (!started) {
            return;
        }
        try {
            // 关闭端口，不关闭线程池
            logger.info("Stop the http rest server at port {}", port);
            server.shutdown();
        } catch (Exception e) {
            logger.error("Stop the http rest server at port " + port + " error !", e);
        }
        started = false;
    }

    public void blockUtilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

}
