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
        long currTime = TimeStamp.returnTime();
        currState = new CurrState(nodeId, new TimeStamp(currTime, nodeId), new HashSet<String>(), false, false, 1, new ConcurrentHashMap<Integer, Message>());
        this.hostMap = hostMap;
        this.portMap = portMap;
        this.completeGraph = completeGraph;
        senders = new HashMap<>();
    }


    public void run(){
        try{
            while(true){
//                synchronized (currState){
                    SctpChannel rcvSC = null;
                    if(currState.counter < completeGraph.size()){
                        Thread.sleep(1000);
                        InetSocketAddress address = new InetSocketAddress(portMap.get(currState.nodeId)); // Get address from port number
                        SctpServerChannel sctpServerChannel = SctpServerChannel.open();//Open server channel
                        sctpServerChannel.bind(address);//Bind server channel to address

                        rcvSC = sctpServerChannel.accept();
                        synchronized (currState) {
                            currState.counter++;
                        }

                    }

                    ByteBuffer buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE);
                    rcvSC.receive(buf, null, null); // Messages are received over SCTP using ByteBuffer
                    Message msg = Message.fromByteBuffer(buf);
                    // update timestamp
//                    currState.globalMaxTimestamp = Math.max(currState.globalMaxTimestamp, msg.timestamp) + 1;
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
                            } else if (currState.hasPendingRequest) {
                                // compare timestamp
                                if (currState.timestamp.time > msg.timestamp.time) {
                                    synchronized (currState) {
                                        sendMessageWithKey(msg.nodeId, MessageType.BOTH);
                                    }
                                } else {
                                    synchronized (currState) {
                                        currState.pendingRequests.put(msg.nodeId, msg);
                                    }

                                }
                            } else { // has no pending request
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
//                }


            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public void sendMessageWithKey(int senderId, MessageType type) throws Exception {
//        currState.timestamp++;
        String keySendBack = Math.min(currState.nodeId, senderId) + "-" + Math.max(currState.nodeId, senderId);
        MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
        long currTime = TimeStamp.returnTime();
        Message msg = new Message("", type, currState.nodeId, new TimeStamp(currTime, currState.nodeId), keySendBack);
        senders.get(msg.nodeId).send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
        currState.keys.remove(keySendBack);
        System.out.println(msg.message);
    }



    public void makeChannels(){
        try{
            // connect to all other nodes in the graph
            for (int neighbor : hostMap.keySet()) {
                if (neighbor != currState.nodeId) {
                    InetSocketAddress addr = new InetSocketAddress(hostMap.get(neighbor), portMap.get(neighbor));
                    SctpChannel sendChannel = SctpChannel.open(addr, 0, 0);
                    senders.put(neighbor, sendChannel);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void makeRequests(){
        for(int neighbor : hostMap.keySet()){
            String key = Math.min(currState.nodeId, neighbor) + "-" + Math.max(currState.nodeId, neighbor);
            if (!currState.keys.contains(key)) {
                MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
                long currTime = TimeStamp.returnTime();
                Message message = new Message("Node " + currState.nodeId + " make a request", MessageType.REQUEST, currState.nodeId, new TimeStamp(currTime, currState.nodeId), null);
                try {
                    senders.get(neighbor).send(message.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean checkForKeys(CurrState currState) {
        synchronized (currState){
            return currState.keys.size() == completeGraph.size() - 1;
        }
    }
}


class CurrState {
    int nodeId;
    TimeStamp timestamp;
//    int globalMaxTimestamp;
    HashSet<String> keys;
    boolean isInCriticalSection;
    boolean hasPendingRequest;
    int counter = 1;
    public ConcurrentHashMap<Integer, Message> pendingRequests;

    public CurrState(int nodeId, TimeStamp timestamp, HashSet<String> keys, boolean isInCriticalSection, boolean hasPendingRequest, int counter, ConcurrentHashMap<Integer, Message> pendingRequests) {
        this.nodeId = nodeId;
        this.timestamp = timestamp;
//        this.globalMaxTimestamp = globalMaxTimestamp;
        this.keys = keys;
        this.isInCriticalSection = isInCriticalSection;
        this.hasPendingRequest = hasPendingRequest;
        this.counter = counter;
        this.pendingRequests = pendingRequests;
    }
}