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
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.*;

import java.io.*;

import java.util.*;

// TODO: add filter option to show only NSP files
public class UsbCommunications extends CancellableRunnable {

    private final ILogPrinter logPrinter;
    private final LinkedHashMap<String, File> nspMap;
    private final String protocol;
    private final boolean nspFilterForGl;

    public UsbCommunications(List<File> nspList, String protocol, boolean filterNspFilesOnlyForGl){
        this.protocol = protocol;
        this.nspFilterForGl = filterNspFilesOnlyForGl;
        this.nspMap = new LinkedHashMap<>();
        for (File f: nspList)
            nspMap.put(f.getName(), f);
        this.logPrinter = Log.getPrinter(EModule.USB_NET_TRANSFERS);
    }

    @Override
    public void run() {
        print("\tStart");

        UsbConnect usbConnect = UsbConnect.connectHomebrewMode(logPrinter);

        if (! usbConnect.isConnected()){
            close(EFileStatus.FAILED);
            return;
        }

        DeviceHandle handler = usbConnect.getNsHandler();

        TransferModule module;

        switch (protocol) {
            case "TinFoil":
                module = new TinFoil(handler, nspMap, this, logPrinter);
                break;
            case "GoldLeafv0.10+":
                module = new GoldLeaf_010(handler, nspMap, this, logPrinter, nspFilterForGl);
                break;
            case "GoldLeafv0.8-0.9":
                module = new GoldLeaf_08(handler, nspMap, this, logPrinter, nspFilterForGl);
                break;
            case "GoldLeafv0.7.x":
                module = new GoldLeaf_07(handler, nspMap, this, logPrinter, nspFilterForGl);
                break;
            default:
                module = new GoldLeaf_05(handler, nspMap, this, logPrinter);
                break;
        }

        usbConnect.close();

        close(module.getStatus());
    }

    /**
     * Report status and close
     */
    private void close(EFileStatus status){
        logPrinter.update(nspMap, status);
        print("\tEnd");
        logPrinter.close();
    }
    private void print(String message){
        try {
            logPrinter.print(message, EMsgType.INFO);
        }
        catch (InterruptedException ie){
            ie.printStackTrace();
        }
    }
}