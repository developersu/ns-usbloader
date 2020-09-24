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

import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

public class NetworkSetupValidator {

    private String hostIP;
    private int hostPort;
    private final HashMap<String, UniFile> files;
    private ServerSocket serverSocket;
    private final boolean valid;
    private final ILogPrinter logPrinter;

    private final boolean doNotServe;

    NetworkSetupValidator(List<File> filesList,
                                          boolean doNotServe,
                                          String hostIP,
                                          String hostPortNum,
                                          ILogPrinter logPrinter) {
        this.files = new HashMap<>();
        this.logPrinter = logPrinter;
        this.doNotServe = doNotServe;

        try {
            validateFiles(filesList);
            encodeAndAddFilesToMap(filesList);
            resolveIp(hostIP);
            resolvePort(hostPortNum);
        }
        catch (Exception e){
            logPrinter.print(e.getMessage(), EMsgType.FAIL);
            valid = false;
            return;
        }
        valid = true;
    }

    private void validateFiles(List<File> filesList) {
        filesList.removeIf(f -> {
            if (f.isFile())
                return false;

            File[] subFiles = f.listFiles((file, name) -> name.matches("[0-9]{2}"));

            if (subFiles == null || subFiles.length == 0) {
                logPrinter.print("NET: Exclude folder: " + f.getName(), EMsgType.WARNING);
                return true;
            }

            Arrays.sort(subFiles, Comparator.comparingInt(file -> Integer.parseInt(file.getName())));

            for (int i = subFiles.length - 2; i > 0 ; i--){
                if (subFiles[i].length() < subFiles[i-1].length()) {
                    logPrinter.print("NET: Exclude split file: "+f.getName()+
                            "\n      Chunk sizes of the split file are not the same, but has to be.", EMsgType.WARNING);
                    return true;
                }
            }
            return false;
        });
    }

    private void encodeAndAddFilesToMap(List<File> filesList) throws UnsupportedEncodingException, FileNotFoundException {
        for (File file : filesList){
            String encodedName = URLEncoder.encode(file.getName(), "UTF-8").replaceAll("\\+", "%20"); // replace '+' to '%20'
            UniFile uniFile = new UniFile(file);
            files.put(encodedName, uniFile);
        }

        if (files.size() == 0) {
            throw new FileNotFoundException("NET: No files to send.");
        }
    }

    private void resolveIp(String hostIPaddr) throws IOException{
        if (! hostIPaddr.isEmpty()){
            this.hostIP = hostIPaddr;
            logPrinter.print("NET: Host IP defined as: " + hostIP, EMsgType.PASS);
            return;
        }

        if (findIpUsingHost("google.com"))
            return;

        if (findIpUsingHost("people.com.cn"))
            return;

        throw new IOException("Try using 'Expert mode' and set IP manually. " + getAvaliableIpExamples());
    }

    private boolean findIpUsingHost(String host) {
        try {
            Socket scoketK;
            scoketK = new Socket();
            scoketK.connect(new InetSocketAddress(host, 80));
            hostIP = scoketK.getLocalAddress().getHostAddress();
            scoketK.close();

            logPrinter.print("NET: Host IP detected as: " + hostIP, EMsgType.PASS);
            return true;
        }
        catch (IOException e){
            logPrinter.print("NET: Can't get your computer IP using "
                    + host
                    + " server (InetSocketAddress). Returned:\n\t"+e.getMessage(), EMsgType.INFO);
            return false;
        }
    }

    private String getAvaliableIpExamples(){
        try {
            StringBuilder builder = new StringBuilder("Check for:\n");
            Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
            while (enumeration.hasMoreElements()) {
                NetworkInterface n = enumeration.nextElement();
                Enumeration<InetAddress> enumeration1 = n.getInetAddresses();
                while (enumeration1.hasMoreElements()){
                    builder.append("- ");
                    builder.append(enumeration1.nextElement().getHostAddress());
                    builder.append("\n");
                }
            }
            return builder.toString();
        }
        catch (SocketException socketException) {
            return "";
        }
    }

    private void resolvePort(String hostPortNum) throws Exception{
        if (! hostPortNum.isEmpty()) {
            parsePort(hostPortNum);
            return;
        }

        if (doNotServe)
            throw new Exception("NET: Port must be defined if 'Don't serve requests' option selected!");

        findPort();
    }

    private void findPort() throws Exception{
        Random portRandomizer = new Random();
        for (int i = 0; i < 5; i++) {
            try {
                this.hostPort = portRandomizer.nextInt(999) + 6000;
                serverSocket = new ServerSocket(hostPort);  //System.out.println(serverSocket.getInetAddress()); 0.0.0.0
                logPrinter.print("NET: Your port detected as: " + hostPort, EMsgType.PASS);
                break;
            }
            catch (IOException ioe) {
                if (i == 4) {
                    throw new Exception("NET: Can't find good port\n"
                            + "Set port by in settings ('Expert mode').");
                }

                logPrinter.print("NET: Can't use port " + hostPort + "\nLooking for another one.", EMsgType.WARNING);
            }
        }
    }

    private void parsePort(String hostPortNum) throws Exception{
        try {
            this.hostPort = Integer.parseInt(hostPortNum);

            if (doNotServe)
                return;

            serverSocket = new ServerSocket(hostPort);
            logPrinter.print("NET: Using defined port number: " + hostPort, EMsgType.PASS);
        }
        catch (IllegalArgumentException | IOException eee){
            throw new Exception("NET: Can't use port defined in settings: " + hostPortNum + "\n\t"+eee.getMessage());
        }
    }

    String getHostIP() { return hostIP; }
    int getHostPort() { return hostPort; }
    HashMap<String, UniFile> getFiles() { return files; }
    ServerSocket getServerSocket() { return serverSocket; }
    boolean isValid() { return valid; }
}
