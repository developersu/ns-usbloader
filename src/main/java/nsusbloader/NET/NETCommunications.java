package nsusbloader.NET;

import javafx.concurrent.Task;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
/*
* Add option: don't serve replies
* */

public class NETCommunications extends Task<Void> { // todo: thows IOException?

    private LogPrinter logPrinter;

    private String hostIP;
    private int hostPort;
    private String extras;

    private String switchIP;

    private HashMap<String, File> nspMap;

    private ServerSocket serverSocket;

    private boolean isValid;
    private boolean doNotServeRequests;
    /**
     * Simple constructor that everybody uses
     * */
    public NETCommunications(List<File> filesList, String switchIP){
        this.doNotServeRequests = false;
        this.extras = "";
        this.logPrinter = new LogPrinter();
        this.switchIP = switchIP;
        this.nspMap = new HashMap<>();
        logPrinter.print("\tPrepare chain", EMsgType.INFO);
        // Collect and encode NSP files list
        try {
            for (File nspFile : filesList)
                nspMap.put(URLEncoder.encode(nspFile.getName(), "UTF-8"), nspFile);
        }
        catch (UnsupportedEncodingException uee){
            isValid = false;
            logPrinter.print("NET: Unsupported encoding for 'URLEncoder'. Internal issue you can't fix. Please report.", EMsgType.FAIL);
            for (File nspFile : filesList)
                nspMap.put(nspFile.getName(), nspFile);
            close(EFileStatus.FAILED);
            return;
        }
        // Resolve IP
        DatagramSocket socket;
        try{                                        // todo: check other method if internet unavaliable
            socket = new DatagramSocket();
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);    // Google
            hostIP = socket.getLocalAddress().getHostAddress();
            socket.close();
        }
        catch (SocketException | UnknownHostException e){
            logPrinter.print("NET: Can't get your computer IP using Google DNS server", EMsgType.INFO);
            try {
                socket = new DatagramSocket();
                socket.connect(InetAddress.getByName("193.0.14.129"), 10002);    // RIPE NCC
                hostIP = socket.getLocalAddress().getHostAddress();
                socket.close();
            }
            catch (SocketException | UnknownHostException e1){
                logPrinter.print("NET: Can't get your computer IP using RIPE NCC root server", EMsgType.INFO);
                try {
                    socket = new DatagramSocket();
                    socket.connect(InetAddress.getByName("people.com.cn"), 10002);    // Renmin Ribao
                    hostIP = socket.getLocalAddress().getHostAddress();
                    socket.close();
                }
                catch (SocketException | UnknownHostException e2){
                    logPrinter.print("NET: Can't get your computer IP using Renmin Ribao server", EMsgType.FAIL);
                    logPrinter.print("Try using 'Expert mode' and set IP by yourself", EMsgType.INFO);
                    try {
                        Enumeration enumeration = NetworkInterface.getNetworkInterfaces();
                        while (enumeration.hasMoreElements()) {
                            NetworkInterface n = (NetworkInterface) enumeration.nextElement();
                            Enumeration enumeration1 = n.getInetAddresses();
                            while (enumeration1.hasMoreElements()) {
                                InetAddress i = (InetAddress) enumeration1.nextElement();
                                logPrinter.print("Check for: "+i.getHostAddress(), EMsgType.INFO);
                            }
                        }
                    } catch (SocketException socketException) {
                        logPrinter.print("Can't determine possible variants.", EMsgType.FAIL);
                    }
                    isValid = false;
                    close(EFileStatus.FAILED);
                    return;
                }
            }
        }
        logPrinter.print("NET: Your IP detected as: "+hostIP, EMsgType.PASS);
        // Detect port
        Random portRandomizer = new Random();
        for (int i=0; i<5; i++){
            try {
                this.hostPort = portRandomizer.nextInt(999)+6000;
                serverSocket = new ServerSocket(hostPort);  //System.out.println(serverSocket.getInetAddress()); 0.0.0.0
                logPrinter.print("NET: Your port detected as: "+hostPort, EMsgType.PASS);
                break;
            }
            catch (IOException ioe){
                if (i==4){
                    logPrinter.print("NET: Can't find good port", EMsgType.FAIL);
                    logPrinter.print("Try using 'Expert mode' and set port by yourself.", EMsgType.INFO);
                    isValid = false;
                    close(EFileStatus.FAILED);
                    return;
                }
                else
                    logPrinter.print("NET: Can't use port "+hostPort+"\nLooking for another one.", EMsgType.WARNING);
            }
        }
        isValid = true;
    }
    /**
     * Do it hard way (expert mode selected)
     * */
    public NETCommunications(List<File> filesList, String switchIP, boolean doNotServeRequests, String hostIP, int hostPort, String extras){
        this.doNotServeRequests = doNotServeRequests;
        if (doNotServeRequests)
            this.extras = extras;
        else
            this.extras = "";
        this.switchIP = switchIP;
    }

