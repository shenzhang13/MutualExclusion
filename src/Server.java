import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class Server extends Thread {
    private static final int MAX_MSG_SIZE = 4096;
    CurrState currState;
    HashMap<Integer, String> hostMap;
    HashMap<Integer, Integer> portMap;
    HashSet<Integer> completeGraph; // All the node ids in the graph
    HashMap<Integer, SctpChannel> senders;
    SctpServerChannel sctpServerChannel;

    /**
     * Constructor for Server class
     *
     * @param nodeId        node's id as integer
     * @param hostMap       hashmap that map the nodeId to its hostname
     * @param portMap       hashmap that map the nodeId to its port number
     * @param completeGraph hashset that contain all the nodes in the system
     * @throws IOException exception thrown
     */
    public Server(int nodeId, HashMap<Integer, String> hostMap, HashMap<Integer, Integer> portMap, HashSet<Integer> completeGraph) throws IOException {
        currState = new CurrState(nodeId, 0, 0, new HashSet<String>(), false, false, 1, new ConcurrentHashMap<Integer, Message>());
        this.hostMap = hostMap;
        this.portMap = portMap;
        this.completeGraph = completeGraph;
        senders = new HashMap<>();
    }


    public void run() {
        try {
            while (!currState.finishedAllRounds) {
                SctpChannel rcvSC = sctpServerChannel.accept();
                ByteBuffer buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE);
                rcvSC.receive(buf, null, null); // Messages are received over SCTP using ByteBuffer
                Message msg = Message.fromByteBuffer(buf);
                currState.totalMsgNum++;
                synchronized (currState) {
                    currState.globalMaxTimestamp = Math.max(currState.globalMaxTimestamp, msg.timestamp) + 1;
                }

                switch (msg.messageType) {
                    case REPLY: {
                        // add the key from REPLY to local key set
                        synchronized (currState) {
                            currState.keys.add(msg.keyCarried);
                        }
                    }
                    case REQUEST: {
                        if (currState.isInCriticalSection) {
                            synchronized (currState) {
                                currState.pendingRequests.put(msg.nodeId, msg);
                            }
                            continue;
                        } else if (Application.hasSentReqForThisRound) {
                            if (currState.timestamp > msg.timestamp) {
                                synchronized (currState) {
                                    sendMessageWithKey(msg.nodeId, MessageType.BOTH);
                                }
                            } else if (currState.timestamp == msg.timestamp) {

                                if (currState.nodeId > msg.nodeId) {
                                    synchronized (currState) {
                                        sendMessageWithKey(msg.nodeId, MessageType.BOTH);
                                    }
                                } else {
                                    synchronized (currState) {
                                        currState.pendingRequests.put(msg.nodeId, msg);
                                    }
                                }
                            } else {
                                synchronized (currState) {
                                    currState.pendingRequests.put(msg.nodeId, msg);
                                }

                            }
                        } else {
                            synchronized (currState) {
                                sendMessageWithKey(msg.nodeId, MessageType.REPLY);
                            }
                        }

                    }
                    case BOTH: {
                        synchronized (currState) {
                            currState.keys.add(msg.keyCarried);
                            currState.pendingRequests.put(msg.nodeId, msg);
                        }

                    }
                }
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to send the key to neighbor along with the message
     *
     * @param senderId
     * @param type
     * @throws Exception
     */
    public void sendMessageWithKey(int senderId, MessageType type) throws Exception {
        InetSocketAddress addr = new InetSocketAddress(hostMap.get(senderId), portMap.get(senderId));
        SctpChannel sendChannel = SctpChannel.open(addr, 0, 0);

        String keySendBack = Math.min(currState.nodeId, senderId) + "-" + Math.max(currState.nodeId, senderId);
        MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
        Message msg = new Message("", type, currState.nodeId, currState.timestamp, keySendBack);
        sendChannel.send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
        currState.totalMsgNum++;
        currState.keys.remove(keySendBack);
    }

    /**
     * Start to listen on self port number
     */
    public void startServer() {
        try {
            InetSocketAddress address = new InetSocketAddress(portMap.get(currState.nodeId)); // Get address from port number
            sctpServerChannel = SctpServerChannel.open();//Open server channel
            sctpServerChannel.bind(address);//Bind server channel to address
            System.out.println("Server started");
            Thread.sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Send message to neighbors to request the key
     *
     * @throws IOException
     */
    public void makeRequests() throws IOException {
        for (int neighbor : hostMap.keySet()) {
            if (neighbor == currState.nodeId) continue;
            String key = Math.min(currState.nodeId, neighbor) + "-" + Math.max(currState.nodeId, neighbor);

            if (!currState.keys.contains(key)) {
                InetSocketAddress addr = new InetSocketAddress(hostMap.get(neighbor), portMap.get(neighbor));
                SctpChannel sendChannel = SctpChannel.open(addr, 0, 0);
                MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
                Message message = new Message("Node " + currState.nodeId + " make a request", MessageType.REQUEST, currState.nodeId, currState.timestamp, null);
                try {
                    sendChannel.send(message.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
                    currState.totalMsgNum++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Check if a node has enough keys to enter its CS
     *
     * @param currState current state of the node
     * @return true if has all the keys needed for CS, false otherwise
     */
    public boolean checkForKeys(CurrState currState) {
        currState.keys.remove(null);
        return currState.keys.size() == completeGraph.size() - 1;
    }
}

/**
 * Class that contain info for a node's current state
 */
class CurrState {
    int nodeId;
    long timestamp;
    long globalMaxTimestamp;
    HashSet<String> keys;
    boolean isInCriticalSection;
    boolean hasPendingRequest;
    boolean finishedAllRounds;
    int counter;
    int totalMsgNum; // count the total num of messages sent & rcvd on this node
    public ConcurrentHashMap<Integer, Message> pendingRequests;


    public CurrState(int nodeId, long timestamp, long globalMaxTimestamp, HashSet<String> keys, boolean isInCriticalSection, boolean hasPendingRequest, int counter, ConcurrentHashMap<Integer, Message> pendingRequests) {
        this.nodeId = nodeId;
        this.timestamp = timestamp;
        this.globalMaxTimestamp = globalMaxTimestamp;
        this.keys = keys;
        this.isInCriticalSection = isInCriticalSection;
        this.hasPendingRequest = hasPendingRequest;
        this.counter = counter;
        this.pendingRequests = pendingRequests;
        this.finishedAllRounds = false;
        this.totalMsgNum = 0;
    }
}