package nsusbloader.NET;

import javafx.concurrent.Task;

import java.io.*;
import java.net.*;

public class NETCommunications extends Task<Void> { // todo: thows IOException

    private String hostIP;
    private String switchIP;

    private ServerSocket serverSocket;

    public NETCommunications(String switchIP){
        this.switchIP = switchIP;
        try{        // todo: check other method if internet unavaliable
            DatagramSocket socket = new DatagramSocket();
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);    //193.0.14.129 RIPE NCC
            hostIP = socket.getLocalAddress().getHostAddress();
            System.out.println(hostIP);
            socket.close();
        }
        catch (SocketException | UnknownHostException e){
            e.printStackTrace();
        }

        try {
            serverSocket = new ServerSocket(6000); // TODO: randomize
            //System.out.println(serverSocket.getInetAddress()); 0.0.0.0
        }
        catch (IOException ioe){
            ioe.printStackTrace();
            System.out.println("unable to use socket");
        }
    }

    @Override
    protected Void call() throws Exception {
        Socket clientSocket = serverSocket.accept();

        InputStream is = clientSocket.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);

        OutputStream os = clientSocket.getOutputStream();
        OutputStreamWriter osr = new OutputStreamWriter(os);
        PrintWriter pw = new PrintWriter(osr);

        String line;
        while ((line = br.readLine()) != null) {
            if (line.equals("hello world")) {
                pw.write("stop doing it!");
                pw.flush();
                break;
            }
            System.out.println(line);
        }
        serverSocket.close();
        return null;
    }
}
