import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Serializable;
import java.util.*;

public class Application {
    private static int nodeId;
    private static int numOfNode;
    public static int meanInterRequestDelay;
    private static int meanCsExecutionTime;
    private static int totalNumOfRequest;
    private static int currNumOfRequest = 0;
    private static HashMap<Integer, String> hostMap = new HashMap<>();
    private static HashMap<Integer, Integer> portMap = new HashMap<>();
    private static HashSet<Integer> completeGraph = new HashSet<>(); // Set of all the nodes
    private static HashSet<String> keys = new HashSet<>(); // Set of keys in the current node
//    private static CurrState currState;
    public static boolean hasSentReqForThisRound = false;
//    public static boolean isInCriticalSection = false;
    public static boolean hasAllKeys = false;
    private static Server server;



    private static void readConfigFile(int nodeId) throws FileNotFoundException {
        FileReader file = new FileReader("/home/012/s/sx/sxz162330/AOS/Project2/configuration.txt");
        Scanner scanner = new Scanner(file);
        // Read first meaningful line
        while (scanner.hasNextLine()) {
            String s = scanner.nextLine();
            if (s.length() == 0 || s.charAt(0) == '#')
                continue;
            if (s.contains("#"))
                s = s.substring(0, s.indexOf("#"));
            String[] array = s.split(" ");
            numOfNode = Integer.parseInt(array[0]);
            meanInterRequestDelay = Integer.parseInt(array[1]);
            meanCsExecutionTime = Integer.parseInt(array[2]);
            totalNumOfRequest = Integer.parseInt(array[3]);
            break;
        }
        // Read each node's host and port into map
        int index = 0;
        while (index < numOfNode) {
            String s = scanner.nextLine();
            if (s.length() == 0 || s.charAt(0) == '#')
                continue;

            // array[] holds node, host, port
            String[] array = s.split(" ");
            completeGraph.add(Integer.parseInt(array[0]));
            hostMap.put(Integer.parseInt(array[0]), array[1]);
            portMap.put(Integer.parseInt(array[0]), Integer.parseInt(array[2]));
//          node = new Node(Integer.parseInt(array[0]), array[1], Integer.parseInt(array[2]));

            // Keys initialization, eg. put "0-1" for node 0, "1-2" for node 1, "2-0" for node 2
            // Each node holds exactly one key when initialized
            int nextId = (nodeId + 1) % numOfNode;
            keys.add(Math.min(nodeId, nextId) + "-" + Math.max(nodeId, nextId));

            index++;
        }

        server.currState.keys = keys;

    }


    public static void main(String[] args) throws FileNotFoundException {
        nodeId = Integer.parseInt(args[0]);
        readConfigFile(nodeId);

        //TODO makeChannels after start??
        server.start();
        server.makeChannels();


        while(currNumOfRequest < totalNumOfRequest) {
            Random r = new Random();
            long interRequestDelay = (long)r.nextGaussian() + meanInterRequestDelay;
            try {
                Thread.sleep(interRequestDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            csEnter();
            executeCriticalSection();
            csLeave();

            currNumOfRequest++;

        }
    }

    public static void csEnter() {
        long currTime = TimeStamp.returnTime();
        Message message = new Message("Node " + nodeId + " sent a request.", MessageType.REQUEST, nodeId, new TimeStamp(currTime, nodeId), null);
        // add request to queue
        synchronized (server.currState) {
            server.currState.pendingRequests.put(message.nodeId, message);
        }

        hasSentReqForThisRound = true;

        // if has all keys, return and execute CS
        // else make request for keys
        if (server.checkForKeys(server.currState)) {
            hasAllKeys = true;
            synchronized (server.currState) {
                server.currState.isInCriticalSection = true;
            }

            return;
        } else {
            server.makeRequests();
        }

        // Block later steps until has all the keys
        while (!server.checkForKeys(server.currState)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        hasAllKeys = true;
        synchronized (server.currState) {
            server.currState.isInCriticalSection = true;
        }

    }

    public static void executeCriticalSection() {
        synchronized (server.currState) {
            server.currState.isInCriticalSection = true;
        }

        System.out.println("Executing CS");
        try {
            Thread.sleep(meanCsExecutionTime);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

    }

    public static void csLeave() {
        synchronized (server.currState) {
            server.currState.isInCriticalSection = false;
        }

        hasSentReqForThisRound = false;

        try {
            for (int neighbor : server.currState.pendingRequests.keySet()) {
                if (neighbor != server.currState.nodeId) {
                    synchronized (server.currState) {
                        server.sendMessageWithKey(server.currState.nodeId, MessageType.REPLY);
                    }

                }
                synchronized (server.currState) {
                    server.currState.pendingRequests.remove(neighbor);
                }

            }
        } catch (ConcurrentModificationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

class TimeStamp implements Serializable {

    private static final long serialVersionUID = 1L;
    long time;
    int nodeId;

    public TimeStamp(long time, int nodeId) {
        this.time = time;
        this.nodeId = nodeId;
    }

    public static long returnTime() {
        java.util.Date date = new java.util.Date();
        return date.getTime();
    }

}