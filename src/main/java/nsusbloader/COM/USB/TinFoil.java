package nsusbloader.COM.USB;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.COM.Helpers.NSSplitReader;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Tinfoil processing
 * */
class TinFoil extends TransferModule {
    TinFoil(DeviceHandle handler, LinkedHashMap<String, File> nspMap, Task<Void> task, LogPrinter logPrinter){
        super(handler, nspMap, task, logPrinter);

        logPrinter.print("============= TinFoil =============", EMsgType.INFO);

        if (!sendListOfNSP())
            return;

        if (proceedCommands())                              // REPORT SUCCESS
            status = EFileStatus.UPLOADED;     // Don't change status that is already set to FAILED
    }
    /**
     * Send what NSP will be transferred
     * */
    private boolean sendListOfNSP(){
        // Send list of NSP files:
        // Proceed "TUL0"
        if (writeUsb("TUL0".getBytes(StandardCharsets.US_ASCII))) {  // new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x76, (byte) 0x30}
            logPrinter.print("TF Send list of files: handshake", EMsgType.FAIL);
            return false;
        }
        else
            logPrinter.print("TF Send list of files: handshake", EMsgType.PASS);
        //Collect file names
        StringBuilder nspListNamesBuilder = new StringBuilder();    // Add every title to one stringBuilder
        for(String nspFileName: nspMap.keySet()) {
            nspListNamesBuilder.append(nspFileName);   // And here we come with java string default encoding (UTF-16)
            nspListNamesBuilder.append('\n');
        }

        byte[] nspListNames = nspListNamesBuilder.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);         // integer = 4 bytes; BTW Java is stored in big-endian format
        byteBuffer.putInt(nspListNames.length);                                                             // This way we obtain length in int converted to byte array in correct Big-endian order. Trust me.
        byte[] nspListSize = byteBuffer.array();                                                            // TODO: rewind? not sure..
        //byteBuffer.reset();

        // Sending NSP list
        logPrinter.print("TF Send list of files", EMsgType.INFO);
        if (writeUsb(nspListSize)) {                                           // size of the list we're going to transfer goes...
            logPrinter.print("  [send list length]", EMsgType.FAIL);
            return false;
        }
        logPrinter.print("  [send list length]", EMsgType.PASS);

        if (writeUsb(new byte[8])) {                                           // 8 zero bytes goes...
            logPrinter.print("  [send padding]", EMsgType.FAIL);
            return false;
        }
        logPrinter.print("  [send padding]", EMsgType.PASS);

        if (writeUsb(nspListNames)) {                                           // list of the names goes...
            logPrinter.print("  [send list itself]", EMsgType.FAIL);
            return false;
        }
        logPrinter.print("  [send list itself]", EMsgType.PASS);

