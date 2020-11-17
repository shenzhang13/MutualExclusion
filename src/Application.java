import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    //    private static HashSet<String> keys = new HashSet<>(); // Set of keys in the current node
//    private static CurrState currState;
    public static boolean hasSentReqForThisRound = false;
    //    public static boolean isInCriticalSection = false;
    public static boolean hasAllKeys = false;
    private static Server server;

    private static PrintWriter out;

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


    public static void main(String[] args) throws IOException, InterruptedException {

        nodeId = Integer.parseInt(args[0]);
//        PrintStream out = new PrintStream(new FileOutputStream("output.txt"));
//        PrintStream out = new PrintStream(new FileOutputStream("output" +nodeId+ ".txt"));
//        System.setOut(out);


        out = new PrintWriter(new BufferedWriter(new FileWriter("output.txt", true)));
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
            long interRequestDelay = getRandomNum(meanInterRequestDelay);
            try {
                Thread.sleep(interRequestDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            System.out.println("Node " + nodeId + " tries to enter CS");
//            System.out.println("Key num: " + server.currState.keys.size());
//            for(String k : server.currState.keys){
//                System.out.println(k);
//            }

            csEnter();
            System.out.println("Node " + nodeId + " is entering CS for the " + currNumOfRequest + " times.");
            executeCriticalSection();
            csLeave();

            currNumOfRequest++;

        }
        server.currState.finishedAllRounds = true;
    }

    // TODO check, should be exponectial
    private static long getRandomNum(int num) {
        Random r = new Random();
        return (long) r.nextGaussian() + num;
    }

    public static void csEnter() throws IOException {
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
//                System.out.println("9****-----------------------------");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        hasAllKeys = true;
        synchronized (server.currState) {
            server.currState.isInCriticalSection = true;
        }

    }

    public static void executeCriticalSection() throws IOException {
        long millis=System.currentTimeMillis();
        out = new PrintWriter(new BufferedWriter(new FileWriter("output.txt", true)));
        out.append(millis+"");
        out.println();
        out.close();
//        java.util.Date date=new java.util.Date(millis);
        System.out.println("Start time: " +  millis);
        synchronized (server.currState) {
            server.currState.isInCriticalSection = true;
        }

        System.out.println("Executing CS");
        try {
            Thread.sleep(getRandomNum(meanCsExecutionTime));
            long millis2=System.currentTimeMillis();

            out = new PrintWriter(new BufferedWriter(new FileWriter("output.txt", true)));
            out.append(millis2+"");
            out.println();
            out.close();



//            java.util.Date date2=new java.util.Date(millis2);
            System.out.println("End time: " + millis2);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

    }

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
//        } catch (ConcurrentModificationException e) {
//            e.printStackTrace();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

