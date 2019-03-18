package nsusbloader.NET;

import javafx.concurrent.Task;
import nsusbloader.NSLMain;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
/*
* Add option: don't serve replies
* */

public class NETCommunications extends Task<Void> { // todo: thows IOException?

    private String hostIP;
    private int hostPort;
    private String switchIP;

    private HashMap<String, File> nspMap;


    private ServerSocket serverSocket;

    public NETCommunications(List<File> filesList, String switchIP){
        this.switchIP = switchIP;
        this.nspMap = new HashMap<>();
        try {
            for (File nspFile : filesList)
                nspMap.put(URLEncoder.encode(nspFile.getName(), "UTF-8"), nspFile);
        }
        catch (UnsupportedEncodingException uee){
            uee.printStackTrace();
            return;                    // TODO: FIX
        }

        try{        // todo: check other method if internet unavaliable
            DatagramSocket socket = new DatagramSocket();
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);    //193.0.14.129 RIPE NCC
            hostIP = socket.getLocalAddress().getHostAddress();
            //System.out.println(hostIP);
            socket.close();
        }
        catch (SocketException | UnknownHostException e){
            e.printStackTrace();
        }
        this.hostPort = 6000;                         // TODO: fix

        try {

            serverSocket = new ServerSocket(hostPort); // TODO: randomize
            //System.out.println(serverSocket.getInetAddress()); 0.0.0.0
        }
        catch (IOException ioe){
            ioe.printStackTrace();
            System.out.println("unable to use socket");
        }

    }
/*
Replace everything to ASCII (WEB representation)
calculate
write in first 4 bytes
* */
    @Override
    protected Void call() {
        // Get files list length
        StringBuilder myStrBuilder;

        myStrBuilder = new StringBuilder();
        for (String fileNameEncoded : nspMap.keySet()) {
            myStrBuilder.append(hostIP);
            myStrBuilder.append(':');
            myStrBuilder.append(hostPort);
            myStrBuilder.append('/');
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

        return null;
    }

    // 200 206 400 (inv range) 404
    private void handleRequest(LinkedList<String> packet, PrintWriter pw){
        if (packet.get(0).startsWith("HEAD")){
            File requestedFile = nspMap.get(packet.get(0).replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", ""));
            if (requestedFile == null || !requestedFile.exists()){
                return;                     //todo: send 404
            }
            else {
                pw.write(NETPacket.getCode200(requestedFile.length()));
                pw.flush();
                System.out.println(requestedFile.getAbsolutePath()+"\n"+NETPacket.getCode200(requestedFile.length()));
            }
        }
    }
}
