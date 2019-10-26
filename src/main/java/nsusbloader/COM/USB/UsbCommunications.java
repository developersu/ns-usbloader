package nsusbloader.COM.USB;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.*;

import java.io.*;

import java.util.*;

// TODO: add filter option to show only NSP files
public class UsbCommunications extends Task<Void> {

    private LogPrinter logPrinter;
    private LinkedHashMap<String, File> nspMap;
    private String protocol;
    private boolean nspFilterForGl;
    /*
        Ok, here is a story. We will pass to NS only file names, not full path. => see nspMap where 'key' is a file name.
        File name itself should not be greater then 512 bytes, but in real world it's limited by OS to something like 256 bytes.
        For sure, there could be FS that supports more then 256 and even more then 512 bytes. So if user decides to set name greater then 512 bytes, everything will ruin.
        There is no extra validations for this situation.
        Why we poking around 512 bytes? Because it's the maximum size of byte-array that USB endpoind of NS could return. And in runtime it returns the filename.
        Therefore, the file name shouldn't be greater then 512. If file name + path-to-file is greater then 512 bytes, we can handle it: sending only file name instead of full path.

        Since this application let user an ability (theoretically) to choose same files in different folders, the latest selected file will be added to the list and handled correctly.
        I have no idea why he/she will make a decision to do that. Just in case, we're good in this point.
         */
    public UsbCommunications(List<File> nspList, String protocol, boolean filterNspFilesOnlyForGl){
        this.protocol = protocol;
        this.nspFilterForGl = filterNspFilesOnlyForGl;
        this.nspMap = new LinkedHashMap<>();
        for (File f: nspList)
            nspMap.put(f.getName(), f);
        this.logPrinter = new LogPrinter();
    }

    @Override
    protected Void call() {
        logPrinter.print("\tStart chain", EMsgType.INFO);

        UsbConnect usbConnect = new UsbConnect(logPrinter);

        if (! usbConnect.isConnected()){
            close(EFileStatus.FAILED);
            return null;
        }

        DeviceHandle handler = usbConnect.getHandlerNS();

        TransferModule module;

        if (protocol.equals("TinFoil"))
            module = new TinFoil(handler, nspMap, this, logPrinter);
        else if (protocol.equals("GoldLeaf"))
            module = new GoldLeaf(handler, nspMap, this, logPrinter, nspFilterForGl);
        else
            module = new GoldLeaf_05(handler, nspMap, this, logPrinter);

        usbConnect.close();

        close(module.getStatus());

        return null;
    }

    /**
     * Report status and close
     */
    private void close(EFileStatus status){
        logPrinter.update(nspMap, status);
        logPrinter.print("\tEnd chain", EMsgType.INFO);
        logPrinter.close();
    }

}