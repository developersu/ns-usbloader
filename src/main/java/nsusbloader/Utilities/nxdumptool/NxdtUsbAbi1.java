/*
    Copyright 2019-2020 Dmitry Isaenko, DarkMatterCore

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
package nsusbloader.Utilities.nxdumptool;

import nsusbloader.COM.USB.UsbErrorCodes;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class NxdtUsbAbi1 {
    private final ILogPrinter logPrinter;
    private final DeviceHandle handlerNS;
    private final String saveToPath;
    private final NxdtTask parent;

    private boolean isWindows;
    private boolean isWindows10;

    private static final int NXDT_MAX_DIRECTIVE_SIZE = 0x1000;
    private static final int NXDT_FILE_CHUNK_SIZE = 0x800000;
    private static final int NXDT_FILE_PROPERTIES_MAX_NAME_LENGTH = 0x300;

    private static final byte ABI_VERSION = 1;
    private static final byte[] MAGIC_NXDT = { 0x4e, 0x58, 0x44, 0x54 };

    private static final int CMD_HANDSHAKE = 0;
    private static final int CMD_SEND_FILE_PROPERTIES = 1;
    private static final int CMD_ENDSESSION = 3;

    // Standard set of possible replies
    private static final byte[] USBSTATUS_SUCCESS = { 0x4e, 0x58, 0x44, 0x54,
                                                    0x00, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00 };
    private static final byte[] USBSTATUS_INVALID_MAGIC = { 0x4e, 0x58, 0x44, 0x54,
                                                    0x04, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00 };
    private static final byte[] USBSTATUS_UNSUPPORTED_CMD = { 0x4e, 0x58, 0x44, 0x54,
                                                    0x05, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00 };
    private static final byte[] USBSTATUS_UNSUPPORTED_ABI = { 0x4e, 0x58, 0x44, 0x54,
                                                    0x06, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00 };
    private static final byte[] USBSTATUS_MALFORMED_REQUEST = { 0x4e, 0x58, 0x44, 0x54,
                                                    0x07, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00 };
    private static final byte[] USBSTATUS_HOSTIOERROR = { 0x4e, 0x58, 0x44, 0x54,
                                                    0x08, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00,
                                                    0x00, 0x00, 0x00, 0x00 };

    public NxdtUsbAbi1(DeviceHandle handler,
                       ILogPrinter logPrinter,
                       String saveToPath,
                       NxdtTask parent
    ){
        this.handlerNS = handler;
        this.logPrinter = logPrinter;
        this.parent = parent;
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("windows");

        if (isWindows)
            isWindows10 = System.getProperty("os.name").toLowerCase().contains("windows 10");

        if (! saveToPath.endsWith(File.separator))
            this.saveToPath = saveToPath + File.separator;
        else
            this.saveToPath = saveToPath;

        readLoop();
    }

    private void readLoop(){
        logPrinter.print("Awaiting for handshake", EMsgType.INFO);
        try {
            byte[] directive;
            int command;

            while (true){
                directive = readUsbDirective();

                if (isInvalidDirective(directive))
                    continue;

                command = getLEint(directive, 4);

                switch (command){
                    case CMD_HANDSHAKE:
                        performHandshake(directive);
                        break;
                    case CMD_SEND_FILE_PROPERTIES:
                        handleSendFileProperties(directive);
                        break;
                    case CMD_ENDSESSION:
                        logPrinter.print("Session successfully ended.", EMsgType.PASS);
                        return;
                    default:
                        writeUsb(USBSTATUS_UNSUPPORTED_CMD);
                        logPrinter.print(String.format("Unsupported command 0x%08x", command), EMsgType.FAIL);
                }
            }
        }
        catch (InterruptedException ie){
            logPrinter.print("Execution interrupted", EMsgType.INFO);
        }
        catch (Exception e){
            e.printStackTrace();
            logPrinter.print(e.getMessage(), EMsgType.INFO);
            logPrinter.print("Terminating now", EMsgType.FAIL);
        }
    };

    private boolean isInvalidDirective(byte[] message) throws Exception{
        if (message.length < 0x10){
            writeUsb(USBSTATUS_MALFORMED_REQUEST);
            logPrinter.print("Directive is too small. Only "+message.length+" bytes received.", EMsgType.FAIL);
            return true;
        }

        if (! Arrays.equals(Arrays.copyOfRange(message, 0,4), MAGIC_NXDT)){
            writeUsb(USBSTATUS_INVALID_MAGIC);
            logPrinter.print("Invalid 'MAGIC'", EMsgType.FAIL);
            return true;
        }

        int payloadSize = getLEint(message, 0x8);
        if (payloadSize + 0x10 != message.length){
            writeUsb(USBSTATUS_MALFORMED_REQUEST);
            logPrinter.print("Invalid directive info block size. "+message.length+" bytes received while "+payloadSize+" expected.", EMsgType.FAIL);
            return true;
        }
        return false;
    }

    private void performHandshake(byte[] message) throws Exception{
        final byte versionMajor = message[0x10];
        final byte versionMinor = message[0x11];
        final byte versionMicro = message[0x12];
        final byte versionABI = message[0x13];

        logPrinter.print("nxdumptool v"+versionMajor+"."+versionMinor+"."+versionMicro+" ABI v"+versionABI, EMsgType.INFO);

        if (ABI_VERSION != versionABI){
            writeUsb(USBSTATUS_UNSUPPORTED_ABI);
            throw new Exception("ABI v"+versionABI+" is not supported in current version.");
        }
        writeUsb(USBSTATUS_SUCCESS);
    }

    private void handleSendFileProperties(byte[] message) throws Exception{
        final long fileSize = getLElong(message, 0x10);
        final int fileNameLen = getLEint(message, 0x18);
        String filename = new String(message, 0x20, fileNameLen, StandardCharsets.UTF_8);

        if (fileNameLen <= 0 || fileNameLen > NXDT_FILE_PROPERTIES_MAX_NAME_LENGTH){
            writeUsb(USBSTATUS_MALFORMED_REQUEST);
            logPrinter.print("Invalid filename length!", EMsgType.FAIL);
            return;
        }

        // If RomFs related
        if (isRomFs(filename)) {
            if (isWindows)
                filename = saveToPath + filename.replaceAll("/", "\\\\");
            else
                filename = saveToPath + filename;

            createPath(filename);
        }
        else {
            logPrinter.print("Receiving: '"+filename+"' ("+fileSize+" b)", EMsgType.INFO);
            filename = saveToPath + filename;
        }

        File fileToDump = new File(filename);
        // Check if enough space
        if (fileToDump.getParentFile().getFreeSpace() <= fileSize){
            writeUsb(USBSTATUS_HOSTIOERROR);
            logPrinter.print("Not enough space on selected volume. Need: "+fileSize+
                    " while available: "+fileToDump.getParentFile().getFreeSpace(), EMsgType.FAIL);
            return;
        }
        // Check if FS is NOT read-only
        if (! (fileToDump.canWrite() || fileToDump.createNewFile()) ){
            writeUsb(USBSTATUS_HOSTIOERROR);
            logPrinter.print("Unable to write into selected volume: "+fileToDump.getAbsolutePath(), EMsgType.FAIL);
            return;
        }

        writeUsb(USBSTATUS_SUCCESS);

        if (fileSize == 0)
            return;

        if (isWindows10)
            dumpFileOnWindowsTen(fileToDump, fileSize);
        else
            dumpFile(fileToDump, fileSize);

        writeUsb(USBSTATUS_SUCCESS);

    }

    private int getLEint(byte[] bytes, int fromOffset){
        return ByteBuffer.wrap(bytes, fromOffset, 0x4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private long getLElong(byte[] bytes, int fromOffset){
        return ByteBuffer.wrap(bytes, fromOffset, 0x8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private boolean isRomFs(String filename){
        return filename.startsWith("/");
    }

    private void createPath(String path) throws Exception{
        File resultingFile = new File(path);
        File folderForTheFile = resultingFile.getParentFile();

        if (folderForTheFile.exists())
            return;

        if (folderForTheFile.mkdirs())
            return;

        writeUsb(USBSTATUS_HOSTIOERROR);
        throw new Exception("Unable to create dir(s) for file in "+folderForTheFile);
    }

    private void dumpFile(File file, long size) throws Exception{
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file, false));

        byte[] readBuffer;
        long received = 0;
        int bufferSize;

        while (received < size){
            readBuffer = readUsbFile();
            bos.write(readBuffer);
            bufferSize = readBuffer.length;
            received += bufferSize;
            logPrinter.updateProgress((received + bufferSize) / (size / 100.0) / 100.0);
        }
        logPrinter.updateProgress(1.0);
        bos.close();
    }

    // @see https://bugs.openjdk.java.net/browse/JDK-8146538
    private void dumpFileOnWindowsTen(File file, long size) throws Exception{
        FileOutputStream fos = new FileOutputStream(file, true);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        FileDescriptor fd = fos.getFD();

        byte[] readBuffer;
        long received = 0;
        int bufferSize;

        while (received < size){
            readBuffer = readUsbFile();
            bos.write(readBuffer);
            fd.sync(); // Fixes flushing under Windows (unharmful for other OS)
            bufferSize = readBuffer.length;
            received += bufferSize;

            logPrinter.updateProgress((received + bufferSize) / (size / 100.0) / 100.0);
        }

        logPrinter.updateProgress(1.0);
        bos.close();
    }

    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private void writeUsb(byte[] message) throws Exception{
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(message.length);
        writeBuffer.put(message);
        IntBuffer writeBufTransferred = IntBuffer.allocate(1);

        if ( parent.isCancelled() )
            throw new InterruptedException("Execution interrupted");

        int result = LibUsb.bulkTransfer(handlerNS, (byte) 0x01, writeBuffer, writeBufTransferred, 5050);

        switch (result){
            case LibUsb.SUCCESS:
                if (writeBufTransferred.get() == message.length)
                    return;
                throw new Exception("Data transfer issue [write]" +
                        "\n         Requested: "+message.length+
                        "\n         Transferred: "+writeBufTransferred.get());
            default:
                throw new Exception("Data transfer issue [write]" +
                        "\n         Returned: "+ UsbErrorCodes.getErrCode(result) +
                        "\n         (execution stopped)");
        }

    }
    /**
     * Reading what USB device responded (command).
     * @return byte array if data read successful
     *         'null' if read failed
     * */
    private byte[] readUsbDirective() throws Exception{
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(NXDT_MAX_DIRECTIVE_SIZE);
        // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
        IntBuffer readBufTransferred = IntBuffer.allocate(1);
        int result;
        while (! parent.isCancelled()) {
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    int trans = readBufTransferred.get();
                    byte[] receivedBytes = new byte[trans];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    break;
                default:
                    throw new Exception("Data transfer issue [read command]" +
                            "\n         Returned: " + UsbErrorCodes.getErrCode(result)+
                            "\n         (execution stopped)");
            }
        }
        throw new InterruptedException();
    }
    /**
     * Reading what USB device responded (file).
     * @return byte array if data read successful
     *         'null' if read failed
     * */
    private byte[] readUsbFile() throws Exception{
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(NXDT_FILE_CHUNK_SIZE);
        IntBuffer readBufTransferred = IntBuffer.allocate(1);
        int result;
        int countDown = 0;
        while (! parent.isCancelled() && countDown < 5) {
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 1000);

            switch (result) {
                case LibUsb.SUCCESS:
                    int trans = readBufTransferred.get();
                    byte[] receivedBytes = new byte[trans];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    countDown++;
                    break;
                default:
                    throw new Exception("Data transfer issue [read file]" +
                            "\n         Returned: " + UsbErrorCodes.getErrCode(result)+
                            "\n         (execution stopped)");
            }
        }
        throw new InterruptedException();
    }
}
