package com.swimming.zookeeperclient.serviceDiscovery;

import org.apache.zookeeper.*;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ZookeeperMonitor implements Watcher, AsyncCallback.StatCallback, AsyncCallback.ChildrenCallback {

    Logger logger = LoggerFactory.getLogger(ZookeeperMonitor.class);

    private ZooKeeper zooKeeper;
    private DataMonitorListener listener;
    private byte prevData[];
    private List<String> prevBlueChildrens = new ArrayList<>();
    private List<String> prevGreenChildrens = new ArrayList<>();
    private String existNode;
    private String childrenNode;

    public ZookeeperMonitor(ZooKeeper zk, DataMonitorListener listener) {
        this.zooKeeper = zk;
        this.listener = listener;
    }

    public void watchNode(String zNode) throws InterruptedException, KeeperException {
        if (zooKeeper.exists(zNode, false) != null) {
            this.existNode = zNode;
            zooKeeper.exists(zNode, this, this, null);
        }
    }

    public void watchChildren(String zNode) throws InterruptedException, KeeperException {
        if (zooKeeper.exists(zNode, false) != null) {
            this.childrenNode = zNode;
            zooKeeper.getChildren(zNode, true, this, null);
        }
    }

    /**
     * Other classes use the DataMonitor by implementing this method
     */
    public interface DataMonitorListener {
        void existsListener(byte data[]);
        void getChildrenListener(String path, List<String> children);
    }

    /**
     * Watcher
     * @param event
     */
    @Override
    public void process(WatchedEvent event) {
        if (event.getType() == Event.EventType.None) {
            // We are are being told that the state of the
            // connection has changed
            switch (event.getState()) {
                case SyncConnected:
                    // In this particular example we don't need to do anything
                    // here - watches are automatically re-registered with
                    // server and any watches triggered while the client was
                    // disconnected will be delivered (in order of course)
                    break;
                case Expired:
                    // It's all over
                    break;
            }
        } else {
            switch (event.getType()) {
                case NodeChildrenChanged:
                    zooKeeper.getChildren(childrenNode, true, this, null);
                case NodeDeleted:
                    zooKeeper.exists(existNode, this, this, null);
                    listener.existsListener(null);
                default:
                    zooKeeper.exists(existNode, this, this, null);
            }
        }
    }

    /**
     * AsyncCallback.StatCallback
     * @param rc
     * @param path
     * @param ctx
     * @param stat
     */
    @Override
    public void processResult(int rc, String path, Object ctx, Stat stat) {
        boolean exists;
        switch (rc) {
            case Code.Ok:
                exists = true;
                break;
            case Code.NoNode:
                exists = false;
                break;
            case Code.SessionExpired:
            case Code.NoAuth:
                return;
            default:
                // Retry errors
                zooKeeper.exists(existNode, this, this, null);
                return;
        }

        byte b[] = null;
        if (exists) {
            try {
                b = zooKeeper.getData(existNode, false, null);
            } catch (KeeperException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                return;
            }
        }
        if ((b == null && b != prevData)
                || (b != null && !Arrays.equals(prevData, b))) {
            listener.existsListener(b);
            prevData = b;
        }
    }

    /**
     * AsyncCallback.ChildrenCallback
     * @param rc
     * @param path
     * @param ctx
     * @param children
     */
    @Override
    public void processResult(int rc, String path, Object ctx, List<String> children) {
        switch (rc) {
            case Code.Ok:
            case Code.NoNode:
                break;
            case Code.SessionExpired:
            case Code.NoAuth:
                return;
            default:
                // Retry errors
                zooKeeper.getChildren(childrenNode, true, this, null);
                return;
        }

        if (path.substring(1).equals(ActiveColor.blue.name())) {
            List<String> noneMatchList = children.stream().filter(
                    target -> prevBlueChildrens.stream().noneMatch(Predicate.isEqual(target))
            ).collect(Collectors.toList());

            logger.info("path={}", path);
            logger.info("prevWorkers={}", prevBlueChildrens);
            logger.info("currentWorkers={}", children);
            logger.info("noneMatchList={}", noneMatchList);

            if (noneMatchList.size() > 0) {
                prevBlueChildrens = children;
                listener.getChildrenListener(path, noneMatchList);
            }
        } else {
            List<String> noneMatchList = children.stream().filter(
                    target -> prevGreenChildrens.stream().noneMatch(Predicate.isEqual(target))
            ).collect(Collectors.toList());

            logger.info("path={}", path);
            logger.info("prevWorkers={}", prevGreenChildrens);
            logger.info("currentWorkers={}", children);
            logger.info("noneMatchList={}", noneMatchList);

            if (noneMatchList.size() > 0) {
                prevGreenChildrens = children;
                listener.getChildrenListener(path, noneMatchList);
            }
        }
    }
}