/*
Replace everything to ASCII (WEB representation)
calculate
write in first 4 bytes
* */
    @Override
    protected Void call() {
        if (!isValid)
            return null;
        if (isCancelled()){
            logPrinter.print("NET: interrupted by user", EMsgType.INFO);
            close(EFileStatus.FAILED);
            return null;
        }

        logPrinter.print("\tStart chain", EMsgType.INFO);
        // Get files list length
        StringBuilder myStrBuilder;

        myStrBuilder = new StringBuilder();
        for (String fileNameEncoded : nspMap.keySet()) {
            myStrBuilder.append(hostIP);
            myStrBuilder.append(':');
            myStrBuilder.append(hostPort);
            myStrBuilder.append('/');
            myStrBuilder.append(extras);
            myStrBuilder.append(fileNameEncoded);
            myStrBuilder.append('\n');
        }

        byte[] nspListNames = myStrBuilder.toString().getBytes(StandardCharsets.UTF_8);                 // Follow the
        byte[] nspListSize = ByteBuffer.allocate(Integer.BYTES).putInt(nspListNames.length).array();    // defining order

        try {
            Socket handShakeSocket = new Socket(InetAddress.getByName(switchIP), 2000);
            OutputStream os = handShakeSocket.getOutputStream();

            os.write(nspListSize);
            os.write(nspListNames);
            os.flush();

            handShakeSocket.close();
        }
        catch (IOException uhe){
            uhe.printStackTrace();          // TODO: FIX: could be [UnknownHostException]
            return null;
        }

        // Go transfer
        try {
            Socket clientSocket = serverSocket.accept();

            InputStream is = clientSocket.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            OutputStream os = clientSocket.getOutputStream();
            OutputStreamWriter osr = new OutputStreamWriter(os);
            PrintWriter pw = new PrintWriter(osr);

            String line;
            LinkedList<String> tcpPackeet = new LinkedList<>();
            while ((line = br.readLine()) != null) {
                if (isCancelled())                      // TODO: notice everywhere
                    break;
                System.out.println(line);              // TODO: remove DBG
                if (line.trim().isEmpty()) {           // If TCP packet is ended
                    handleRequest(tcpPackeet, pw);     // Proceed required things
                    tcpPackeet.clear();                // Clear data and wait for next TCP packet
                }
                else
                    tcpPackeet.add(line);               // Otherwise collect data
            }
            System.out.println("\nDone!"); // reopen client sock
            clientSocket.close();
            serverSocket.close();
        }
        catch (IOException ioe){
            ioe.printStackTrace();          // TODO: fix
        }
        logPrinter.update(nspMap, EFileStatus.UNKNOWN);
        logPrinter.close();
        return null;
    }

    // 200 206 400 (inv range) 404 416 (416 Range Not Satisfiable )
    private void handleRequest(LinkedList<String> packet, PrintWriter pw){
        File requestedFile;
        if (packet.get(0).startsWith("HEAD")){
            requestedFile = nspMap.get(packet.get(0).replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", ""));
            if (requestedFile == null || !requestedFile.exists()){
                pw.write(NETPacket.getCode404());
                pw.flush();
                logPrinter.update(requestedFile, EFileStatus.FAILED);
            }
            else {
                pw.write(NETPacket.getCode200(requestedFile.length()));
                pw.flush();
                System.out.println(requestedFile.getAbsolutePath()+"\n"+NETPacket.getCode200(requestedFile.length()));
            }
            return;
        }
        if (packet.get(0).startsWith("GET")) {
            requestedFile = nspMap.get(packet.get(0).replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", ""));
            if (requestedFile == null || !requestedFile.exists()){
                pw.write(NETPacket.getCode404());
                pw.flush();
                logPrinter.update(requestedFile, EFileStatus.FAILED);
            }
            else {
                for (String line: packet)
                    if (line.toLowerCase().startsWith("range")){
                        String[] rangeStr = line.toLowerCase().replaceAll("^range:\\s+?bytes=", "").split("-", 2);
                        if (!rangeStr[0].isEmpty() && !rangeStr[1].isEmpty()){      // If both ranges defined: Read requested

                        }
                        else if (!rangeStr[0].isEmpty()){                           // If only START defined: Read all

                        }
                        else if (!rangeStr[1].isEmpty()){                           // If only END defined: Try to read last 500 bytes

                        }
                        else {
                            pw.write(NETPacket.getCode400());
                            pw.flush();
                            logPrinter.update(requestedFile, EFileStatus.FAILED);
                            //logPrinter.print();                                   // TODO: INFORM
                        }
                    }
            }
        }
    }
    /**
     * Close when done
     * */
    private void close(EFileStatus status){
        try {
            serverSocket.close();
            logPrinter.print("NET: Closing server socket.", EMsgType.PASS);
        }
        catch (IOException ioe){
            logPrinter.print("NET: Closing server socket failed. Sometimes it's not an issue.", EMsgType.WARNING);
        }
        if (status != null) {
            logPrinter.update(nspMap, status);
        }
        logPrinter.print("\tEnd chain", EMsgType.INFO);
        logPrinter.close();
    }
}
