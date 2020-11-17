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
//                synchronized (currState){
//                System.out.println("6----------------"); // YES
//                for (String k : currState.keys) {
//                    System.out.println(k);
//                }
                SctpChannel rcvSC = sctpServerChannel.accept();
//                System.out.println("7----------------"); // YES
                ByteBuffer buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE);
                rcvSC.receive(buf, null, null); // Messages are received over SCTP using ByteBuffer
                Message msg = Message.fromByteBuffer(buf);
                // update timestamp
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
//                            System.out.println("1----------------");
                            synchronized (currState) {
                                currState.pendingRequests.put(msg.nodeId, msg);
                            }
                            continue;
                        } else if (Application.hasSentReqForThisRound) {
                            // compare timestamp
                            if (currState.timestamp > msg.timestamp) {
                                synchronized (currState) {
//                                    System.out.println("2----------------");
                                    sendMessageWithKey(msg.nodeId, MessageType.BOTH);
                                }
                            } else if (currState.timestamp == msg.timestamp) {

                                if (currState.nodeId > msg.nodeId) {
                                    // key转圈传
//                                    System.out.println("3----------------");
                                    synchronized (currState) {
                                        sendMessageWithKey(msg.nodeId, MessageType.BOTH);
                                    }
                                } else {
                                    synchronized (currState) {
                                        currState.pendingRequests.put(msg.nodeId, msg);
                                    }
//                                    System.out.println("4----------------");
                                }
                            } else {
                                synchronized (currState) {
                                    currState.pendingRequests.put(msg.nodeId, msg);
                                }

                            }
                        } else { // has no pending request
                            synchronized (currState) {
//                                System.out.println("5----------------");
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


    public void sendMessageWithKey(int senderId, MessageType type) throws Exception {

//        System.out.println("NODE ID: " + currState.nodeId + " is trying to send key to: " + senderId);

        InetSocketAddress addr = new InetSocketAddress(hostMap.get(senderId), portMap.get(senderId));
        SctpChannel sendChannel = SctpChannel.open(addr, 0, 0);

        String keySendBack = Math.min(currState.nodeId, senderId) + "-" + Math.max(currState.nodeId, senderId);
        MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
        Message msg = new Message("", type, currState.nodeId, currState.timestamp, keySendBack);
        sendChannel.send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
        currState.keys.remove(keySendBack);

//        System.out.println("--------- SENT ----------");
//        System.out.println(msg.message);
    }


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


    public void makeRequests() throws IOException {
        for (int neighbor : hostMap.keySet()) {
            if (neighbor == currState.nodeId) continue;
            String key = Math.min(currState.nodeId, neighbor) + "-" + Math.max(currState.nodeId, neighbor);

            if (!currState.keys.contains(key)) {
//                System.out.println("-------------*****************@@@@@@@@@@@@@@ " + key); // yes

                InetSocketAddress addr = new InetSocketAddress(hostMap.get(neighbor), portMap.get(neighbor));
                SctpChannel sendChannel = SctpChannel.open(addr, 0, 0);
                MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
                Message message = new Message("Node " + currState.nodeId + " make a request", MessageType.REQUEST, currState.nodeId, currState.timestamp, null);
                try {
//                    System.out.println("MAKING REQUEST"); //yes
                    sendChannel.send(message.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
//                    System.out.println("REQUEST SENT"); //yes
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean checkForKeys(CurrState currState) {
//        synchronized (currState){
//        System.out.println("Current num of Keys: " + currState.keys.size());
//        System.out.println("Total nodes: " + completeGraph.size());
        currState.keys.remove(null);
        return currState.keys.size() == completeGraph.size() - 1;
//        }
    }
}


class CurrState {
    int nodeId;
    long timestamp;
    long globalMaxTimestamp;
    HashSet<String> keys;
    boolean isInCriticalSection;
    boolean hasPendingRequest;
    boolean finishedAllRounds;
    int counter = 1;
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
    }
}