package com.swimming.clientservice.serviceDiscovery;

import org.apache.zookeeper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ZookeeperHandler implements Watcher, SmartApplicationListener {

    Logger logger = LoggerFactory.getLogger(ZookeeperHandler.class);

    ZooKeeper zooKeeper;

    private final String blueNode;
    private final String greenNode;
    private final String runtimeColor;
    private String registerNode;

    private AtomicInteger port = new AtomicInteger(0);

    @Autowired
    private ApplicationContext context;
    @Autowired
    private EurekaHandler eurekaHandler;

    public ZookeeperHandler(
            @Value("${server.runtime-color}")
            String runtimeColor,
            @Value("${zookeeper.server.hostname}")
            String hostName,
            @Value("${zookeeper.server.node.blue}")
            String blueNode,
            @Value("${zookeeper.server.node.green}")
            String greenNode,
            @Value("${zookeeper.server.timeout}")
            int timeout) throws IOException {
        this.blueNode = blueNode;
        this.greenNode = greenNode;
        this.runtimeColor = runtimeColor.toLowerCase(Locale.ROOT);

        zooKeeper = new ZooKeeper(hostName, timeout, this);
    }

    public void start()  {
        serviceRegister();
    }

    public void stop() {
        try {
            zooKeeper.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void serviceRegister() {
        try {
            logger.info("runtime-color={}", runtimeColor);
            if (runtimeColor.equals(ActiveColor.blue.name())) {
                registerNode = blueNode;
            } else {
                registerNode = greenNode;
            }

            zooKeeper.create(
                    new StringBuilder(registerNode).append("/").append(eurekaHandler.getInstanceId()).toString(),
                    new StringBuilder("127.0.0.1:").append(port.get()).toString().getBytes(StandardCharsets.UTF_8),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return WebServerInitializedEvent.class.isAssignableFrom(eventType)
                || ContextClosedEvent.class.isAssignableFrom(eventType);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof WebServerInitializedEvent) {
            onApplicationEvent((WebServerInitializedEvent) event);
        } else if (event instanceof ContextClosedEvent) {
            onApplicationEvent((ContextClosedEvent) event);
        }
    }

    private void onApplicationEvent(WebServerInitializedEvent event) {
        String contextName = event.getApplicationContext().getServerNamespace();
        if (contextName == null || !contextName.equals("management")) {
            int localPort = event.getWebServer().getPort();
            if (this.port.get() == 0) {
                logger.info("Updating port to " + localPort);
                this.port.compareAndSet(0, localPort);
                start();
            }
        }
    }

    private void onApplicationEvent(ContextClosedEvent event) {
        if (event.getApplicationContext() == context) {
            stop();
        }
    }

    @Override
    public void process(WatchedEvent event) {

    }
}