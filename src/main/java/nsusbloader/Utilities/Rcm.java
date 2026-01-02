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
    --
    Implementation of the 'Fusée Gelée' RCM payload that is inspired by 'fusee-launcher' application by ktemkin.
    Definitely uses ideas and even some code.
    Check original project: https://github.com/reswitched/fusee-launcher

    This code is not political. It could be used by anyone.
*/
package nsusbloader.Utilities;

import nsusbloader.com.usb.UsbConnect;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class Rcm implements Runnable{

    private enum ECurrentOS {
        win, lin, mac, unsupported
    }

    private final ILogPrinter logPrinter;
    private final String filePath;

    private DeviceHandle handler;

    private byte[] fullPayload;

    private static final byte[] initSeq = { (byte) 0x98, (byte) 0x02, (byte) 0x03 };

    private static final byte[] mezzo = {
        (byte) 0x5c, (byte) 0x00, (byte) 0x9f, (byte) 0xe5, (byte) 0x5c, (byte) 0x10, (byte) 0x9f, (byte) 0xe5,   (byte) 0x5c, (byte) 0x20, (byte) 0x9f, (byte) 0xe5, (byte) 0x01, (byte) 0x20, (byte) 0x42, (byte) 0xe0,
        (byte) 0x0e, (byte) 0x00, (byte) 0x00, (byte) 0xeb, (byte) 0x48, (byte) 0x00, (byte) 0x9f, (byte) 0xe5,   (byte) 0x10, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, (byte) 0x00, (byte) 0x00, (byte) 0xa0, (byte) 0xe1,
        (byte) 0x48, (byte) 0x00, (byte) 0x9f, (byte) 0xe5, (byte) 0x48, (byte) 0x10, (byte) 0x9f, (byte) 0xe5,   (byte) 0x01, (byte) 0x29, (byte) 0xa0, (byte) 0xe3, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0xeb,
        (byte) 0x38, (byte) 0x00, (byte) 0x9f, (byte) 0xe5, (byte) 0x01, (byte) 0x19, (byte) 0xa0, (byte) 0xe3,   (byte) 0x01, (byte) 0x00, (byte) 0x80, (byte) 0xe0, (byte) 0x34, (byte) 0x10, (byte) 0x9f, (byte) 0xe5,
        (byte) 0x03, (byte) 0x28, (byte) 0xa0, (byte) 0xe3, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0xeb,   (byte) 0x20, (byte) 0x00, (byte) 0x9f, (byte) 0xe5, (byte) 0x10, (byte) 0xff, (byte) 0x2f, (byte) 0xe1,
        (byte) 0x04, (byte) 0x30, (byte) 0x91, (byte) 0xe4, (byte) 0x04, (byte) 0x30, (byte) 0x80, (byte) 0xe4,   (byte) 0x04, (byte) 0x20, (byte) 0x52, (byte) 0xe2, (byte) 0xfb, (byte) 0xff, (byte) 0xff, (byte) 0x1a,
        (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1, (byte) 0x00, (byte) 0xf0, (byte) 0x00, (byte) 0x40,   (byte) 0x20, (byte) 0x00, (byte) 0x01, (byte) 0x40, (byte) 0x7c, (byte) 0x00, (byte) 0x01, (byte) 0x40,
        (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x40, (byte) 0x40, (byte) 0x0e, (byte) 0x01, (byte) 0x40,   (byte) 0x00, (byte) 0x70, (byte) 0x01, (byte) 0x40
    }; // 124 bytes

    private static final byte[] sprayPttrn = { 0x00, 0x00, 0x01, 0x40};

    public Rcm(String filePath){
        this.logPrinter = Log.getPrinter(EModule.RCM);
        this.filePath = filePath;
    }

    @Override
    public void run() {
        print("Selected: "+filePath, EMsgType.INFO);
        print("=============== RCM ===============", EMsgType.INFO);

        ECurrentOS ecurrentOS;
        String realOsName = System.getProperty("os.name").toLowerCase().replace(" ", "");
        if (realOsName.equals("macos") || realOsName.equals("macosx") || realOsName.equals("freebsd"))
            ecurrentOS = ECurrentOS.mac;
        else if (realOsName.contains("windows"))
            ecurrentOS = ECurrentOS.win;
        else if (realOsName.equals("linux"))
            ecurrentOS = ECurrentOS.lin;
        else
            ecurrentOS = ECurrentOS.unsupported;
        print("Found your OS: "+System.getProperty("os.name"), EMsgType.PASS);

        if (! ecurrentOS.equals(ECurrentOS.mac)){
            if (! RcmSmash.isSupported()){
                print("Unfortunately your platform '"+System.getProperty("os.name")+
                        "' of '"+System.getProperty("os.arch")+"' is not supported :("+
                        "\n         But you could file a bug with request."+
                        "\n\n         Nothing has been sent to NS. Execution stopped.", EMsgType.FAIL);
                logPrinter.close();
                return;
            }
        }

        if (preparePayload()){
            logPrinter.close();
            return;
        }
        // === TEST THIS ===
        // writeTestFile();
        // =================
        // Bring up USB connection

        UsbConnect usbConnect = UsbConnect.connectRcmMode(logPrinter);
        
        if (! usbConnect.isConnected()){
            logPrinter.close();
            return;
        }
        this.handler = usbConnect.getNsHandler();

        // Get device ID and show it.
        if (readUsbDeviceID()){
            usbConnect.close();
            logPrinter.close();
            return;
        }
        // Send payload
        for (int i=0; i < fullPayload.length / 4096 ; i++){
            if (writeUsb(Arrays.copyOfRange(fullPayload, i*4096, (i+1)*4096))){
                print("Failed to sent payload ["+i+"]"+
                        "\n\n         Execution stopped.", EMsgType.FAIL);
                usbConnect.close();
                logPrinter.close();
                return;
            }
        }
        print("Information sent to NS.", EMsgType.PASS);

        if (ecurrentOS.equals(ECurrentOS.mac)){
            if (smashMacOS()){
                usbConnect.close();
                logPrinter.close();
                return;
            }
        }
        else {
            // JNI MAGIC HERE
            int retval;
            if (ecurrentOS.equals(ECurrentOS.lin))
                retval = RcmSmash.smashLinux(usbConnect.getNsBus(), usbConnect.getNsAddress());
            else if (ecurrentOS.equals(ECurrentOS.win))
                retval = RcmSmash.smashWindows();
            else {
                // ( ?_?)
                print("Failed to smash the stack since your OS is not supported. Please report this issue."+
                        "\n\n         Execution stopped and failed. And it's strange.", EMsgType.FAIL);
                usbConnect.close();
                logPrinter.close();
                return;
            }

            if (retval != 0){
                print("Failed to smash the stack ("+retval+")"+
                        "\n\n         Execution stopped and failed.", EMsgType.FAIL);
                usbConnect.close();
                logPrinter.close();
                return;
            }
        }
        print(".:: Payload complete ::.", EMsgType.PASS);
        usbConnect.close();
        logPrinter.updateOneLinerStatus(true);
        logPrinter.close();
    }
    
    private void print(String message, EMsgType type){
        try {
            logPrinter.print(message, type);
        }
        catch (InterruptedException intr){
            intr.printStackTrace();
        }
    }
    /**
     * Prepare the 'big' or full-size byte-buffer that is actually is a payload that we're about to use.
     * @return false for issues
     *          true for good result
     * */
    private boolean preparePayload(){
        File pldrFile = new File(filePath);

        // 126296 b <- biggest size per CTCaer; 16384 selected randomly as minimum threshold. It's probably wrong.
        if (pldrFile.length() > 126296 || pldrFile.length() < 16384) {
            print("File size of this payload looks wired. It's "+pldrFile.length()+" bytes."+
                    "\n         1. Double-check that you're using the right payload." +
                    "\n         2. Please report this issue in case you're sure that you're doing everything right." +
                    "\n\n         Nothing has been sent to NS. Execution stopped.", EMsgType.FAIL);
            return true;
        }
        // Get payload file size
        int pldFileSize = (int) pldrFile.length();
        // Get full payload array size
        int totalSize = 4328 + pldFileSize + 8640;
        totalSize += 4096 - (totalSize % 4096);
        if ((totalSize / 4096 % 2) == 0)            // Flip buffer story to get 0x40009000 (hi) buf to always smash with 0x7000 (dec: 28672)
            totalSize += 4096;
        // Double-check
        if (totalSize > 0x30298){
            print("File size of the payload is too big. Comparing to maximum size, it's greater to "+(totalSize - 0x30298)+" bytes!"+
                    "\n         1. Double-check that you're using the right payload." +
                    "\n         2. Please report this issue in case you're sure that you're doing everything right." +
                    "\n\n         Nothing has been sent to NS. Execution stopped.", EMsgType.FAIL); // Occurs: never. I'm too lazy to check.
            return true;
        }
        // Define holder of 'everything payload'
        fullPayload = new byte[totalSize];
        // Prepare array to store file payload.
        byte[] dataPldFile = new byte[pldFileSize];

        try{
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(pldrFile));
            int readSize;
            if ((readSize = bis.read(dataPldFile)) != pldFileSize){
                print("Failed to retrieve data from payload file." +
                        "\n         Got only "+readSize+" bytes while "+pldFileSize+" expected." +
                        "\n\n         Nothing has been sent to NS. Execution stopped.", EMsgType.FAIL);
                bis.close();
                return true;
            }
            bis.close();
        }
        catch (Exception e){
            print("Failed to retrieve data from payload file: " +e.getMessage()+
                    "\n\n         Nothing has been sent to NS. Execution stopped.", EMsgType.FAIL);
            return true;
        }
        // Trust me
        System.arraycopy(initSeq, 0, fullPayload, 0, 3);
        System.arraycopy(mezzo, 0, fullPayload, 680, 124);
        System.arraycopy(dataPldFile, 0, fullPayload, 4328, 16384);
        for (int i = 0; i < 2160; i++)
            System.arraycopy(sprayPttrn, 0, fullPayload, 20712+i*4, 4);
        System.arraycopy(dataPldFile, 16384, fullPayload, 29352, pldFileSize-16384);
        return false;
    }
    /**
     * Read device ID in the early beginning
     * @return false if NO issues 
     *          true if issues 
     * */
    private boolean readUsbDeviceID(){
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(16);
        IntBuffer readBufTransferred = IntBuffer.allocate(1);
        int result = LibUsb.bulkTransfer(handler, (byte) 0x81, readBuffer, readBufTransferred, 1000);
        if (result != LibUsb.SUCCESS) {
            print("Unable to get device ID" +
                    "\n\n         Nothing has been sent to NS. Execution stopped.", EMsgType.FAIL);
            return true;
        }
        int trans = readBufTransferred.get();
        byte[] receivedBytes = new byte[trans];
        readBuffer.get(receivedBytes);
        StringBuilder idStrBld = new StringBuilder("Found device with ID: ");
        for (byte b: receivedBytes)
            idStrBld.append(String.format("%02x ", b));
        print(idStrBld.toString(), EMsgType.PASS);
        return false;
    }
    /**
     * Sending byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private boolean writeUsb(byte[] message){
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(4096);
        writeBuffer.put(message);
        IntBuffer writeBufTransferred = IntBuffer.allocate(1);
        int result = LibUsb.bulkTransfer(handler, (byte) 0x01, writeBuffer, writeBufTransferred, 5050);

        if (result == LibUsb.SUCCESS) {
            if (writeBufTransferred.get() == 4096)
                return false;

            print("RCM Data transfer issue [write]" +
                    "\n         Requested: " + message.length +
                    "\n         Transferred: " + writeBufTransferred.get()+
                    "\n\n         Execution stopped.", EMsgType.FAIL);
            return true;
        }
        print("RCM Data transfer issue [write]" +
                "\n         Returned: " + LibUsb.errorName(result) +
                "\n\n         Execution stopped.", EMsgType.FAIL);
        return true;
    }
    /**
     * MacOS version of RcmSmash class
     * */
    boolean smashMacOS(){
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(28672);
        LibUsb.controlTransfer(handler, (byte) 0x82, LibUsb.REQUEST_GET_STATUS, (short) 0, (short) 0, writeBuffer, 1000); // Result doesn't matter.
        return false;
    }
}