        return true;
    }
    /**
     * After we sent commands to NS, this chain starts
     * */
    private boolean proceedCommands(){
        logPrinter.print("TF Awaiting for NS commands.", EMsgType.INFO);

        /*  byte[] magic = new byte[4];
            ByteBuffer bb = StandardCharsets.UTF_8.encode("TUC0").rewind().get(magic); // Let's rephrase this 'string'
        */
        final byte[] magic = new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30};  // eq. 'TUC0' @ UTF-8 (actually ASCII lol, u know what I mean)

        byte[] receivedArray;

        while (true){   // Check if user interrupted process.

            receivedArray = readUsb();

            if (receivedArray == null)      // catches error
                return false;

            if (!Arrays.equals(Arrays.copyOfRange(receivedArray, 0,4), magic))      // Bytes from 0 to 3 should contain 'magic' TUC0, so must be verified like this
                continue;

            // 8th to 12th(explicits) bytes in returned data stands for command ID as unsigned integer (Little-endian). Actually, we have to compare arrays here, but in real world it can't be greater then 0/1/2, thus:
            // BTW also protocol specifies 4th byte to be 0x00 kinda indicating that that this command is valid. But, as you may see, never happens other situation when it's not = 0.
            if (receivedArray[8] == 0x00){                           //0x00 - exit
                logPrinter.print("TF Received 'EXIT' command. Terminating.", EMsgType.PASS);
                return true;                     // All interaction with USB device should be ended (expected);
            }
            else if ((receivedArray[8] == 0x01) || (receivedArray[8] == 0x02)){           //0x01 - file range; 0x02 unknown bug on backend side (dirty hack).
                logPrinter.print("TF Received 'FILE RANGE' command. Proceeding: [0x0"+receivedArray[8]+"]", EMsgType.PASS);
                /*// We can get in this pocket a length of file name (+32). Why +32? I dunno man.. Do we need this? Definitely not. This app can live without it.
                long receivedSize = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 12,20)).order(ByteOrder.LITTLE_ENDIAN).getLong();
                logsArea.appendText("[V] Received FILE_RANGE command. Size: "+Long.toUnsignedString(receivedSize)+"\n");            // this shit returns string that will be chosen next '+32'. And, BTW, can't be greater then 512
                */
                if (! fileRangeCmd())
                    return false;      // catches exception
            }
        }
    }
    /**
     * This is what returns requested file (files)
     * Executes multiple times
     * @return 'true' if everything is ok
     *          'false' is error/exception occurs
     * */
    private boolean fileRangeCmd(){
        byte[] receivedArray;
        // Here we take information of what other side wants
        receivedArray = readUsb();
        if (receivedArray == null)
            return false;

        // range_offset of the requested file. In the begining it will be 0x10.
        long receivedRangeSize = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 0,8)).order(ByteOrder.LITTLE_ENDIAN).getLong();          // Note - it could be unsigned long. Unfortunately, this app won't support files greater then 8796093022208 Gb
        byte[] receivedRangeSizeRAW = Arrays.copyOfRange(receivedArray, 0,8);                                                               // used (only) when we use sendResponse(). It's just simply.
        long receivedRangeOffset = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 8,16)).order(ByteOrder.LITTLE_ENDIAN).getLong();      // Note - it could be unsigned long. Unfortunately, this app won't support files greater then 8796093022208 Gb
            /* Below, it's REAL NSP file name length that we sent before among others (WITHOUT +32 byes). It can't be greater then... see what is written in the beginning of this code.
            We don't need this since in next pocket we'll get name itself UTF-8 encoded. Could be used to double-checks or something like that.
            long receivedNspNameLen = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 16,24)).order(ByteOrder.LITTLE_ENDIAN).getLong(); */

        // Requesting UTF-8 file name required:
        receivedArray = readUsb();
        if (receivedArray == null)
            return false;

        String receivedRequestedNSP = new String(receivedArray, StandardCharsets.UTF_8);
        logPrinter.print("TF Reply to requested file: "+receivedRequestedNSP
                +"\n         Range Size:   "+receivedRangeSize
                +"\n         Range Offset: "+receivedRangeOffset, EMsgType.INFO);

        // Sending response header
        if (!sendResponse(receivedRangeSizeRAW))   // Get receivedRangeSize in 'RAW' format exactly as it has been received. It's simply.
            return false;

        try {
            byte[] bufferCurrent;   //= new byte[1048576];        // eq. Allocate 1mb

            long currentOffset = 0;
            // 'End Offset' equal to receivedRangeSize.
            int readPice = 8388608;                     // = 8Mb

            //---------------! Split files start !---------------
            if (nspMap.get(receivedRequestedNSP).isDirectory()){
                NSSplitReader nsSplitReader = new NSSplitReader(nspMap.get(receivedRequestedNSP), receivedRangeSize);
                if (nsSplitReader.seek(receivedRangeOffset) != receivedRangeOffset){
                    logPrinter.print("TF Requested skip is out of file size. Nothing to transmit.", EMsgType.FAIL);
                    return false;
                }

                while (currentOffset < receivedRangeSize){
                    if ((currentOffset + readPice) >= receivedRangeSize )
                        readPice = Math.toIntExact(receivedRangeSize - currentOffset);
                    //System.out.println("CO: "+currentOffset+"\t\tEO: "+receivedRangeSize+"\t\tRP: "+readPice);  // NOTE: DEBUG
                    // updating progress bar (if a lot of data requested) START BLOCK
                    //---Tell progress to UI---/
                    logPrinter.updateProgress((currentOffset+readPice)/(receivedRangeSize/100.0) / 100.0);
                    //------------------------/
                    bufferCurrent = new byte[readPice];                                                         // TODO: not perfect moment, consider refactoring.

                    if (nsSplitReader.read(bufferCurrent) != readPice) {                                      // changed since @ v0.3.2
                        logPrinter.print("TF Reading of stream suddenly ended.", EMsgType.WARNING);
                        return false;
                    }
                    //write to USB
                    if (writeUsb(bufferCurrent)) {
                        logPrinter.print("TF Failure during NSP transmission.", EMsgType.FAIL);
                        return false;
                    }
                    currentOffset += readPice;
                }
                nsSplitReader.close();
                //---Tell progress to UI---/
                logPrinter.updateProgress(1.0);
                //------------------------/
            }
            //---------------! Split files end     !---------------
            //---------------! Regular files start !---------------
            else {
                BufferedInputStream bufferedInStream = new BufferedInputStream(new FileInputStream(nspMap.get(receivedRequestedNSP)));      // TODO: refactor?

                if (bufferedInStream.skip(receivedRangeOffset) != receivedRangeOffset) {
                    logPrinter.print("TF Requested skip is out of file size. Nothing to transmit.", EMsgType.FAIL);
                    return false;
                }

                while (currentOffset < receivedRangeSize) {
                    if ((currentOffset + readPice) >= receivedRangeSize)
                        readPice = Math.toIntExact(receivedRangeSize - currentOffset);
                    //System.out.println("CO: "+currentOffset+"\t\tEO: "+receivedRangeSize+"\t\tRP: "+readPice);  // NOTE: DEBUG
                    // updating progress bar (if a lot of data requested) START BLOCK
                    //---Tell progress to UI---/
                    logPrinter.updateProgress((currentOffset + readPice) / (receivedRangeSize / 100.0) / 100.0);
                    //------------------------/
                    bufferCurrent = new byte[readPice];                                                         // TODO: not perfect moment, consider refactoring.

                    if (bufferedInStream.read(bufferCurrent) != readPice) {                                      // changed since @ v0.3.2
                        logPrinter.print("TF Reading of stream suddenly ended.", EMsgType.WARNING);
                        return false;
                    }
                    //write to USB
                    if (writeUsb(bufferCurrent)) {
                        logPrinter.print("TF Failure during NSP transmission.", EMsgType.FAIL);
                        return false;
                    }
                    currentOffset += readPice;
                }
                bufferedInStream.close();
                //---Tell progress to UI---/
                logPrinter.updateProgress(1.0);
                //------------------------/
            }
            //---------------! Regular files end     !---------------
        } catch (FileNotFoundException fnfe){
            logPrinter.print("TF FileNotFoundException:\n  "+fnfe.getMessage(), EMsgType.FAIL);
            fnfe.printStackTrace();
            return false;
        } catch (IOException ioe){
            logPrinter.print("TF IOException:\n  "+ioe.getMessage(), EMsgType.FAIL);
            ioe.printStackTrace();
            return false;
        } catch (ArithmeticException ae){
            logPrinter.print("TF ArithmeticException (can't cast 'offset end' - 'offsets current' to 'integer'):\n  "+ae.getMessage(), EMsgType.FAIL);
            ae.printStackTrace();
            return false;
        } catch (NullPointerException npe){
            logPrinter.print("TF NullPointerException (in some moment application didn't find something. Something important.):\n  "+npe.getMessage(), EMsgType.FAIL);
            npe.printStackTrace();
            return false;
        }

        return true;
    }
    /**
     * Send response header.
     * @return true if everything OK
     *         false if failed
     * */
    private boolean sendResponse(byte[] rangeSize){                                 // This method as separate function itself for application needed as a cookie in the middle of desert.
        logPrinter.print("TF Sending response", EMsgType.INFO);
        if (writeUsb(new byte[] { (byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30,    // 'TUC0'
                (byte) 0x01,                                                // CMD_TYPE_RESPONSE = 1
                (byte) 0x00, (byte) 0x00, (byte) 0x00,                      // kinda padding. Guys, didn't you want to use integer value for CMD semantic?
                (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00} )       // Send integer value of '1' in Little-endian format.
        ){
            logPrinter.print("  [1/3]", EMsgType.FAIL);
            return false;
        }
        logPrinter.print("  [1/3]", EMsgType.PASS);
        if(writeUsb(rangeSize)) {                                                          // Send EXACTLY what has been received
            logPrinter.print("  [2/3]", EMsgType.FAIL);
            return false;
        }
        logPrinter.print("  [2/3]", EMsgType.PASS);
        if(writeUsb(new byte[12])) {                                                       // kinda another one padding
            logPrinter.print("  [3/3]", EMsgType.FAIL);
            return false;
        }
        logPrinter.print("  [3/3]", EMsgType.PASS);
        return true;
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
                        logPrinter.print("TF Data transfer issue [write]\n  Requested: "+message.length+"\n  Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                        return true;
                    }
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    logPrinter.print("TF Data transfer issue [write]\n  Returned: "+ UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    logPrinter.print("TF Execution stopped", EMsgType.FAIL);
                    return true;
            }
        }
        logPrinter.print("TF Execution interrupted", EMsgType.INFO);
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
                    logPrinter.print("TF Data transfer issue [read]\n  Returned: " + UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    logPrinter.print("TF Execution stopped", EMsgType.FAIL);
                    return null;
            }
        }
        logPrinter.print("TF Execution interrupted", EMsgType.INFO);
        return null;
    }
}
