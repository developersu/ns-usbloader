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
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static java.nio.ByteBuffer.allocateDirect;
import static java.util.Comparator.comparingInt;
import static nsusbloader.NSLDataTypes.EMsgType.FAIL;
import static nsusbloader.NSLDataTypes.EMsgType.WARNING;

public abstract class TransferModule {
    private static final byte IN_EP = (byte) 0x81;
    private static final byte OUT_EP = (byte) 0x01;
    private final DeviceHandle handlerNS;

    protected EFileStatus status = EFileStatus.UNKNOWN;

    protected final LinkedHashMap<String, File> nspMap;
    protected final ILogPrinter logPrinter;
    protected final CancellableRunnable task;

    protected TransferModule(DeviceHandle handler,
                             LinkedHashMap<String, File> nspMap,
                             CancellableRunnable task,
                             ILogPrinter printer){
        this.handlerNS = handler;
        this.nspMap = nspMap;
        this.task = task;
        this.logPrinter = printer;

        filterFiles();
    }
    void filterFiles(){
        nspMap.values().removeIf(f -> {
            if (f.isFile())
                return false;

            var subFiles = f.listFiles((file, name) -> name.matches("[0-9]{2}"));

            if (subFiles == null || subFiles.length == 0) {
                print("TransferModule: Exclude folder: " + f.getName(), EMsgType.WARNING);
                return true;
            }

            Arrays.sort(subFiles, comparingInt(file -> Integer.parseInt(file.getName())));

            for (int i = subFiles.length - 2; i > 0 ; i--){
                if (subFiles[i].length() != subFiles[i-1].length()) {
                    print("TransferModule: Exclude split file: "+f.getName()+
                            "\n      Chunk sizes of the split file are not the same, but has to be.", EMsgType.WARNING);
                    return true;
                }
            }

            long firstFileLength = subFiles[0].length();
            long lastFileLength = subFiles[subFiles.length-1].length();

            if (lastFileLength > firstFileLength){
                print("TransferModule: Exclude split file: "+f.getName()+
                        "\n      Chunk sizes of the split file are not the same, but has to be.", EMsgType.WARNING);
                return true;
            }
            return false;
        });
    }
    public EFileStatus getStatus(){ return status; }

    protected void print(String message, EMsgType type){
        try {
            logPrinter.print(message, type);
        }
        catch (InterruptedException ie){
            ie.printStackTrace();
        }
    }

    /**
     * Read USB response
     * @param bufferSize — count of bytes to read
     * @return byte array if data read successful
     *         'null' on failure
     */
    protected byte[] readUsb(int bufferSize) throws Exception {
        var readBuffer = ByteBuffer.allocateDirect(bufferSize);
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
                    print("Data transfer issue [read]" +
                            "\n         Returned: "+ LibUsb.errorName(result) +
                            "\n         (execution stopped)", FAIL);
                    throw new LibUsbException(result);
            }
        }
        throw new InterruptedException("Execution interrupted");
    }

    /**
     * Sending anything to USB device
     * @param message is payload
     * @param operation is operation/error description
     */
    protected void writeUsb(byte[] message, String operation) throws Exception {
        var wBufferTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            int result = LibUsb.bulkTransfer(handlerNS,
                    OUT_EP,
                    allocateDirect(message.length).put(message), //.order() is BIG_ENDIAN; Don't .rewind();
                    wBufferTransferred,
                    1000);

            switch (result) {
                case LibUsb.SUCCESS:
                    if (wBufferTransferred.get() == message.length)
                        return;
                    print(operation +
                            "\n         Data transfer issue [write]" +
                            "\n         Requested: "+message.length+
                            "\n         Transferred: "+wBufferTransferred.get(), FAIL);
                    throw new LibUsbException("Transferred amount of data mismatch", LibUsb.SUCCESS);
                case LibUsb.ERROR_TIMEOUT:
                    print("Data transfer issue [write]: Timeout error. Keep trying", WARNING);
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
}