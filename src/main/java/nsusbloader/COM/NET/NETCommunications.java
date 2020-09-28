/*
    Copyright 2019-2020 Dmitry Isaenko

    This file is part of NS-USBloader.

    NS-USBloader is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NS-USBloader is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NS-USBloader.  If not, see <https://www.gnu.org/licenses/>.
*/
package nsusbloader.COM.NET;

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.COM.Helpers.NSSplitReader;
import nsusbloader.RainbowHexDump;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NETCommunications extends CancellableRunnable {

    private final ILogPrinter logPrinter;

    private final String switchIP;
    private final static int SWITCH_PORT = 2000;
    private final String hostIP;
    private final int hostPort;
    private final String extras;
    private final boolean doNotServe;

    private final HashMap<String, UniFile> files;

    private final ServerSocket serverSocket;
    private Socket clientSocket;

    private final boolean isValid;

    private OutputStream currSockOS;
    private PrintWriter currSockPW;
    /**
     * Simple constructor that everybody uses
     * */
    public NETCommunications(List<File> filesList,
                             String switchIP,
                             boolean doNotServe,
                             String hostIP,
                             String hostPortNum,
                             String extras)
    {
        this.doNotServe = doNotServe;
        if (doNotServe)
            this.extras = extras;
        else
            this.extras = "";
        this.switchIP = switchIP;
        this.logPrinter = Log.getPrinter(EModule.USB_NET_TRANSFERS);

        NetworkSetupValidator validator =
                new NetworkSetupValidator(filesList, doNotServe, hostIP, hostPortNum, logPrinter);

        this.hostIP = validator.getHostIP();
        this.hostPort = validator.getHostPort();
        this.files = validator.getFiles();
        this.serverSocket = validator.getServerSocket();
        this.isValid = validator.isValid();

        if (! isValid)
            close(EFileStatus.FAILED);
    }

    @Override
    public void run() {
        if (! isValid || isCancelled() )
            return;

        logPrinter.print("\tStart chain", EMsgType.INFO);

        final String handshakeContent = buildHandshakeContent();

        byte[] handshakeCommand = handshakeContent.getBytes(StandardCharsets.UTF_8);
        byte[] handshakeCommandSize = ByteBuffer.allocate(Integer.BYTES).putInt(handshakeCommand.length).array();

        if (sendHandshake(handshakeCommandSize, handshakeCommand))
            return;

        // Check if we should serve requests
        if (this.doNotServe){
            logPrinter.print("List of files transferred. Replies won't be served.", EMsgType.PASS);
            close(EFileStatus.UNKNOWN);
            return;
        }
        logPrinter.print("Initiation files list has been sent to NS.", EMsgType.PASS);

        // Go transfer
        serveRequestsLoop();
    }
    /**
    * Create string that we'll send to TF/AW and which initiates chain
    * */
    private String buildHandshakeContent(){
        StringBuilder builder = new StringBuilder();

        for (String fileNameEncoded : files.keySet()) {
            builder.append(hostIP);
            builder.append(':');
            builder.append(hostPort);
            builder.append('/');
            builder.append(extras);
            builder.append(fileNameEncoded);
            builder.append('\n');
        }

        return builder.toString();
    }

    private boolean sendHandshake(byte[] handshakeCommandSize, byte[] handshakeCommand){
        try {
            Socket handshakeSocket = new Socket(InetAddress.getByName(switchIP), SWITCH_PORT);
            OutputStream os = handshakeSocket.getOutputStream();

            os.write(handshakeCommandSize);
            os.write(handshakeCommand);
            os.flush();

            handshakeSocket.close();
        }
        catch (IOException uhe){
            logPrinter.print("Unable to connect to NS and send files list:\n         "
                    + uhe.getMessage(), EMsgType.FAIL);
            close(EFileStatus.UNKNOWN);
            return true;
        }
        return false;
    }
    private void serveRequestsLoop(){
        try {
            while (true){
                clientSocket = serverSocket.accept();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream())
                );

                currSockOS = clientSocket.getOutputStream();
                currSockPW = new PrintWriter(new OutputStreamWriter(currSockOS));

                String line;
                LinkedList<String> tcpPacket = new LinkedList<>();
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) {          // If TCP packet is ended
                        handleRequest(tcpPacket);     // Proceed required things
                        tcpPacket.clear();                // Clear data and wait for next TCP packet
                    }
                    else
                        tcpPacket.add(line);              // Otherwise collect data
                }
                clientSocket.close();
            }
        }
        catch (Exception e){
            if (isCancelled())
                logPrinter.print("Interrupted by user.", EMsgType.INFO);
            else
                logPrinter.print(e.getMessage(), EMsgType.INFO);
            close(EFileStatus.UNKNOWN);
        }
    }
    /**
     * Handle requests
     * @return true if failed
     * */
    private void handleRequest(LinkedList<String> packet) throws Exception{
        if (packet.get(0).startsWith("DROP")){
            throw new Exception("All transfers finished");
        }

        File requestedFile;
        String reqFileName = packet.get(0).replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", "");

        if (! files.containsKey(reqFileName)){
            writeToSocket(NETPacket.getCode404());
            logPrinter.print("File "+reqFileName+" doesn't exists or have 0 size. Returning 404", EMsgType.FAIL);
            return;
        }

        long reqFileSize = files.get(reqFileName).getSize();
        requestedFile = files.get(reqFileName).getFile();

        if (! requestedFile.exists() || reqFileSize == 0){   // well.. tell 404 if file exists with 0 length is against standard, but saves time
            writeToSocket(NETPacket.getCode404());
            logPrinter.print("File "+requestedFile.getName()+" doesn't exists or have 0 size. Returning 404", EMsgType.FAIL);
            logPrinter.update(requestedFile, EFileStatus.FAILED);
            return;
        }
        if (packet.get(0).startsWith("HEAD")){
            writeToSocket(NETPacket.getCode200(reqFileSize));
            logPrinter.print("Replying for requested file: "+requestedFile.getName(), EMsgType.INFO);
            return;
        }
        if (packet.get(0).startsWith("GET")) {
            for (String line: packet) {
                if (! line.toLowerCase().startsWith("range"))                //todo: fix
                    continue;

                parseGETrange(requestedFile, reqFileName, reqFileSize, line);
                return;
            }
        }
    }

    private void parseGETrange(File file, String fileName, long fileSize, String rangeDirective) throws Exception{
        try {
            String[] rangeStr = rangeDirective.toLowerCase().replaceAll("^range:\\s+?bytes=", "").split("-", 2);

            if (! rangeStr[0].isEmpty() && ! rangeStr[1].isEmpty()) {      // If both ranges defined: Read requested
                long fromRange = Long.parseLong(rangeStr[0]);
                long toRange = Long.parseLong(rangeStr[1]);

                if (fromRange > toRange){ // If start bytes greater then end bytes
                    writeToSocket(NETPacket.getCode400());
                    logPrinter.print("Requested range for "
                            + file.getName()
                            + " is incorrect. Returning 400", EMsgType.FAIL);
                    logPrinter.update(file, EFileStatus.FAILED);
                    return;
                }
                writeToSocket(fileName, fromRange, toRange);
                return;
            }

            if (! rangeStr[0].isEmpty()) { // If only START defined: Read all
                writeToSocket(fileName, Long.parseLong(rangeStr[0]), fileSize);
                return;
            }

            if (rangeStr[1].isEmpty()) { // If Range not defined: like "Range: bytes=-"
                writeToSocket(NETPacket.getCode400());
                logPrinter.print("Requested range for "
                        + file.getName()
                        + " is incorrect (empty start & end). Returning 400", EMsgType.FAIL);
                logPrinter.update(file, EFileStatus.FAILED);
                return;
            }

            if (fileSize > 500){
                writeToSocket(fileName, fileSize - 500, fileSize);
                return;
            }
            // If file smaller than 500 bytes
            writeToSocket(NETPacket.getCode416());
            logPrinter.print("File size requested for "
                    + file.getName()
                    + " while actual size of it: "
                    + fileSize+". Returning 416", EMsgType.FAIL);
            logPrinter.update(file, EFileStatus.FAILED);
        }
        catch (NumberFormatException nfe){
            writeToSocket(NETPacket.getCode400());
            logPrinter.print("Requested range for "
                    + file.getName()
                    + " has incorrect format. Returning 400\n\t"
                    + nfe.getMessage(), EMsgType.FAIL);
            logPrinter.update(file, EFileStatus.FAILED);
        }
    }

    private void writeToSocket(String string) {
        currSockPW.write(string);
        currSockPW.flush();
    }
    /**
     * Send files.
     * */
    private void writeToSocket(String fileName, long start, long end) throws Exception{
        File file = files.get(fileName).getFile();

        logPrinter.print("Reply to range: "+start+"-"+end, EMsgType.INFO);

        writeToSocket(NETPacket.getCode206(files.get(fileName).getSize(), start, end));
        try{
            if (file.isDirectory())
                handleSplitFile(file, start, end);
            else
                handleRegularFile(file, start, end);

            logPrinter.updateProgress(1.0);
        }
        catch (Exception e){
            logPrinter.update(file, EFileStatus.FAILED);
            throw new Exception("File transmission failed:\n         "+e.getMessage());
        }
    }

    private void handleSplitFile(File file, long start, long end) throws Exception{
        long count = end - start + 1;

        int readPice = 1024;// NOTE: keep it small for better speed
        byte[] byteBuf;
        long currentOffset = 0;

        NSSplitReader nsr = new NSSplitReader(file, start);

        while (currentOffset < count){
            if ((currentOffset + readPice) >= count){
                readPice = Math.toIntExact(count - currentOffset);
            }
            byteBuf = new byte[readPice];

            if (nsr.read(byteBuf) != readPice)
                throw new IOException("File stream suddenly ended.");

            currSockOS.write(byteBuf);
            logPrinter.updateProgress((currentOffset+readPice)/(count/100.0) / 100.0);

            currentOffset += readPice;
        }
        currSockOS.flush();         // TODO: check if this really needed.
        nsr.close();
    }
    private void handleRegularFile(File file, long start, long end) throws Exception{
        long count = end - start + 1;

        int readPice = 1024; // NOTE: keep it small for better speed
        byte[] byteBuf;
        long currentOffset = 0;

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

        if (bis.skip(start) != start)
            throw new IOException("Unable to skip requested range.");

        while (currentOffset < count){

            if ((currentOffset + readPice) >= count){
                readPice = Math.toIntExact(count - currentOffset);
            }
            byteBuf = new byte[readPice];

            if (bis.read(byteBuf) != readPice){
                throw new IOException("File stream suddenly ended.");
            }
            currSockOS.write(byteBuf);
            logPrinter.updateProgress((currentOffset+readPice)/(count/100.0) / 100.0);
            currentOffset += readPice;
        }
        currSockOS.flush();         // TODO: check if this really needed.
        bis.close();
    }

    public ServerSocket getServerSocket(){
        return serverSocket;
    }
    public Socket getClientSocket(){
        return clientSocket;
    }
    /**
     * Close when done
     * */
    private void close(EFileStatus status){
        try {
            if (serverSocket != null && ! serverSocket.isClosed()) {
                serverSocket.close();
                logPrinter.print("Closing server socket.", EMsgType.PASS);
            }
        }
        catch (IOException ioe){
            logPrinter.print("Closing server socket failed. Sometimes it's not an issue.", EMsgType.WARNING);
        }

        HashMap<String, File> tempMap = new HashMap<>();
        for (UniFile sf : files.values())
            tempMap.put(sf.getFile().getName(), sf.getFile());

        logPrinter.update(tempMap, status);

        logPrinter.print("\tEnd chain", EMsgType.INFO);
        logPrinter.close();
    }
}