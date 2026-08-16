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

import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class NetworkSetupValidator {

    private String hostIp;
    private int hostPort;
    private final HashMap<String, UniFile> files;
    private ServerSocket serverSocket;
    private final boolean valid;
    private final ILogPrinter logPrinter;

    private final boolean doNotServe;

    NetworkSetupValidator(List<File> filesList,
                          boolean doNotServe,
                          String hostIp,
                          String hostPortNum,
                          ILogPrinter logPrinter) {
        this.files = new HashMap<>();
        this.logPrinter = logPrinter;
        this.doNotServe = doNotServe;

        try {
            filesList.removeIf(this::fileValidate);
            encodeAndAddFilesToMap(filesList);
            resolveIp(hostIp);
            resolvePort(hostPortNum);
        }
        catch (Exception e) {
            try {
                logPrinter.print(e.getMessage(), EMsgType.FAIL);
            }
            catch (InterruptedException ignore) {}
            valid = false;
            return;
        }
        valid = true;
    }

    private boolean fileValidate(File file) {
        try {
            if (file.isFile())
                return false;

            var subFiles = file.listFiles((myFile, name) -> name.matches("[0-9]{2}"));

            if (subFiles == null || subFiles.length == 0) {
                logPrinter.print("NET: Exclude folder: " + file.getName(), EMsgType.WARNING);
                return true;
            }

            Arrays.sort(subFiles, Comparator.comparingInt(myFile -> Integer.parseInt(myFile.getName())));

            for (int i = subFiles.length - 2; i > 0; i--) {
                if (subFiles[i].length() != subFiles[i - 1].length()) {
                    logPrinter.print("NET: Exclude split file: " + file.getName() +
                            "\n      Chunk sizes of the split file are not the same, but has to be.", EMsgType.WARNING);
                    return true;
                }
            }

            long firstFileLength = subFiles[0].length();
            long lastFileLength = subFiles[subFiles.length - 1].length();

            if (lastFileLength > firstFileLength) {
                logPrinter.print("NET: Exclude split file: " + file.getName() +
                        "\n      Chunk sizes of the split file are not the same, but has to be.", EMsgType.WARNING);
                return true;
            }
            return false;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private void encodeAndAddFilesToMap(List<File> filesList) throws FileNotFoundException {
        filesList.forEach(file -> files.put(encodeUri(file), new UniFile(file)));

        if (files.isEmpty()) {
            throw new FileNotFoundException("NET: No files to send.");
        }
    }

    private String encodeUri(File file) {
        return URLEncoder.encode(file.getName(), UTF_8).replaceAll("\\+", "%20"); // replace '+' to '%20'
    }

    private void resolveIp(String hostIpAddr) throws IOException, InterruptedException {
        if (!hostIpAddr.isEmpty()) {
            hostIp = hostIpAddr;
            logPrinter.print("NET: Host IP defined as: " + hostIp, EMsgType.PASS);
            return;
        }

        if (findIpUsingHost("google.com"))
            return;

        if (findIpUsingHost("people.com.cn"))
            return;

        throw new IOException("Try using 'Expert mode' and set IP manually.\n"+getAvailableIpExamples());
    }

    private boolean findIpUsingHost(String host) throws InterruptedException {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 80));
            hostIp = socket.getLocalAddress().getHostAddress();
            logPrinter.print("NET: Host IP detected as: " + hostIp, EMsgType.PASS);
            return true;
        }
        catch (IOException e) {
            logPrinter.print("NET: Can't get your computer IP using "
                    + host
                    + " server (InetSocketAddress). Returned:\n\t" + e.getMessage(), EMsgType.INFO);
            return false;
        }
    }

    private String getAvailableIpExamples() {
        try {
            var builder = new StringBuilder("Check for:\n");
            var netInterfaces = NetworkInterface.getNetworkInterfaces();
            while (netInterfaces.hasMoreElements()) {
                var inetAddresses = netInterfaces.nextElement().getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    builder.append("- ");
                    builder.append(inetAddresses.nextElement().getHostAddress());
                    builder.append("\n");
                }
            }
            return builder.toString();
        }
        catch (SocketException ignored) {
            return "¯\\_(ツ)_/¯";
        }
    }

    private void resolvePort(String hostPortNum) throws Exception {
        if (!hostPortNum.isEmpty()) {
            parsePort(hostPortNum);
            return;
        }

        if (doNotServe)
            throw new Exception("NET: Port must be defined if 'Don't serve requests' option selected!");

        findPort();
    }

    private void findPort() throws Exception {
        var portRandom = new Random();
        for (int i = 0; i < 5; i++) {
            try {
                hostPort = portRandom.nextInt(999) + 6000;
                serverSocket = new ServerSocket(hostPort);  //System.out.println(serverSocket.getInetAddress()); 0.0.0.0
                logPrinter.print("NET: Your port detected as: " + hostPort, EMsgType.PASS);
                break;
            }
            catch (IOException ioe) {
                if (i == 4) {
                    throw new Exception("NET: Can't find good port\nSet port by in settings ('Expert mode').");
                }
                logPrinter.print("NET: Can't use port %s\nLooking for another one.".formatted(hostPort), EMsgType.WARNING);
            }
        }
    }

    private void parsePort(String hostPortNum) throws Exception {
        try {
            hostPort = Integer.parseInt(hostPortNum);

            if (doNotServe)
                return;

            serverSocket = new ServerSocket(hostPort);
            logPrinter.print("NET: Using defined port number: " + hostPort, EMsgType.PASS);
        } catch (IllegalArgumentException | IOException e) {
            throw new Exception("NET: Can't use port defined in settings: %s\n\t%s".formatted(hostPortNum, e.getMessage()));
        }
    }

    String getHostIP() {
        return hostIp;
    }

    int getHostPort() {
        return hostPort;
    }

    HashMap<String, UniFile> getFiles() {
        return files;
    }

    ServerSocket getServerSocket() {
        return serverSocket;
    }

    boolean isValid() {
        return valid;
    }
}
