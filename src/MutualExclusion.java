//import com.sun.nio.sctp.MessageInfo;
//import com.sun.nio.sctp.SctpChannel;
//import com.sun.nio.sctp.SctpServerChannel;
//
//import java.io.FileNotFoundException;
//import java.io.FileReader;
//import java.net.InetSocketAddress;
//import java.nio.ByteBuffer;
//import java.util.*;
//
//public class MutualExclusion {
//    private static int numOfNode;
//    public static int interRequestDelay;
//    private static int csExecutionTime;
//    private static int maxNumOfRequest;
////    private static boolean[] authorization; // maintained in each node's local memory
////    private static boolean[] deferredReply;
//    private static boolean using;
//    private static boolean waiting;
//    private static HashMap<Integer, String> hostMap = new HashMap<>();
//    private static HashMap<Integer, Integer> portMap = new HashMap<>();
//    private static HashSet<Integer> completeGraph = new HashSet<>(); // Set of all the nodes
//    private static HashSet<String> keys = new HashSet<>(); // Set of keys in the current node
////    private static Node node;
//    //    public static List<SctpChannel> senders = new ArrayList<>();
//    private static CurrState currState;
//    private static List<Receiver> receivers;
//    private static HashMap<Integer, SctpChannel> senders = new HashMap<>();
//
//
//    private static void readConfigFile(int nodeId) throws FileNotFoundException {
//        FileReader file = new FileReader("/home/012/s/sx/sxz162330/AOS/Project2/configuration.txt");
//        Scanner scanner = new Scanner(file);
//        // Read first meaningful line
//        while (scanner.hasNextLine()) {
//            String s = scanner.nextLine();
//            if (s.length() == 0 || s.charAt(0) == '#')
//                continue;
//            if (s.contains("#"))
//                s = s.substring(0, s.indexOf("#"));
//            String[] array = s.split(" ");
//            numOfNode = Integer.parseInt(array[0]);
//            interRequestDelay = Integer.parseInt(array[1]);
//            csExecutionTime = Integer.parseInt(array[2]);
//            maxNumOfRequest = Integer.parseInt(array[3]);
//            break;
//        }
//        // Read each node's host and port into map
//        int index = 0;
//        while (index < numOfNode) {
//            String s = scanner.nextLine();
//            if (s.length() == 0 || s.charAt(0) == '#')
//                continue;
//
//            // array[] holds node, host, port
//            String[] array = s.split(" ");
//            completeGraph.add(Integer.parseInt(array[0]));
//            hostMap.put(Integer.parseInt(array[0]), array[1]);
//            portMap.put(Integer.parseInt(array[0]), Integer.parseInt(array[2]));
////          node = new Node(Integer.parseInt(array[0]), array[1], Integer.parseInt(array[2]));
//
//            // Keys initialization, eg. put "0-1" for node 0, "1-2" for node 1, "2-0" for node 2
//            // Each node holds exactly one key when initialized
//            int nextId = (nodeId + 1) % numOfNode;
//            keys.add(Math.min(nodeId, nextId) + "-" + Math.max(nodeId, nextId));
//
//            index++;
//        }
//
//        currState.keys = keys;
//
//    }
//
//    public static boolean checkForKeys(CurrState currState) {
//        return currState.keys.size() == currState.totoalNumOfNodes - 1;
//    }
//
//    public static void main(String[] args) throws Exception {
//        int nodeId = Integer.parseInt(args[0]);
//        readConfigFile(nodeId);
////        authorization = new boolean[numOfNode];
////        deferredReply = new boolean[numOfNode];
//
//        InetSocketAddress address = new InetSocketAddress(portMap.get(nodeId)); // Get address from port number
//        SctpServerChannel sctpServerChannel = SctpServerChannel.open();//Open server channel
//        sctpServerChannel.bind(address);//Bind server channel to address
//        System.out.println("Server has started");
//        currState = new CurrState(nodeId, numOfNode, 0, keys, false);
//        Thread.sleep(3000);
//
//        // connect to all other nodes in the graph
//        for (int neighbor : hostMap.keySet()) {
//            if (neighbor != nodeId) {
//                InetSocketAddress addr = new InetSocketAddress(hostMap.get(neighbor), portMap.get(neighbor));
//                SctpChannel sendChannel = SctpChannel.open(addr, 0, 0);
//                senders.put(neighbor, sendChannel);
//            }
//        }
//
//        int recevierCount = 0;
//        while (recevierCount++ < numOfNode - 1) {
//            SctpChannel rcvSC = sctpServerChannel.accept(); // Wait for incoming connection from client
//
//            Receiver rcv = new Receiver(rcvSC, currState, senders); // Create a Receiver thread to handle incoming messages for each client
////            currState.receiverTracker.put(rcv, true);
//            receivers.add(rcv);
//        }
//
//        Synchronizer synchronizer = new Synchronizer(currState, senders);
//
//        //
////        synchronizer.start();
////        for (Receiver receiver : receivers) receiver.start();
////
////        synchronizer.join();
////        for (Receiver receiver : receivers) receiver.join();
//
//
//
//        //TODO execute ceEnter() every d ms
//
//
//    }
//
////    static class Node {
////        int nodeId;
////        String host;
////        int port;
////
////        public Node(int nodeId, String host, int port) {
////            this.nodeId = nodeId;
////            this.host = host;
////            this.port = port;
////        }
////    }
//
//
//}
//
///**
// * If a node has all the keys, execute CS,
// *  otherwise send out requests
// */
//class Synchronizer extends Thread {
//    private CurrState currState;
//    private HashMap<Integer, SctpChannel> senders;
//
//    public Synchronizer(CurrState currState, HashMap<Integer, SctpChannel> senders) {
//        this.currState = currState;
//        this.senders = senders;
//    }
//
//    public void run() {
//        while (true) {
//            synchronized (currState) {
//                if (currState.keys.size() == currState.totoalNumOfNodes - 1) {
//                    // Increase timestamp upon receiving all keys from neighbors
//                    currState.selfTimestamp = currState.globalMaxTimestamp + 1;
//                    currState.hasPendingRequest = false;
//                    MutualExclusion.executeCriticalSection();
//                    currState.notifyAll();
//                } else {
//                    currState.hasPendingRequest = true;
//                    // TODO set timestamp
//                    // TODO just self+1 or global max +1???
//                    currState.selfTimestamp++;
//                    while (currState.keys.size() < currState.totoalNumOfNodes - 1) {
//                        for (int neighbor : senders.keySet()) {
//                            String key = Math.min(currState.NODE, neighbor) + "-" + Math.max(currState.NODE, neighbor);
//                            if (!currState.keys.contains(key)) {
//                                MessageInfo messageInfo = MessageInfo.createOutgoing(null, currState.selfTimestamp); // MessageInfo for SCTP layer
//                                Message message = new Message("Node " + currState.NODE + " make a request", MessageType.REQUEST, currState.NODE, currState.selfTimestamp, null);
//                                try {
//                                    senders.get(neighbor).send(message.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }
//                                System.out.println(message.message);
//                            }
//
//
//                        }
//                    }
//                }
//                // Wait for interRequestDelay milisec
////                try {
////                    Thread.sleep(MutualExclusion.interRequestDelay);
////                } catch (InterruptedException e) {
////                    e.printStackTrace();
////                }
//
//                // Then send out request
////                try {
////                    if (currState.msgLeftToRcv == 0) {
////                        currState.round_id++;
////                        currState.msgLeftToRcv = currState.numberOfNeighbors;
////
////                        // When entering a new round, send a message to each neighbor
////                        for(int neighbor : senders.keySet()){
////                            // TODO
////                            String key = Math.min(currState.NODE, neighbor) + "-" + Math.max(currState.NODE, neighbor);
////                            if(!currState.keys.contains(key)){
////                                MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
////                            Message message = new Message("Node " +  currState.NODE  + " Sent a Message at Round " + currState.round_id, MessageType.REQUEST, , currState.NODE);
//////                            sender.send(message.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
//////                            System.out.println(message.message);
////                            }
////
////
////                        }
////                        currState.hoppingNeighbors.add(new HashSet<Integer>());
////                        for(Receiver rcv : currState.receiverTracker.keySet()) currState.receiverTracker.put(rcv, false);
////                        currState.notifyAll();
////                    }
////                } catch (Exception e) {
////                    e.printStackTrace();
////                }
//            }
//
//        }
////        MutualExclusion.csEnter();
//    }
//}
//
//
///**
// * Object to handle incoming REQUEST & REPLY messages from neighbors
// * and reply with REPLY message
// */
//class Receiver extends Thread {
//    CurrState currState;
//    private HashMap<Integer, SctpChannel> senders;
//
//    // Size of ByteBuffer to accept incoming messages
//    static int MAX_MSG_SIZE = 4096;
//    SctpChannel serverClient;
//
//    Receiver(SctpChannel sc, CurrState currState, HashMap<Integer, SctpChannel> senders) {
//        this.serverClient = sc;
//        this.currState = currState;
//        this.senders = senders;
//    }
//
//    public void run() {
//        try {
//            while (true) {
//                synchronized (currState) {
//                    // defer the sending of REPLY if already in CS
//                    if (currState.isInCriticalSection) {
//                        currState.wait();
//                    }
//                    ByteBuffer buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE);
//
//                    serverClient.receive(buf, null, null); // Messages are received over SCTP using ByteBuffer
//
//                    Message msg = Message.fromByteBuffer(buf);
//                    switch (msg.messageType) {
//                        case REPLY: {
//                            // add the key from REPLY to local key set
//                            currState.keys.add(msg.keyCarried);
//                            currState.selfTimestamp = Math.max(currState.globalMaxTimestamp, msg.timestamp);
//                        }
//                        case REQUEST: {
//
//                            if (currState.hasPendingRequest) {
//                                // compare timestamp
//                                if (currState.selfTimestamp < msg.timestamp) {
//                                    currState.wait();
//                                    sendReplyWithKey(msg, MessageType.REPLY); // REPLY
//                                } else if (currState.selfTimestamp == msg.timestamp) {
//                                    if (currState.NODE < msg.nodeId) {
//                                        currState.wait();
//                                        sendReplyWithKey(msg, MessageType.REPLY); // REPLY
//                                    } else {
//                                        sendReplyWithKey(msg, MessageType.BOTH); //BOTH
//                                    }
//                                } else {
//                                    // Reply with key, and Request for key
//                                    // add keyCarried to REPLY msg
//                                    sendReplyWithKey(msg, MessageType.BOTH); //BOTH
//                                }
//                            } else {
//                                sendReplyWithKey(msg, MessageType.REPLY);
//                            }
//
//                        }
//                        case BOTH: {
//                            // add the key from REPLY to local key set
//                            currState.wait();
//
//                            //TODO timestamp
//                            currState.keys.add(msg.keyCarried);
//                            currState.wait();
//                            sendReplyWithKey(msg, MessageType.REPLY);
////                            Message reply = new Message("", MessageType.BOTH, currState.NODE, currState.selfTimestamp, null);
//                        }
//                    }
////                    int msg_id = msg.round_id;
////                    HashSet<Integer> nextHop = msg.hoppingNeighbors.get(msg.hoppingNeighbors.size() - 1);
//
//                    // Add the current node to hopping neighbors if the node has not been reach before
////                    for(int node : nextHop){
////                        if(!currState.reached.contains(node)) {
////                            currState.hoppingNeighbors.get(currState.hoppingNeighbors.size() - 1).add(node);
////                            currState.reached.add(node);
////                        }
////                    }
//
//                }
//                try {
//                    Thread.sleep(500);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    private void sendReplyWithKey(Message msg, MessageType type) throws Exception {
//        String keySendBack = Math.min(currState.NODE, msg.nodeId) + "-" + Math.max(currState.NODE, msg.nodeId);
//        MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
//        Message replyAndRequest = new Message("", type, currState.NODE, currState.selfTimestamp, keySendBack);
////                                    Message message = new Message("Node " +  currState.NODE  + " Sent a Message at Round " + currState.round_id, currState.round_id, currState.NODE, currState.hoppingNeighbors);
//        senders.get(msg.nodeId).send(replyAndRequest.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
//        System.out.println(replyAndRequest.message);
//    }
//}
//
///**
// * Object to be synchronized between Receiver thread and Synchronizer thread
// * CurrState contains the node's current running state
// */
//class CurrState {
//    int NODE;
//    //    int round_id;
////    int msgLeftToRcv;
////    int numberOfNeighbors;
//    int totoalNumOfNodes;
//    int selfTimestamp;
//    int globalMaxTimestamp;
//    HashSet<String> keys;
//    boolean isInCriticalSection;
//    boolean hasPendingRequest;
//
////    HashMap<Receiver, Boolean> receiverTracker;
////    List<HashSet<Integer>> hoppingNeighbors;
////    HashSet<Integer> reached;
//
//
//    public CurrState(int NODE, int totoalNumOfNodes, int timestamp, HashSet<String> keys, boolean isInCriticalSection) {
////        this.round_id = round_id;
////        this.msgLeftToRcv = msgLeftToRcv;
//        this.NODE = NODE;
//        this.totoalNumOfNodes = totoalNumOfNodes;
////        this.numberOfNeighbors = numberOfNeighbors;
//        this.selfTimestamp = timestamp;
////        this.receiverTracker = receiverTracker;
////        this.hoppingNeighbors = hoppingNeighbors;
////        this.reached = reached;
//        this.keys = keys;
//        this.isInCriticalSection = isInCriticalSection;
//    }
//}