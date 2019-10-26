package nsusbloader.COM.USB;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.DeviceHandle;

import java.io.File;
import java.util.*;

public abstract class TransferModule {
    EFileStatus status = EFileStatus.UNKNOWN;

    LinkedHashMap<String, File> nspMap;
    LogPrinter logPrinter;
    DeviceHandle handlerNS;
    Task<Void> task;

    TransferModule(DeviceHandle handler, LinkedHashMap<String, File> nspMap, Task<Void> task, LogPrinter printer){
        this.handlerNS = handler;
        this.nspMap = nspMap;
        this.task = task;
        this.logPrinter = printer;

        // Validate split files to be sure that there is no crap
        logPrinter.print("TransferModule: Validating split files ...", EMsgType.INFO);
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
        logPrinter.print("TransferModule: Validation complete.", EMsgType.INFO);
    }

    public EFileStatus getStatus(){ return status; }
}
