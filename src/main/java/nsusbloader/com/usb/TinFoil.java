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
package nsusbloader.com.usb;

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.com.helpers.NSSplitReader;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nsusbloader.NSLDataTypes.EMsgType.*;
import static nsusbloader.com.DataConvertUtils.arrToLongLE;
import static nsusbloader.com.DataConvertUtils.intToArrLE;

/**
 * Awoo processing
 * */
class TinFoil extends TransferModule {
    private static final int CHUNK_SIZE = 0x800000;  // = 8Mb;
    private static final byte[] TUL0  = "TUL0".getBytes(UTF_8);
    private static final String MAGIC = "TUC0";

    private static final byte[] STANDARD_REPLY = new byte[] { 0x54, 0x55, 0x43, 0x30,    // 'TUC0'
                                                              0x01, 0x00, 0x00, 0x00,    // CMD_TYPE_RESPONSE = 1 (Int in LE-format)
                                                              0x01, 0x00, 0x00, 0x00 };
    private static final byte[] TWELVE_ZERO_BYTES = new byte[12];
    private static final byte[] PADDING = new byte[8];

    private static final byte CMD_EXIT = 0x00;
    private static final byte CMD_FILE_RANGE_DEFAULT = 0x01;
    private static final byte CMD_FILE_RANGE_ALTERNATIVE = 0x02;

