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

import nsusbloader.COM.INSTask;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.DeviceHandle;

import java.io.File;
import java.util.*;

public abstract class TransferModule {
    EFileStatus status = EFileStatus.UNKNOWN;

    LinkedHashMap<String, File> nspMap;
    ILogPrinter logPrinter;
    DeviceHandle handlerNS;
    INSTask task;

    TransferModule(DeviceHandle handler, LinkedHashMap<String, File> nspMap, INSTask task, ILogPrinter printer){
        this.handlerNS = handler;
        this.nspMap = nspMap;
        this.task = task;
        this.logPrinter = printer;

        // Validate split files to be sure that there is no crap
        //logPrinter.print("TransferModule: Validating split files ...", EMsgType.INFO); // NOTE: Used for debug
        Iterator<Map.Entry<String, File>> iterator = nspMap.entrySet().iterator();
        while (iterator.hasNext()){
            File f = iterator.next().getValue();
            if (f.isDirectory()){
                File[] subFiles = f.listFiles((file, name) -> name.matches("[0-9]{2}"));
                if (subFiles == null || subFiles.length == 0) {
                    logPrinter.print("TransferModule: Removing empty folder: " + f.getName(), EMsgType.WARNING);
                    iterator.remove();
                }
                else {
                    Arrays.sort(subFiles, Comparator.comparingInt(file -> Integer.parseInt(file.getName())));

                    for (int i = subFiles.length - 2; i > 0 ; i--){
                        if (subFiles[i].length() < subFiles[i-1].length()) {
                            logPrinter.print("TransferModule: Removing strange split file: "+f.getName()+
                                    "\n      (Chunk sizes of the split file are not the same, but has to be.)", EMsgType.WARNING);
                            iterator.remove();
                        } // what
                    } // a
                } // nice
            } // stairway
        } // here =)
        //logPrinter.print("TransferModule: Validation complete.", EMsgType.INFO);  // NOTE: Used for debug
    }

    public EFileStatus getStatus(){ return status; }
}
