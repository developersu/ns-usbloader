package nsusbloader.COM.USB;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.COM.Helpers.NSSplitReader;
import nsusbloader.COM.USB.PFS.PFSProvider;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * GoldLeaf processing
 * */
public class GoldLeaf_05 extends TransferModule {
    //                            CMD                                G     L     U     C
    private static final byte[] CMD_GLUC =               new byte[]{0x47, 0x4c, 0x55, 0x43};
    private static final byte[] CMD_ConnectionRequest =  new byte[]{0x00, 0x00, 0x00, 0x00};    // Write-only command
    private static final byte[] CMD_NSPName =            new byte[]{0x02, 0x00, 0x00, 0x00};    // Write-only command
    private static final byte[] CMD_NSPData =            new byte[]{0x04, 0x00, 0x00, 0x00};    // Write-only command

    private static final byte[] CMD_ConnectionResponse = new byte[]{0x01, 0x00, 0x00, 0x00};
    private static final byte[] CMD_Start =              new byte[]{0x03, 0x00, 0x00, 0x00};
    private static final byte[] CMD_NSPContent =         new byte[]{0x05, 0x00, 0x00, 0x00};
    private static final byte[] CMD_NSPTicket =          new byte[]{0x06, 0x00, 0x00, 0x00};
    private static final byte[] CMD_Finish =             new byte[]{0x07, 0x00, 0x00, 0x00};

    private RandomAccessFile raf;   // NSP File
    private NSSplitReader nsr;      // It'a also NSP File

