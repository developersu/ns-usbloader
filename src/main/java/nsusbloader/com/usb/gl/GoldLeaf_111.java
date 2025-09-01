/*
    Copyright 2019-2025 Dmitry Isaenko

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
package nsusbloader.com.usb.gl;

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.com.helpers.NSSplitReader;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;

import static nsusbloader.com.usb.gl.Converters.longToArrLE;

/**
 * GoldLeaf 1.1.1 processing
 */
public class GoldLeaf_111 extends GoldLeaf_010{
    public GoldLeaf_111(DeviceHandle handler, LinkedHashMap<String, File> nspMap,
                        CancellableRunnable task,
                        ILogPrinter logPrinter,
                        boolean nspFilter) {
        super(handler, nspMap, task, logPrinter, nspFilter);
    }

    @Override
    protected boolean readFile(String glFileName, long offset, long size) {
        var fileName = glFileName.replaceFirst("^.*?:/", "");
        System.out.println(fileName+" readFile "+glFileName+"\t"+offset+"\t"+size+"\n");
        if (glFileName.startsWith("VIRT:/")){                                              // Could have split-file
            // Let's find out which file requested
            var fNamePath = nspMap.get(fileName).getAbsolutePath(); // NOTE: 6 = "VIRT:/".length
            // If we don't have this file opened, let's open it
            if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(fNamePath))) {
                if (openReadFileNameAndPath != null)                                      // (Try to) close what opened
                    closeRAFandSplitReader();
                try{                                                                      // And open the rest
                    var tempFile = nspMap.get(fileName);
                    if (tempFile.isDirectory()) {
                        randAccessFile = null;
                        splitReader = new NSSplitReader(tempFile, 0);
                    }
                    else {
                        splitReader = null;
                        randAccessFile = new RandomAccessFile(tempFile, "r");
                    }
                    openReadFileNameAndPath = fNamePath;
                }
                catch (IOException | NullPointerException ioe){
                    return writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
                }
            }
        }
        else { // SPEC:/ & HOME:/
            String filePath;

            if (glFileName.startsWith("SPEC:/")) {
                if (! fileName.equals(selectedFile.getName())) {
                    return writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command\n\trequested != selected:\n\t"
                            + glFileName + "\n\t" + selectedFile);
                }
                filePath = selectedFile.getAbsolutePath();
            }
            else {
                filePath = decodeGlPath(glFileName); // What requested?
            }
            // If we don't have this file opened, let's open it
            if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(filePath))) {
                if (openReadFileNameAndPath != null) // Try close what opened
                    closeRAF();
                try{                                   // Open what has to be opened
                    randAccessFile = new RandomAccessFile(filePath, "r");
                    openReadFileNameAndPath = filePath;
                }catch (IOException | NullPointerException ioe){
                    return writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
                }
            }
        }
        //----------------------- Actual transfer chain ------------------------
        try{
            var chunk = new byte[(int)size];
            int bytesRead;

            if (randAccessFile == null){
                splitReader.seek(offset);
                bytesRead = splitReader.read(chunk);   // How many bytes we got?
            }
            else {
                randAccessFile.seek(offset);
                bytesRead = randAccessFile.read(chunk); // How many bytes we got?
            }

            if (bytesRead != (int) size)    // Let's check that we read expected size
                return writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command [CMD]" +
                        "\n         At offset: " + offset +
                        "\n         Requested: " + size +
                        "\n         Received:  " + bytesRead);
            if (writeGL_PASS(longToArrLE(size), "GL Handle 'ReadFile' command [CMD]")) { // Reporting result
                System.out.println("+SENT 1 -");
                return true;
            }
            System.out.println("+SENT 1");
            if (writeToUsbV100(chunk)) {    // Bypassing bytes we read total // FIXME: move failure message into method
                print("GL Handle 'ReadFile' command", EMsgType.FAIL);
                System.out.println("+SENT 2 -");
                return true;
            }
            System.out.println("+SENT 2");
            return false;
        }
        catch (Exception ioe){
            closeOpenedReadFilesGl();
            return writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' transfer chain\n\t"+ioe.getMessage());
        }
    }

    private boolean writeToUsbV100(byte[] message) {
        var writeBufTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            int result = LibUsb.bulkTransfer(handlerNS,
                    OUT_EP,
                    ByteBuffer.allocateDirect(message.length)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .put(message),
                    writeBufTransferred,
                    1000);

            switch (result){
                case LibUsb.SUCCESS:
                    if (writeBufTransferred.get() == message.length)
                        return false;
                    print("GL Data transfer issue [write]\n         Requested: " +
                            message.length +
                            "\n         Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                    return true;
                case LibUsb.ERROR_TIMEOUT:
                    print("GL Data transfer issue [write]", EMsgType.WARNING);
                    continue;
                default:
                    print("GL Data transfer issue [write]\n         Returned: " +
                            LibUsb.errorName(result) +
                            "\n         GL Execution stopped", EMsgType.FAIL);
                    return true;
            }
        }
        print("GL Execution interrupted", EMsgType.INFO);
        return true;
    }
}
