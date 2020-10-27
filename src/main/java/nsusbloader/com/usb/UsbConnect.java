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

import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.*;

public class UsbConnect {
    private static final int DEFAULT_INTERFACE = 0;
    private static final int DEFAULT_HOMEBREW_CONFIGURATION = 1;

    private static final short RCM_VID = 0x0955;
    private static final short RCM_PID = 0x7321;

    private static final short HOMEBREW_VID = 0x057E;
    private static final short HOMEBREW_PID = 0x3000;

    // private static final short TEST_VID = 0x1a86;
    // private static final short TEST_PID = 0x7523;

    private Context contextNS;
    private DeviceHandle handlerNS;
    private Device deviceNS;

    private ILogPrinter logPrinter;

    private boolean connected; // TODO: replace to 'connectionFailure' and invert requests everywhere

    private short VENDOR_ID;
    private short PRODUCT_ID;

    private int returningValue;
    private DeviceList deviceList;

    public static UsbConnect connectRcmMode(ILogPrinter logPrinter){
        UsbConnect usbConnect = new UsbConnect(logPrinter);
        usbConnect.VENDOR_ID = RCM_VID;
        usbConnect.PRODUCT_ID = RCM_PID;
        try{
            usbConnect.createContextAndInitLibUSB();
            usbConnect.getDeviceList();
            usbConnect.findDevice();
            usbConnect.openDevice();
            usbConnect.freeDeviceList();
            usbConnect.setAutoDetachKernelDriver();
            //this.resetDevice();
            usbConnect.claimInterface();
            usbConnect.connected = true;
        }
        catch (Exception e){
            logPrinter.print(e.getMessage(), EMsgType.FAIL);
            usbConnect.close();
        }

        return usbConnect;
    }
    public static UsbConnect connectHomebrewMode(ILogPrinter logPrinter){
        UsbConnect usbConnect = new UsbConnect(logPrinter);
        usbConnect.VENDOR_ID = HOMEBREW_VID;
        usbConnect.PRODUCT_ID = HOMEBREW_PID;
        try {
            usbConnect.createContextAndInitLibUSB();
            usbConnect.getDeviceList();
            usbConnect.findDevice();
            usbConnect.openDevice();
            usbConnect.freeDeviceList();
            usbConnect.setAutoDetachKernelDriver();
            //this.resetDevice();
            usbConnect.setConfiguration(DEFAULT_HOMEBREW_CONFIGURATION);
            usbConnect.claimInterface();
            usbConnect.connected = true;
        }
        catch (Exception e){
            e.printStackTrace();
            logPrinter.print(e.getMessage(), EMsgType.FAIL);
            usbConnect.close();
        }
        return usbConnect;
    }

    private UsbConnect(){}

    private UsbConnect(ILogPrinter logPrinter){
        this.logPrinter = logPrinter;
        this.connected = false;
    };

    private void createContextAndInitLibUSB() throws Exception{
        // Creating Context required by libusb. Optional? Consider removing.
        contextNS = new Context();

        returningValue = LibUsb.init(contextNS);
        if (returningValue != LibUsb.SUCCESS)
            throw new Exception("LibUSB initialization failed: "+UsbErrorCodes.getErrCode(returningValue));
    }

    private void getDeviceList() throws Exception{
        deviceList = new DeviceList();
        returningValue = LibUsb.getDeviceList(contextNS, deviceList);
        if (returningValue < 0)
            throw new Exception("Can't get device list: "+UsbErrorCodes.getErrCode(returningValue));
    }

    private void findDevice() throws Exception{
        // Searching for NS in devices: looking for NS
        DeviceDescriptor descriptor;

        for (Device device: deviceList){
            descriptor = getDeviceDescriptor(device);

            if ((descriptor.idVendor() == VENDOR_ID) && descriptor.idProduct() == PRODUCT_ID){
                deviceNS = device;
                break;
            }
        }
        if (deviceNS == null) {
            this.freeDeviceList();
            throw new Exception("NS not found in connected USB devices");
        }
    }

