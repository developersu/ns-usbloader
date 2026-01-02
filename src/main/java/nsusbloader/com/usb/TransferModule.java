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
package nsusbloader.com.usb;

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.DeviceHandle;

import java.io.File;
import java.util.*;

import static java.util.Comparator.comparingInt;

public abstract class TransferModule {
    protected static final byte IN_EP = (byte) 0x81;
    protected static final byte OUT_EP = (byte) 0x01;

    protected EFileStatus status = EFileStatus.UNKNOWN;

    protected final LinkedHashMap<String, File> nspMap;
    protected final ILogPrinter logPrinter;
    protected final DeviceHandle handlerNS;
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
}
