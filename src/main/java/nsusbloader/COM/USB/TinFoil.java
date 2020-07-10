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
package nsusbloader.COM.USB;

import nsusbloader.ModelControllers.ILogPrinter;
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
    // "TUL0".getBytes(StandardCharsets.US_ASCII)
    private static final byte[] TUL0  = new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x4c, (byte) 0x30};
    private static final byte[] MAGIC = new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30};  // aka 'TUC0' ASCII

    private static final byte CMD_EXIT = 0x00;
    private static final byte CMD_FILE_RANGE_DEFAULT = 0x01;
    private static final byte CMD_FILE_RANGE_ALTERNATIVE = 0x02;
    /*  byte[] magic = new byte[4];
        ByteBuffer bb = StandardCharsets.UTF_8.encode("TUC0").rewind().get(magic); // Let's rephrase this 'string' */

    TinFoil(DeviceHandle handler, LinkedHashMap<String, File> nspMap, Runnable task, ILogPrinter logPrinter){
        super(handler, nspMap, task, logPrinter);
        logPrinter.print("============= Tinfoil =============", EMsgType.INFO);

        if (! sendListOfFiles())
            return;

        if (proceedCommands())                              // REPORT SUCCESS
            status = EFileStatus.UPLOADED;     // Don't change status that is already set to FAILED
    }
    /**
     * Send what NSP will be transferred
     * */
    private boolean sendListOfFiles(){
        final String fileNamesListToSend = getFileNamesToSend();

        byte[] nspListNames = getFileNamesToSendAsBytes(fileNamesListToSend);
        byte[] nspListNamesSize = getFileNamesLengthToSendAsBytes(nspListNames);
        byte[] padding = new byte[8];

        if (writeUsb(TUL0)) {
            logPrinter.print("TF Send list of files: handshake   [1/4]", EMsgType.FAIL);
            return false;
        }

        if (writeUsb(nspListNamesSize)) {                                 // size of the list we can transfer
            logPrinter.print("TF Send list of files: list length [2/4]", EMsgType.FAIL);
            return false;
        }

        if (writeUsb(padding)) {
            logPrinter.print("TF Send list of files: padding     [3/4]", EMsgType.FAIL);
            return false;
        }

        if (writeUsb(nspListNames)) {
            logPrinter.print("TF Send list of files: list itself [4/4]", EMsgType.FAIL);
            return false;
        }
        logPrinter.print("TF Send list of files complete.", EMsgType.PASS);

        return true;
    }

    private String getFileNamesToSend(){
        StringBuilder fileNamesListBuilder = new StringBuilder();
        for(String nspFileName: nspMap.keySet()) {
            fileNamesListBuilder.append(nspFileName);   // And here we come with java string default encoding (UTF-16)
            fileNamesListBuilder.append('\n');
        }
        return fileNamesListBuilder.toString();
    }

    private byte[] getFileNamesToSendAsBytes(String fileNamesListToSend){
        return fileNamesListToSend.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getFileNamesLengthToSendAsBytes(byte[] fileNamesListToSendAsBytes){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);         // integer = 4 bytes; BTW Java is stored in big-endian format
        byteBuffer.putInt(fileNamesListToSendAsBytes.length);                                                             // This way we obtain length in int converted to byte array in correct Big-endian order. Trust me.
        return byteBuffer.array();
    }
    /**
     * After we sent commands to NS, this chain starts
     * */
    private boolean proceedCommands(){
        logPrinter.print("TF Awaiting for NS commands.", EMsgType.INFO);
        try{
            byte[] deviceReply;
            byte command;

            while (true){
                deviceReply = readUsb();
                if (! isReplyValid(deviceReply))
                    continue;
                command = getCommandFromReply(deviceReply);

                switch (command){
                    case CMD_EXIT:
                        logPrinter.print("TF Transfer complete.", EMsgType.PASS);
                        return true;
                    case CMD_FILE_RANGE_DEFAULT:
                    case CMD_FILE_RANGE_ALTERNATIVE:
                        //logPrinter.print("TF Received 'FILE RANGE' command [0x0"+command+"].", EMsgType.PASS);
                        if (fileRangeCmd())
                            return false;      // catches exception
                }
            }
        }
        catch (Exception e){
            logPrinter.print(e.getMessage(), EMsgType.INFO);
            return false;
        }
    }

    private boolean isReplyValid(byte[] reply){
        return Arrays.equals(Arrays.copyOfRange(reply, 0,4), MAGIC);
    }

    private byte getCommandFromReply(byte[] reply){
        return reply[8];
    }
    /**
     * This is what returns requested file (files)
     * Executes multiple times
     * @return 'false' if everything is ok
     *          'true' is error/exception occurs
     * */
    private boolean fileRangeCmd(){
        try {
            byte[] receivedArray = readUsb();

            byte[] sizeAsBytes = Arrays.copyOfRange(receivedArray, 0,8);
            long size = ByteBuffer.wrap(sizeAsBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();          // could be unsigned long. This app won't support files greater then 8796093022208 Gb
            long offset = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 8,16)).order(ByteOrder.LITTLE_ENDIAN).getLong();      // could be unsigned long. This app doesn't support files greater then 8796093022208 Gb

            // Requesting UTF-8 file name required:
            receivedArray = readUsb();

            String nspFileName = new String(receivedArray, StandardCharsets.UTF_8);

            logPrinter.print(String.format("TF Reply to: %s" +
                    "\n         Offset: %-20d 0x%x" +
                    "\n         Size:   %-20d 0x%x",
                    nspFileName,
                    offset, offset,
                    size, size), EMsgType.INFO);

            File nspFile = nspMap.get(nspFileName);
            boolean isSplitFile = nspFile.isDirectory();

            // Sending response 'header'
            if (sendMetaInfoForFile(sizeAsBytes))   // Get size in 'RAW' format exactly as it has been received to simplify the process.
                return true;

            if (isSplitFile)
                sendSplitFile(nspFile, size, offset);
            else
                sendNormalFile(nspFile, size, offset);
        } catch (IOException ioe){
            logPrinter.print("TF IOException:\n         "+ioe.getMessage(), EMsgType.FAIL);
            ioe.printStackTrace();
            return true;
        } catch (ArithmeticException ae){
            logPrinter.print("TF ArithmeticException (can't cast 'offset end' - 'offsets current' to 'integer'):" +
                    "\n         "+ae.getMessage(), EMsgType.FAIL);
            ae.printStackTrace();
            return true;
        } catch (NullPointerException npe){
            logPrinter.print("TF NullPointerException (in some moment application didn't find something. Something important.):" +
                    "\n         "+npe.getMessage(), EMsgType.FAIL);
            npe.printStackTrace();
            return true;
        }
        catch (Exception defe){
            logPrinter.print(defe.getMessage(), EMsgType.FAIL);
            return true;
        }
        return false;
    }

    void sendSplitFile(File nspFile, long size, long offset) throws IOException, NullPointerException, ArithmeticException {
        byte[] readBuffer;
        long currentOffset = 0;
        int chunk = 8388608; // = 8Mb;

        NSSplitReader nsSplitReader = new NSSplitReader(nspFile, size);
        if (nsSplitReader.seek(offset) != offset)
            throw new IOException("TF Requested offset is out of file size. Nothing to transmit.");

        while (currentOffset < size){
            if ((currentOffset + chunk) >= size )
                chunk = Math.toIntExact(size - currentOffset);
            //System.out.println("CO: "+currentOffset+"\t\tEO: "+size+"\t\tRP: "+chunk);  // NOTE: DEBUG
            logPrinter.updateProgress((currentOffset + chunk) / (size / 100.0) / 100.0);

            readBuffer = new byte[chunk];     // TODO: not perfect moment, consider refactoring.

            if (nsSplitReader.read(readBuffer) != chunk)
                throw new IOException("TF Reading from stream suddenly ended.");

            if (writeUsb(readBuffer))
                throw new IOException("TF Failure during file transfer.");
            currentOffset += chunk;
        }
        nsSplitReader.close();
        logPrinter.updateProgress(1.0);
    }

    void sendNormalFile(File nspFile, long size, long offset) throws IOException, NullPointerException, ArithmeticException {
        byte[] readBuffer;
        long currentOffset = 0;
        int chunk = 8388608;

        BufferedInputStream bufferedInStream = new BufferedInputStream(new FileInputStream(nspFile));

        if (bufferedInStream.skip(offset) != offset)
            throw new IOException("TF Requested offset is out of file size. Nothing to transmit.");

        while (currentOffset < size) {
            if ((currentOffset + chunk) >= size)
                chunk = Math.toIntExact(size - currentOffset);
            //System.out.println("CO: "+currentOffset+"\t\tEO: "+receivedRangeSize+"\t\tRP: "+chunk);  // NOTE: DEBUG
            logPrinter.updateProgress((currentOffset + chunk) / (size / 100.0) / 100.0);

            readBuffer = new byte[chunk];

            if (bufferedInStream.read(readBuffer) != chunk)
                throw new IOException("TF Reading from stream suddenly ended.");

            if (writeUsb(readBuffer))
                throw new IOException("TF Failure during file transfer.");
            currentOffset += chunk;
        }
        bufferedInStream.close();
        logPrinter.updateProgress(1.0);
    }
    /**
     * Send response header.
     * @return false if everything OK
     *         true if failed
     * */
    private boolean sendMetaInfoForFile(byte[] sizeAsBytes){
        final byte[] standardReplyBytes = new byte[] { 0x54, 0x55, 0x43, 0x30,    // 'TUC0'
                                                       0x01, 0x00, 0x00, 0x00,    // CMD_TYPE_RESPONSE = 1
                                                       0x01, 0x00, 0x00, 0x00 };

        final byte[] twelveZeroBytes = new byte[12];

        if (writeUsb(standardReplyBytes)){       // Send integer value of '1' in Little-endian format.
            logPrinter.print("TF Sending response failed [1/3]", EMsgType.FAIL);
            return true;
        }

        if(writeUsb(sizeAsBytes)) {                                                          // Send EXACTLY what has been received
            logPrinter.print("TF Sending response failed [2/3]", EMsgType.FAIL);
            return true;
        }

        if(writeUsb(twelveZeroBytes)) {                                                       // kinda another one padding
            logPrinter.print("TF Sending response failed [3/3]", EMsgType.FAIL);
            return true;
        }
        return false;
    }

    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private boolean writeUsb(byte[] message) {
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(message.length);   //writeBuffer.order() equals BIG_ENDIAN;
        writeBuffer.put(message);                                             // Don't do writeBuffer.rewind();
        IntBuffer writeBufTransferred = IntBuffer.allocate(1);
        int result;
        //int varVar = 0; //todo:remove
        while (! Thread.interrupted() ) {
            /*
            if (varVar != 0)
                logPrinter.print("writeUsb() retry cnt: "+varVar, EMsgType.INFO); //NOTE: DEBUG
            varVar++;
            */
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x01, writeBuffer, writeBufTransferred, 5050);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint OUT = 0x01

            switch (result){
                case LibUsb.SUCCESS:
                    if (writeBufTransferred.get() == message.length)
                        return false;
                    logPrinter.print("TF Data transfer issue [write]" +
                            "\n         Requested: "+message.length+
                            "\n         Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                    return true;
                case LibUsb.ERROR_TIMEOUT:
                    //System.out.println("writeBuffer position: "+writeBuffer.position()+" "+writeBufTransferred.get());
                    //writeBufTransferred.clear();    // MUST BE HERE IF WE 'GET()' IT
                    continue;
                default:
                    logPrinter.print("TF Data transfer issue [write]" +
                            "\n         Returned: "+ UsbErrorCodes.getErrCode(result) +
                            "\n         (execution stopped)", EMsgType.FAIL);
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
    private byte[] readUsb() throws Exception{
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(512);
        // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
        IntBuffer readBufTransferred = IntBuffer.allocate(1);
        int result;
        while (! Thread.interrupted()) {
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
                    throw new Exception("TF Data transfer issue [read]" +
                            "\n         Returned: " + UsbErrorCodes.getErrCode(result)+
                            "\n         (execution stopped)");
            }
        }
        throw new InterruptedException("TF Execution interrupted");
    }
}