    private DeviceDescriptor getDeviceDescriptor(Device device) throws Exception{
        DeviceDescriptor descriptor = new DeviceDescriptor();

        returningValue = LibUsb.getDeviceDescriptor(device, descriptor);

        if (returningValue != LibUsb.SUCCESS){
            this.freeDeviceList();
            throw new Exception("Get USB device descriptor failure: "+UsbErrorCodes.getErrCode(returningValue));
        }
        return descriptor;
    }

    private void openDevice() throws Exception{
        // Handle NS device
        handlerNS = new DeviceHandle();
        returningValue = LibUsb.open(deviceNS, handlerNS);

        if (returningValue == LibUsb.SUCCESS)
            return;

        handlerNS = null;  // Avoid issue on close();
        if (returningValue == LibUsb.ERROR_ACCESS) {
            throw new Exception(String.format(
                    "Can't open NS USB device: %s\n" +
                    "Double check that you have administrator privileges (you're 'root') or check 'udev' rules set for this user (linux only)!\n\n" +
                    "Steps to set 'udev' rules:\n" +
                    "root # vim /etc/udev/rules.d/99-NS" + ((RCM_VID == VENDOR_ID) ? "RCM" : "") + ".rules\n" +
                    "SUBSYSTEM==\"usb\", ATTRS{idVendor}==\"%04x\", ATTRS{idProduct}==\"%04x\", GROUP=\"plugdev\"\n" +
                    "root # udevadm control --reload-rules && udevadm trigger\n", UsbErrorCodes.getErrCode(returningValue), VENDOR_ID, PRODUCT_ID));
        }
        else
            throw new Exception("Can't open NS USB device: "+UsbErrorCodes.getErrCode(returningValue));
    }

    private void freeDeviceList(){
        LibUsb.freeDeviceList(deviceList, true);
    }

    private void setAutoDetachKernelDriver(){
        // Actually, there are no drivers in Linux kernel which uses this device.
        returningValue = LibUsb.setAutoDetachKernelDriver(handlerNS, true);
        if (returningValue != LibUsb.SUCCESS)
            logPrinter.print("Skip kernel driver attach & detach ("+UsbErrorCodes.getErrCode(returningValue)+")", EMsgType.INFO);
    }

    /*
    private void resetDevice(){
        result = LibUsb.resetDevice(handlerNS);
        if (returningValue != LibUsb.SUCCESS)
            throw new Exception("Reset device\n         Returned: "+UsbErrorCodes.getErrCode(returningValue));
    }
     */
    private void setConfiguration(int configuration) throws Exception{
        returningValue = LibUsb.setConfiguration(handlerNS, configuration);
        if (returningValue != LibUsb.SUCCESS)
            throw new Exception("Unable to set active configuration on device: "+UsbErrorCodes.getErrCode(returningValue));
    }
    private void claimInterface() throws Exception{
        returningValue = LibUsb.claimInterface(handlerNS, DEFAULT_INTERFACE);
        if (returningValue != LibUsb.SUCCESS)
            throw new Exception("Claim interface failure: "+UsbErrorCodes.getErrCode(returningValue));
    }
    /**
     * Get USB status
     * @return status of connection
     */
    public boolean isConnected() { return connected; }
    /**
     * Getter for handler
     * @return DeviceHandle of NS
     */
    public DeviceHandle getNsHandler(){ return handlerNS; }
    /**
     * Getter for 'Bus ID' where NS located found
     */
    public int getNsBus(){
        return LibUsb.getBusNumber(deviceNS);
    }
    /**
     * Getter for 'Device address' where NS located at
     */
    public int getNsAddress(){
        return LibUsb.getDeviceAddress(deviceNS);
    }
    /**
     * Correct exit
     * */
    public void close(){
        // Close handler in the end
        if (handlerNS != null) {
            // Try to release interface
            returningValue = LibUsb.releaseInterface(handlerNS, DEFAULT_INTERFACE);

            if (returningValue != LibUsb.SUCCESS) {
                logPrinter.print("Release interface failure: " +
                        UsbErrorCodes.getErrCode(returningValue) +
                        " (sometimes it's not an issue)", EMsgType.WARNING);
            }

            LibUsb.close(handlerNS);
        }
        // Close context in the end
        if (contextNS != null)
            LibUsb.exit(contextNS);
    }
}
