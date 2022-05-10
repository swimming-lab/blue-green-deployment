package com.swimming.zookeeperclient.serviceDiscovery;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ZookeeperHandler implements Watcher, ZookeeperMonitor.DataMonitorListener, SmartApplicationListener {

    Logger logger = LoggerFactory.getLogger(ZookeeperHandler.class);

    ZookeeperMonitor zookeeperMonitor;
    ZooKeeper zooKeeper;

    private boolean isInit = true;
    private boolean isLeader = false;
    private final String leaderNode;
    private final String activeColorNode;
    private final String blueNode;
    private final String greenNode;
    private String activeColor;
    private String activeNode;
    private String standbyNode;

    private AtomicInteger port = new AtomicInteger(0);

    @Autowired
    private ApplicationContext context;
    @Autowired
    private ServiceHandler serviceHandler;

    public ZookeeperHandler(
            @Value("${zookeeper.server.hostname}")
            String hostName,
            @Value("${zookeeper.server.node.leader}")
            String leaderNode,
            @Value("${zookeeper.server.node.active-color}")
            String activeColorNode,
            @Value("${zookeeper.server.node.blue}")
            String blueNode,
            @Value("${zookeeper.server.node.green}")
            String greenNode,
            @Value("${zookeeper.server.timeout}")
            int timeout) throws IOException {
        this.leaderNode = leaderNode;
        this.activeColorNode = activeColorNode;
        this.blueNode = blueNode;
        this.greenNode = greenNode;

        zooKeeper = new ZooKeeper(hostName, timeout, this);
        zookeeperMonitor = new ZookeeperMonitor(zooKeeper, this);
    }

    public void start()  {
        initZooKeeper();
        leaderSelect();
    }

    public void stop() {
        try {
            zooKeeper.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void initZooKeeper() {
        try {
            if (zooKeeper.exists(blueNode, false) == null) {
                zooKeeper.create(
                        blueNode,
                        null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }

            if (zooKeeper.exists(greenNode, false) == null) {
                zooKeeper.create(
                        greenNode,
                        null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }

            if (zooKeeper.exists(activeColorNode, false) == null) {
                zooKeeper.create(
                        activeColorNode,
                        null,
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
        }
    }

    private void leaderSelect() {
        try {
            logger.info("리더 경합 시작");
            zooKeeper.create(
                    leaderNode,
                    new StringBuilder("127.0.0.1:").append(port.get()).toString().getBytes(StandardCharsets.UTF_8),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL);

            String currentActiveColor = new String(zooKeeper.getData(activeColorNode, false, null));
            setActiveColorNode(currentActiveColor);

            zookeeperMonitor.watchNode(activeColorNode);
            zookeeperMonitor.watchChildren(activeNode);

            isLeader = true;
            logger.info("리더 경합 완료 > 리더로 선정됨");
        } catch (Exception e) {
            isLeader = false;
        } finally {
            if (!isLeader) {
                followerSelect();
            }
        }
    }

    private void setActiveColorNode(String activeColor) {
        if (activeColor.equals(ActiveColor.blue.name())) {
            activeNode = blueNode;
            standbyNode = greenNode;
        } else {
            activeNode = greenNode;
            standbyNode = blueNode;
        }
    }

    private void followerSelect() {
        try {
            zookeeperMonitor.watchNode(leaderNode);
            logger.info("팔로워로 선정됨");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void process(WatchedEvent event) {
        if (zookeeperMonitor != null) {
            zookeeperMonitor.process(event);
        }
    }

    @Override
    public synchronized void existsListener(byte[] data) {
        if (isLeader) {
            try {
                if (data != null) {
                    activeColor = new String(data);
                    logger.info("active_color 변경");
                    logger.info("activeColor={}", activeColor);

                    List<String> activeWorkers = zooKeeper.getChildren(activeNode, false);
                    List<String> standbyWorkers = zooKeeper.getChildren(standbyNode, false);
                    logger.info("activeWorkers={}", activeWorkers);
                    logger.info("standbyWorkers={}", standbyWorkers);

                    for (String instanceId : activeWorkers) {
                        String instanceDomain = new String(zooKeeper.getData(
                                new StringBuilder(activeNode).append("/").append(instanceId).toString(),
                                false,
                                null));

                        serviceHandler.changeService(instanceId, instanceDomain, EurekaStatus.OUT_OF_SERVICE.name());
                    }

                    for (String instanceId : standbyWorkers) {
                        String instanceDomain = new String(zooKeeper.getData(
                                new StringBuilder(standbyNode).append("/").append(instanceId).toString(),
                                false,
                                null));

                        serviceHandler.changeService(instanceId, instanceDomain, EurekaStatus.UP.name());
                    }

                    String currentActiveColor = new String(zooKeeper.getData(activeColorNode, false, null));
                    setActiveColorNode(currentActiveColor);
                    zookeeperMonitor.watchChildren(activeNode);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (isInit) {
                isInit = false;
            } else {
                logger.info("리더 사라짐");
                leaderSelect();
            }
        }
    }

    @Override
    public synchronized void getChildrenListener(String path, List<String> workers) {
        try {
            String currentActiveColor = new String(zooKeeper.getData(activeColorNode, false, null));
            if (path.substring(1).equals(currentActiveColor)) {
                logger.info("active node 추가");
                logger.info("activeColor={}", activeColor);
                logger.info("workers={}", workers);

                for (String instanceId : workers) {
                    String instanceDomain = new String(zooKeeper.getData(
                            new StringBuilder(activeNode).append("/").append(instanceId).toString(),
                            false,
                            null));

                    serviceHandler.changeService(instanceId, instanceDomain, EurekaStatus.UP.name());
                }
            }
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
}