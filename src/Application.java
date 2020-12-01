import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Scanner;

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
    public static boolean hasSentReqForThisRound = false;
    public static boolean hasAllKeys = false;
    private static Server server;


    private static PrintWriter out;


    private static long requestTime;  // the time when node starts to request for critical section
    private static long responseTimeSum; // sum of response time on the node


    /**
     * Read info from config file, construct host, port mapping for each node
     * @param nodeId current node id as integer
     * @throws FileNotFoundException exception thrown when config file is missing
     */
    private static void readConfigFile(int nodeId) throws FileNotFoundException {
        FileReader file = new FileReader("/home/012/q/qx/qxw170003/AOS_P2/config.txt");
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

            index++;
        }


    }


    public static void main(String[] args) throws Exception {
        nodeId = Integer.parseInt(args[0]);
        out = new PrintWriter(new BufferedWriter(new FileWriter("validation.txt", true)));
        out.close();

        out = new PrintWriter(new BufferedWriter(new FileWriter("performance.txt", true)));
        out.close();

        readConfigFile(nodeId);
        server = new Server(nodeId, hostMap, portMap, completeGraph);

        // Keys initialization, eg. put "0-1" for node 0, "1-2" for node 1, "2-0" for node 2
        // Each node holds exactly one key when initialized
        int nextId = (nodeId + 1) % numOfNode;
        String key = (Math.min(nodeId, nextId) + "-" + Math.max(nodeId, nextId));
        synchronized (server.currState) {
            server.currState.keys.add(key);
        }


        server.startServer();
        server.start();


        while (currNumOfRequest < totalNumOfRequest) {
            int interRequestDelay = getRandomNum(meanInterRequestDelay);
            try {
                Thread.sleep(interRequestDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            csEnter();
            System.out.println("Node " + nodeId + " is entering CS for the " + currNumOfRequest + " times.");
            executeCriticalSection();
            csLeave();

            currNumOfRequest++;

        }

        server.currState.finishedAllRounds = true;

        // Sleep for some time to keep communication with other nodes if necessary
        Thread.sleep(10000);
        out = new PrintWriter(new BufferedWriter(new FileWriter("performance.txt", true)));
        out.append("Message count on Node " + nodeId + ": " + server.currState.totalMsgNum);
        out.println();
        out.append("Average response time on Node " + nodeId + ": " + responseTimeSum / totalNumOfRequest);
        out.println();
        out.close();
    }

    /**
     * Generate exponential distributed random number
     * @param num the mean value passed in
     * @return generated random number as integer
     */
    private static int getRandomNum(int num) {
        Random rand = new Random();
        double exponentialRandom = Math.log(1 - rand.nextDouble()) / -3;
        return (int)Math.floor(num * (1 + exponentialRandom));
    }

    /**
     * Method to make request to enter CS
     * @throws IOException
     */
    public static void csEnter() throws IOException {
        requestTime = System.currentTimeMillis();
        hasSentReqForThisRound = true;
        Message message = new Message("Node " + nodeId + " sent a request.", MessageType.REQUEST, nodeId, server.currState.timestamp, null);
        // add request to queue
        synchronized (server.currState) {
            server.currState.pendingRequests.put(message.nodeId, message);
        }

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

    /**
     * Method to execute CS on a node, print out log file for inspection
     * @throws IOException
     */
    public static void executeCriticalSection() throws IOException {
        long enterTime = System.currentTimeMillis();
        out = new PrintWriter(new BufferedWriter(new FileWriter("validation.txt", true)));
        out.append(enterTime + "");
        out.println();
        out.close();
        System.out.println("Start time: " + enterTime);
        synchronized (server.currState) {
            server.currState.isInCriticalSection = true;
        }

        System.out.println("Executing CS");
        int csExecutionTime = getRandomNum(meanCsExecutionTime);
        try {
            Thread.sleep(csExecutionTime);
            long leaveTime = System.currentTimeMillis();

            out = new PrintWriter(new BufferedWriter(new FileWriter("validation.txt", true)));
            out.append(leaveTime + "");
            out.println();
            out.close();

            responseTimeSum += leaveTime - requestTime; // calculate the total response time on this server


            System.out.println("End time: " + leaveTime);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

    }

    /**
     * Method to reset current state upon exiting the CS
     */
    public static void csLeave() {
        synchronized (server.currState) {
            server.currState.isInCriticalSection = false;
        }

        hasSentReqForThisRound = false;
        server.currState.timestamp = server.currState.globalMaxTimestamp;
        try {
            for (int neighbor : server.currState.pendingRequests.keySet()) {
                if (neighbor != server.currState.nodeId) {
                    synchronized (server.currState) {
                        server.sendMessageWithKey(neighbor, MessageType.REPLY);
                    }
                }
                synchronized (server.currState) {
                    server.currState.pendingRequests.remove(neighbor);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