    GoldLeaf_05(DeviceHandle handler, LinkedHashMap<String, File> nspMap, Task<Void> task, LogPrinter logPrinter){
        super(handler, nspMap, task, logPrinter);
        status = EFileStatus.FAILED;

        logPrinter.print("============= GoldLeaf v0.5 =============\n" +
            "        Only one file per time could be sent. In case you selected more the first one would be picked.", EMsgType.INFO);
        if (nspMap.isEmpty()){
            logPrinter.print("For using this GoldLeaf version you have to add file to the table and select it for upload", EMsgType.INFO);
            return;
        }
        File nspFile = (File) nspMap.values().toArray()[0];
        logPrinter.print("File for upload: "+nspFile.getAbsolutePath(), EMsgType.INFO);

        if (!nspFile.getName().toLowerCase().endsWith(".nsp")) {
            logPrinter.print("GL This file doesn't look like NSP", EMsgType.FAIL);
            return;
        }
        PFSProvider pfsElement;
        try{
            pfsElement = new PFSProvider(nspFile, logPrinter);
        }
        catch (Exception e){
            logPrinter.print("GL File provided has incorrect structure and won't be uploaded\n\t"+e.getMessage(), EMsgType.FAIL);
            status = EFileStatus.INCORRECT_FILE_FAILED;
            return;
        }
        logPrinter.print("GL File structure validated and it will be uploaded", EMsgType.PASS);

        try{
            if (nspFile.isDirectory())
                this.nsr = new NSSplitReader(nspFile, 0);
            else
                this.raf = new RandomAccessFile(nspFile, "r");
        }
        catch (IOException ioe){
            logPrinter.print("GL File not found\n\t"+ioe.getMessage(), EMsgType.FAIL);
            return;
        }

        // Go parse commands
        byte[] readByte;

        // Go connect to GoldLeaf
        if (writeUsb(CMD_GLUC)) {
            logPrinter.print("GL Initiating GoldLeaf connection [1/2]", EMsgType.FAIL);
            return;
        }
        logPrinter.print("GL Initiating GoldLeaf connection: [1/2]", EMsgType.PASS);
        if (writeUsb(CMD_ConnectionRequest)){
            logPrinter.print("GL Initiating GoldLeaf connection: [2/2]", EMsgType.FAIL);
            return;
        }
        logPrinter.print("GL Initiating GoldLeaf connection: [2/2]", EMsgType.PASS);

        while (true) {
            readByte = readUsb();
            if (readByte == null)
                return;

            if (Arrays.equals(readByte, CMD_GLUC)) {
                if ((readByte = readUsb()) == null)
                    return;

                if (Arrays.equals(readByte, CMD_ConnectionResponse)) {
                    if (handleConnectionResponse(pfsElement))
                        return;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_Start)) {
                    if (handleStart(pfsElement))
                        return;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_NSPContent)) {
                    if (handleNSPContent(pfsElement, true))
                        return;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_NSPTicket)) {
                    if (handleNSPContent(pfsElement, false))
                        return;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_Finish)) {
                    logPrinter.print("GL Closing GoldLeaf connection: Transfer successful.", EMsgType.PASS);
                    status = EFileStatus.UPLOADED;
                    break;
                }
            }
        }
        try {
            raf.close();
        }
        catch (IOException | NullPointerException ignored){}
        try {
            nsr.close();
        }
        catch (IOException | NullPointerException ignored){}
    }
    /**
     * ConnectionResponse command handler
     * @return true if failed
     *         false if no issues
     * */
    private boolean handleConnectionResponse(PFSProvider pfsElement){
        logPrinter.print("GL 'ConnectionResponse' command:", EMsgType.INFO);
        if (writeUsb(CMD_GLUC)) {
            logPrinter.print("  [1/4]", EMsgType.FAIL);
            return true;
        }
        logPrinter.print("  [1/4]", EMsgType.PASS);
        if (writeUsb(CMD_NSPName)) {
            logPrinter.print("  [2/4]", EMsgType.FAIL);
            return true;
        }
        logPrinter.print("  [2/4]", EMsgType.PASS);

        if (writeUsb(pfsElement.getBytesNspFileNameLength())) {
            logPrinter.print("  [3/4]", EMsgType.FAIL);
            return true;
        }
        logPrinter.print("  [3/4]", EMsgType.PASS);

        if (writeUsb(pfsElement.getBytesNspFileName())) {
            logPrinter.print("  [4/4]", EMsgType.FAIL);
            return true;
        }
        logPrinter.print("  [4/4]", EMsgType.PASS);

        return false;
    }
    /**
     * Start command handler
     * @return true if failed
     *         false if no issues
     * */
    private boolean handleStart(PFSProvider pfsElement){
        logPrinter.print("GL Handle 'Start' command:", EMsgType.INFO);
        if (writeUsb(CMD_GLUC)) {
            logPrinter.print("  [Prefix]", EMsgType.FAIL);
            return true;
        }
        logPrinter.print("  [Prefix]", EMsgType.PASS);

        if (writeUsb(CMD_NSPData)) {
            logPrinter.print("  [Command]", EMsgType.FAIL);
            return true;
        }
        logPrinter.print("  [Command]", EMsgType.PASS);

        if (writeUsb(pfsElement.getBytesCountOfNca())) {
            logPrinter.print("  [Sub-files count]", EMsgType.FAIL);
            return true;
        }
        logPrinter.print("  [Sub-files count]", EMsgType.PASS);

        int ncaCount = pfsElement.getIntCountOfNca();
        logPrinter.print("  [Information for "+ncaCount+" sub-files]", EMsgType.INFO);
        for (int i = 0; i < ncaCount; i++){
            logPrinter.print("File #"+i, EMsgType.INFO);
            if (writeUsb(pfsElement.getNca(i).getNcaFileNameLength())) {
                logPrinter.print("  [1/4] Name length", EMsgType.FAIL);
                return true;
            }
            logPrinter.print("  [1/4] Name length", EMsgType.PASS);

            if (writeUsb(pfsElement.getNca(i).getNcaFileName())) {
                logPrinter.print("  [2/4] Name", EMsgType.FAIL);
                return true;
            }
            logPrinter.print("  [2/4] Name", EMsgType.PASS);
            if (writeUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getBodySize()+pfsElement.getNca(i).getNcaOffset()).array())) {   // offset. real.
                logPrinter.print("  [3/4] Offset", EMsgType.FAIL);
                return true;
            }
            logPrinter.print("  [3/4] Offset", EMsgType.PASS);
            if (writeUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getNca(i).getNcaSize()).array())) {  // size
                logPrinter.print("  [4/4] Size", EMsgType.FAIL);
                return true;
            }
            logPrinter.print("  [4/4] Size", EMsgType.PASS);
        }
        return false;
    }
    /**
     * NSPContent command handler
     * @param isItRawRequest true: just ask NS what's needed
     *                       false: send ticket
     * @return true if failed
     *         false if no issues
     * */
    private boolean handleNSPContent(PFSProvider pfsElement, boolean isItRawRequest){
        int requestedNcaID;

        if (isItRawRequest) {
            logPrinter.print("GL Handle 'Content' command", EMsgType.INFO);
            byte[] readByte = readUsb();
            if (readByte == null || readByte.length != 4) {
                logPrinter.print("  [Read requested ID]", EMsgType.FAIL);
                return true;
            }
            requestedNcaID = ByteBuffer.wrap(readByte).order(ByteOrder.LITTLE_ENDIAN).getInt();
            logPrinter.print("  [Read requested ID = "+requestedNcaID+" ]", EMsgType.PASS);
        }
        else {
            requestedNcaID = pfsElement.getNcaTicketID();
            logPrinter.print("GL Handle 'Ticket' command (ID = "+requestedNcaID+" )", EMsgType.INFO);
        }

        long realNcaOffset = pfsElement.getNca(requestedNcaID).getNcaOffset()+pfsElement.getBodySize();
        long realNcaSize = pfsElement.getNca(requestedNcaID).getNcaSize();

        long readFrom = 0;

        int readPice = 8388608; // 8mb
        byte[] readBuf;

        try{
            if (raf == null){
                nsr.seek(realNcaOffset);

                while (readFrom < realNcaSize){
                    if (realNcaSize - readFrom < readPice)
                        readPice = Math.toIntExact(realNcaSize - readFrom);    // it's safe, I guarantee
                    readBuf = new byte[readPice];
                    if (nsr.read(readBuf) != readPice)
                        return true;
                    //System.out.println("S: "+readFrom+" T: "+realNcaSize+" P: "+readPice);    //  DEBUG
                    if (writeUsb(readBuf))
                        return true;
                    //-----------------------------------------/
                    logPrinter.updateProgress((readFrom+readPice)/(realNcaSize/100.0) / 100.0);
                    //-----------------------------------------/
                    readFrom += readPice;
                }
            }
            else {
                raf.seek(realNcaOffset);

                while (readFrom < realNcaSize){
                    if (realNcaSize - readFrom < readPice)
                        readPice = Math.toIntExact(realNcaSize - readFrom);    // it's safe, I guarantee
                    readBuf = new byte[readPice];
                    if (raf.read(readBuf) != readPice)
                        return true;
                    //System.out.println("S: "+readFrom+" T: "+realNcaSize+" P: "+readPice);    //  DEBUG
                    if (writeUsb(readBuf))
                        return true;
                    //-----------------------------------------/
                    logPrinter.updateProgress((readFrom+readPice)/(realNcaSize/100.0) / 100.0);
                    //-----------------------------------------/
                    readFrom += readPice;
                }
            }
            //-----------------------------------------/
            logPrinter.updateProgress(1.0);
            //-----------------------------------------/
        }
        catch (IOException ioe){
            logPrinter.print("GL Failed to read NCA ID "+requestedNcaID+". IO Exception:\n  "+ioe.getMessage(), EMsgType.FAIL);
            ioe.printStackTrace();
            return true;
        }
        return false;
    }


    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private boolean writeUsb(byte[] message){
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(message.length);   //writeBuffer.order() equals BIG_ENDIAN;
        writeBuffer.put(message);                                             // Don't do writeBuffer.rewind();
        IntBuffer writeBufTransferred = IntBuffer.allocate(1);
        int result;

        while (! task.isCancelled()) {
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x01, writeBuffer, writeBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint OUT = 0x01

            switch (result){
                case LibUsb.SUCCESS:
                    if (writeBufTransferred.get() == message.length)
                        return false;
                    else {
                        logPrinter.print("GL Data transfer issue [write]\n  Requested: "+message.length+"\n  Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                        return true;
                    }
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    logPrinter.print("GL Data transfer issue [write]\n  Returned: "+ UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    logPrinter.print("GL Execution stopped", EMsgType.FAIL);
                    return true;
            }
        }
        logPrinter.print("GL Execution interrupted", EMsgType.INFO);
        return true;
    }
    /**
     * Reading what USB device responded.
     * @return byte array if data read successful
     *         'null' if read failed
     * */
    private byte[] readUsb(){
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(512);
        // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
        IntBuffer readBufTransferred = IntBuffer.allocate(1);

        int result;
        while (! task.isCancelled()) {
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    int trans = readBufTransferred.get();
                    byte[] receivedBytes = new byte[trans];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    logPrinter.print("GL Data transfer issue [read]\n  Returned: " + UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    logPrinter.print("GL Execution stopped", EMsgType.FAIL);
                    return null;
            }
        }
        logPrinter.print("GL Execution interrupted", EMsgType.INFO);
        return null;
    }
}