/*
    Copyright 2019-2026 Dmitry Isaenko

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
package nsusbloader.com.net;

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.com.helpers.NSSplitReader;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nsusbloader.com.net.NETPacket.*;

public class NETCommunications extends CancellableRunnable {

    private final static int SWITCH_PORT = 2000;
    private final static int CHUNK_SIZE = 1024;  // keep it small for better speed

    private final ILogPrinter logPrinter;

    private final String switchIp;
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

    private boolean jobInProgress = true;
    /**
     * Simple constructor that everybody uses
     * */
    public NETCommunications(List<File> filesList,
                             String switchIp,
                             boolean doNotServe,
                             String hostIP,
                             String hostPortNum,
                             String extras) {
        this.doNotServe = doNotServe;
        if (doNotServe)
            this.extras = extras;
        else
            this.extras = "";
        this.switchIp = switchIp;
        this.logPrinter = Log.getPrinter(EModule.USB_NET_TRANSFERS);

        var validator = new NetworkSetupValidator(filesList, doNotServe, hostIP, hostPortNum, logPrinter);

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
        if (! isValid || isCancelled())
            return;

        print("\tStart chain", EMsgType.INFO);

        if (sendListOfFiles())
            return;

        if (doNotServe) {
            print("List of files transferred. Replies won't be served.", EMsgType.PASS);
            close(EFileStatus.UNKNOWN);
            return;
        }
        print("Initiation files list has been sent to NS.", EMsgType.PASS);

        serveRequestsLoop();
    }
    private boolean sendListOfFiles() {
        try {
            final var prefix = hostIP + ':' + hostPort + '/' + extras;
            var payload = files.keySet().stream()
                    .map(fileName -> prefix + fileName)
                    .collect(Collectors.joining("\n", "", "\n"))
                    .getBytes(UTF_8);
            var payloadSize = ByteBuffer.allocate(Integer.BYTES)
                    .putInt(payload.length)
                    .array();

            var switchSocket = new Socket(InetAddress.getByName(switchIp), SWITCH_PORT);
            var switchStream = switchSocket.getOutputStream();
            switchStream.write(payloadSize);
            switchStream.write(payload);
            switchStream.flush();
            switchSocket.close();
            return false;
        }
        catch (Exception e) {
            print("Unable to connect to NS or send files list:%n         "+e.getMessage(), EMsgType.FAIL);
            close(EFileStatus.UNKNOWN);
            return true;
        }
    }
    private void serveRequestsLoop() {
        try {
            while (jobInProgress) {
                clientSocket = serverSocket.accept();

                currSockOS = clientSocket.getOutputStream();
                currSockPW = new PrintWriter(new OutputStreamWriter(currSockOS));

                try (var reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                    String line;
                    var tcpPacket = new ArrayList<String>();
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) {          // If TCP packet is ended
                            handleRequest(tcpPacket);         // Proceed required things
                            tcpPacket.clear();                // Clear data and wait for next TCP packet
                        }
                        else {
                            tcpPacket.add(line);              // Otherwise collect data
                        }
                    }
                    clientSocket.close();
                }
            }
        }
        catch (Exception e) {
            if (isCancelled())
                print("Interrupted by user.", EMsgType.INFO);
            else
                print(e.getMessage(), EMsgType.INFO);
            close(EFileStatus.UNKNOWN);
            return;
        }
        print("All transfers complete", EMsgType.PASS);
        close(EFileStatus.UPLOADED);
    }
    /**
     * Handle requests
     * @return true if failed
     * */
    private void handleRequest(List<String> packet) throws Exception {
        if (packet.getFirst().startsWith("DROP")) {
            jobInProgress = false;
            return;
        }
        
        var fileName = packet.getFirst().replaceAll("(^[A-z\\s]+/)|(\\s+?.*$)", "");

        if (! files.containsKey(fileName)) {
            writeToSocket(getCode404());
            print("File "+fileName+" doesn't exists or have 0 size. Reply 404", EMsgType.FAIL);
            return;
        }

        var file = files.get(fileName).getFile();
        var fileSize = files.get(fileName).getSize();

        if (! file.exists() || fileSize == 0) {   // reply 404 if file exists with 0 length. Saves time
            writeToSocket(getCode404());
            print("File "+file.getName()+" doesn't exists or have 0 size. Reply 404", EMsgType.FAIL);
            logPrinter.update(file, EFileStatus.FAILED);
            return;
        }
        if (packet.getFirst().startsWith("HEAD")) {
            writeToSocket(getCode200(fileSize));
            print("Replying for requested file: "+file.getName(), EMsgType.INFO);
            return;
        }
        if (packet.getFirst().startsWith("GET")) {
            for (var line: packet) {
                if (line.toLowerCase().startsWith("range")) {
                    parseGetRange(file, fileName, fileSize, line);
                    return;
                }
            }
        }
    }

    private void parseGetRange(File file, String fileName, long fileSize, String rangeDirective) throws Exception {
        try {
            var rangeStr = rangeDirective.toLowerCase()
                    .replaceAll("^range:\\s+?bytes=", "")
                    .split("-", 2);

            if (! rangeStr[0].isEmpty() && ! rangeStr[1].isEmpty()) {
                long fromRange = Long.parseLong(rangeStr[0]);
                long toRange = Long.parseLong(rangeStr[1]);

                if (fromRange > toRange) { // If start bytes greater than end bytes
                    writeToSocket(getCode400());
                    print("Requested range for "+file.getName()+" is incorrect. Reply 400", EMsgType.FAIL);
                    logPrinter.update(file, EFileStatus.FAILED);
                    return;
                }
                writeToSocket(fileName, fromRange, toRange);
                return;
            }

            if (! rangeStr[0].isEmpty()) { // If only START defined: Read all
                writeToSocket(fileName, Long.parseLong(rangeStr[0]), fileSize-1);
                return;
            }

            if (rangeStr[1].isEmpty()) { // If Range not defined: like "Range: bytes=-"
                writeToSocket(getCode400());
                print("Requested range for "+file.getName()+" is incorrect. Reply 400", EMsgType.FAIL);
                logPrinter.update(file, EFileStatus.FAILED);
                return;
            }

            if (fileSize > 500) {
                writeToSocket(fileName, fileSize - 500, fileSize);
                return;
            }
            // If file smaller than 500 bytes
            writeToSocket(getCode416());
            print("%s file requested size of %s. Reply 416".formatted(file.getName(), fileSize), EMsgType.FAIL);
            logPrinter.update(file, EFileStatus.FAILED);
        }
        catch (NumberFormatException nfe) {
            writeToSocket(getCode400());
            print("Requested range for "+file.getName()+" has incorrect format. Reply 400\n\t"+nfe.getMessage(),
                    EMsgType.FAIL);
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
    private void writeToSocket(String fileName, long start, long end) throws Exception {
        print("Reply to: %s%n         0x%x-0x%x | %d-%d".formatted(fileName, start, end, start, end), EMsgType.INFO);
        
        writeToSocket(getCode206(files.get(fileName).getSize(), start, end));
        var file = files.get(fileName).getFile();
        try {
            handleFile(file, start, end, file.isDirectory());
            logPrinter.updateProgress(1.0);
        }
        catch (Exception e) {
            logPrinter.update(file, EFileStatus.FAILED);
            throw new Exception("File transmission failed:%n         "+e.getMessage());
        }
    }

    private void handleFile(File file, long start, long end, boolean isSplit) throws Exception {
        int readPice = CHUNK_SIZE;
        long offset = 0;

        try (var inStream = isSplit?
                new NSSplitReader(file, start):
                new BufferedInputStream(new FileInputStream(file))) {

            if (!isSplit && inStream.skip(start) != start)
                throw new IOException("Unable to skip requested range.");

            var count = end-start+1;
            while (offset < count) {
                if ((offset + readPice) >= count)
                    readPice = Math.toIntExact(count - offset);

                var byteBuf = new byte[readPice];

                if (inStream.read(byteBuf) != readPice)
                    throw new IOException("File stream suddenly ended.");
                currSockOS.write(byteBuf);
                logPrinter.updateProgress((offset + readPice) / (count / 100.0) / 100.0);
                offset += readPice;
            }
            currSockOS.flush();
        }
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }
    public Socket getClientSocket() {
        return clientSocket;
    }
    /**
     * Close when done
     * */
    private void close(EFileStatus status) {
        try {
            if (serverSocket != null && ! serverSocket.isClosed()) {
                serverSocket.close();
                print("Closing server socket.", EMsgType.PASS);
            }
        }
        catch (IOException ioe) {
            print("Closing server socket failed. Sometimes it's not an issue.", EMsgType.WARNING);
        }

        var tempMap = files.values().stream().collect(Collectors.toMap(
                uniFile -> uniFile.getFile().getName(), 
                uniFile -> uniFile.getFile()));
        
        logPrinter.update(tempMap, status);
        print("\tEnd chain", EMsgType.INFO);
        logPrinter.close();
    }
    private void print(String message, EMsgType type) {
        try {
            logPrinter.print(message, type);
        }
        catch (InterruptedException ignored) {}
    }
}