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
package nsusbloader.Utilities.nxdumptool;

import nsusbloader.com.usb.UsbConnect;
import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.DeviceHandle;

public class NxdtTask extends CancellableRunnable {

    private final ILogPrinter logPrinter;
    private final String saveToLocation;

    public NxdtTask(String saveToLocation){
        this.logPrinter = Log.getPrinter(EModule.NXDT);
        this.saveToLocation = saveToLocation;
    }

    @Override
    public void run() {
        logPrinter.print("Save to location: "+ saveToLocation, EMsgType.INFO);
        logPrinter.print("=============== nxdumptool ===============", EMsgType.INFO);

        UsbConnect usbConnect = UsbConnect.connectHomebrewMode(logPrinter);
        
        if (! usbConnect.isConnected()){
            logPrinter.close();
            return;
        }

        DeviceHandle handler = usbConnect.getNsHandler();

        try {
            new NxdtUsbAbi1(handler, logPrinter, saveToLocation, this);
        }
        catch (Exception e){
            logPrinter.print(e.getMessage(), EMsgType.FAIL);
        }

        logPrinter.print(".:: Complete ::.", EMsgType.PASS);

        usbConnect.close();
        logPrinter.updateOneLinerStatus(true);
        logPrinter.close();
    }
}