    TinFoil(DeviceHandle handler,
            LinkedHashMap<String, File> nspMap,
            CancellableRunnable task,
            ILogPrinter logPrinter) {
        super(handler, nspMap, task, logPrinter);
        print("======== Awoo Installer and compatibles ========", INFO);

        workLoop();
    }
    private void workLoop(){
        try {
            sendListOfFiles();
            proceedCommands();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    /**
     * Send what NSP will be transferred
     * */
    private void sendListOfFiles() throws Exception {
        var fileNames = buildFileNamesToSend();
        var fileNamesSize = intToArrLE(fileNames.length);

        writeUsb(TUL0, "[1/4] Send list of files: handshake");
        writeUsb(fileNamesSize, "[2/4] Send list of files: list length"); // size of the list we can transfer
        writeUsb(PADDING, "[3/4] Send list of files: padding");
        writeUsb(fileNames, "[4/4] Send list of files: list itself");

        print("Send list of files complete.", PASS);
    }

    private byte[] buildFileNamesToSend() {
        var fileNamesListBuilder = new StringBuilder();
        nspMap.keySet().forEach(fileName -> fileNamesListBuilder
                .append(fileName)
                .append('\n'));
        return fileNamesListBuilder.toString().getBytes(UTF_8);
    }

    /**
     * After we sent commands to NS, this chain starts
     * */
    private void proceedCommands() {
        print("Awaiting for NS commands", INFO);
        try{
            while (true) {
                var deviceReply = readUsb();

                if (isInvalidReply(deviceReply))
                    continue;

                switch (deviceReply[8]){
                    case CMD_EXIT:
                        print("Transfer complete", PASS);
                        status = EFileStatus.UPLOADED;
                        return;
                    case CMD_FILE_RANGE_DEFAULT:
                    case CMD_FILE_RANGE_ALTERNATIVE:
                        fileRangeCmd();
                }
            }
        } catch (ArithmeticException ae){
            print("Unable to cast huge offsets to int ('offset end' - 'offsets current'):\n" +
                    "         "+ae.getMessage(), FAIL);
            ae.printStackTrace();
        } catch (NullPointerException npe){
            print("Something missed. Make sure you have enough space on medium!\n" +
                    "         "+npe.getMessage(), FAIL);
            npe.printStackTrace();
        }
        catch (Exception e){
            print(e.getMessage(), FAIL);
            e.printStackTrace();
        }
    }

    private boolean isInvalidReply(byte[] reply) {
        return ! MAGIC.equals(new String(reply, 0,4, UTF_8));
    }
    /**
     * This is what returns requested file (files)
     * Executes multiple times
     * */
    private void fileRangeCmd() throws Exception {
        byte[] readData = readUsb();

        var sizeAsBytes = Arrays.copyOfRange(readData, 0,8);
        var size = arrToLongLE(readData, 0);    // could be unsigned long. Files greater than 8796093022208 Gb r not supported
        var offset = arrToLongLE(readData, 8);  // could be unsigned long
        var fileName = new String(readUsb(), UTF_8); // Requesting UTF-8 file name required

        print(String.format("Reply to: %s" +
                "%n         Offset: %-20d 0x%x" +
                "%n         Size:   %-20d 0x%x",
                fileName,
                offset, offset,
                size, size), INFO);

        var file = nspMap.get(fileName);
        var isSplitFile = file.isDirectory();

        // Sending response 'header'
        sendFileMetadata(sizeAsBytes);   // Size in format as received

        if (isSplitFile)
            sendSplitFile(file, size, offset);
        else
            sendNormalFile(file, size, offset);
    }

    private void sendSplitFile(File file, long size, long offset) throws Exception {
        try (var inStream = new NSSplitReader(file, size)) {
            if (inStream.seek(offset) != offset)
                throw new IOException("Requested offset is out of file size. Nothing to transmit.");

            sendFile(size, inStream);
        }
        logPrinter.updateProgress(1.0);
    }

    private void sendNormalFile(File file, long size, long offset) throws Exception {
        try (var inStream = new BufferedInputStream(new FileInputStream(file))) {
            if (inStream.skip(offset) != offset)
                throw new IOException("Requested offset is out of file size. Nothing to transmit.");

            sendFile(size, inStream);
        }
        logPrinter.updateProgress(1.0);
    }

    private void sendFile(long size, InputStream inStream) throws Exception {
        var currentOffset = 0L;
        var chunk = CHUNK_SIZE;

        while (currentOffset < size) {
            if (currentOffset + chunk >= size)
                chunk = Math.toIntExact(size - currentOffset);

            var readBuffer = new byte[chunk];

            if (inStream.read(readBuffer) != chunk)
                throw new IOException("Reading from stream suddenly ended");

            writeUsb(readBuffer, "Failure during file transfer");

            currentOffset += chunk;
            logPrinter.updateProgress((double)currentOffset / (double)size);
        }
    }

    private void sendFileMetadata(byte[] sizeAsBytes) throws Exception{
        writeUsb(STANDARD_REPLY, "Sending response [1/3]");
        writeUsb(sizeAsBytes, "Sending response [2/3]");       // Send EXACTLY what received
        writeUsb(TWELVE_ZERO_BYTES, "Sending response [3/3]"); // kinda another one padding
    }

    /**
     * Sending anything to USB device
     * @param message is payload
     * @param operation is operation description
     * */
    private void writeUsb(byte[] message, String operation) throws Exception {
        var wBufferTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            int result = LibUsb.bulkTransfer(handlerNS,
                    OUT_EP,
                    ByteBuffer.allocateDirect(message.length).put(message), //writeBuffer.order() equals BIG_ENDIAN; Don't writeBuffer.rewind();
                    wBufferTransferred,
                    5050);  // TIMEOUT. 0 stands for unlimited

            switch (result){
                case LibUsb.SUCCESS:
                    if (wBufferTransferred.get() == message.length)
                        return;
                    print(operation +
                            "\n         Data transfer issue [write]" +
                            "\n         Requested: "+message.length+
                            "\n         Transferred: "+wBufferTransferred.get(), FAIL);
                    throw new LibUsbException("Transferred amount of data mismatch", LibUsb.SUCCESS);
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    print(operation +
                            "\n         Data transfer issue [write]" +
                            "\n         Returned: "+ LibUsb.errorName(result) +
                            "\n         (execution stopped)", FAIL);
                    throw new LibUsbException(result);
            }
        }
        throw new InterruptedException("Execution interrupted");
    }
    /**
     * Read USB response
     * @return byte array if data read successful
     *         'null' on failure
     * */
    private byte[] readUsb() throws Exception{
        var readBuffer = ByteBuffer.allocateDirect(512);
        // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
        var rBufferTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            var result = LibUsb.bulkTransfer(handlerNS,
                    IN_EP,
                    readBuffer,
                    rBufferTransferred,
                    1000);

            switch (result) {
                case LibUsb.SUCCESS:
                    var receivedBytes = new byte[rBufferTransferred.get()];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    throw new Exception("Data transfer issue [read]" +
                            "\n         Returned: " + LibUsb.errorName(result)+
                            "\n         (execution stopped)");
            }
        }
        throw new InterruptedException("Execution interrupted");
    }
}
