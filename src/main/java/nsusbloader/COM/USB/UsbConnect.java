package nsusbloader.COM.USB;

import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.*;

public class UsbConnect {
    private int DEFAULT_INTERFACE = 0;

    private Context contextNS;
    private DeviceHandle handlerNS;
    private Device deviceNS;

    private LogPrinter logPrinter;

    private boolean connected; // TODO: replace to 'connectionFailure' and invert requests everywhere

    public UsbConnect(LogPrinter logPrinter, boolean initForRCM){
        this.logPrinter = logPrinter;
        this.connected = false;

        short VENDOR_ID;
        short PRODUCT_ID;

        if (initForRCM){
            // CORRECT NV:
            VENDOR_ID = 0x0955;
            PRODUCT_ID = 0x7321;
            /* // QA:
            VENDOR_ID = 0x1a86;
            PRODUCT_ID = 0x7523;
             */
        }
        else {
            VENDOR_ID = 0x057E;
            PRODUCT_ID = 0x3000;
        }

        int result;

        // Creating Context required by libusb. Optional. TODO: Consider removing.
        contextNS = new Context();
        result = LibUsb.init(contextNS);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("libusb initialization\n  Returned: "+result, EMsgType.FAIL);
            close();
            return;
        }
        logPrinter.print("libusb initialization", EMsgType.PASS);

        // Searching for NS in devices: obtain list of all devices
        DeviceList deviceList = new DeviceList();
        result = LibUsb.getDeviceList(contextNS, deviceList);
        if (result < 0) {
            logPrinter.print("Get device list\n  Returned: "+result, EMsgType.FAIL);
            close();
            return;
        }
        logPrinter.print("Get device list", EMsgType.PASS);
        // Searching for NS in devices: looking for NS
        DeviceDescriptor descriptor;
        deviceNS = null;
        for (Device device: deviceList){
            descriptor = new DeviceDescriptor();                // mmm.. leave it as is.
            result = LibUsb.getDeviceDescriptor(device, descriptor);
            if (result != LibUsb.SUCCESS){
                logPrinter.print("Read file descriptors for USB devices\n  Returned: "+result, EMsgType.FAIL);
                LibUsb.freeDeviceList(deviceList, true);
                close();
                return;
            }
            if ((descriptor.idVendor() == VENDOR_ID) && descriptor.idProduct() == PRODUCT_ID){
                deviceNS = device;
                logPrinter.print("Read file descriptors for USB devices", EMsgType.PASS);
                break;
            }
        }
        // Free device list.
        if (deviceNS == null){
            logPrinter.print("NS in connected USB devices not found", EMsgType.FAIL);
            close();
            return;
        }
        logPrinter.print("NS in connected USB devices found", EMsgType.PASS);

        // Handle NS device
        handlerNS = new DeviceHandle();
        result = LibUsb.open(deviceNS, handlerNS);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("Open NS USB device\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            if (result == LibUsb.ERROR_ACCESS)
                logPrinter.print("Double check that you have administrator privileges (you're 'root') or check 'udev' rules set for this user (linux only)!\n\n" +
                        String.format("Steps to set 'udev' rules:\n" +
                                "root # vim /etc/udev/rules.d/99-NS"+(initForRCM?"RCM":"")+".rules\n" +
                                "SUBSYSTEM==\"usb\", ATTRS{idVendor}==\"%04x\", ATTRS{idProduct}==\"%04x\", GROUP=\"plugdev\"\n" +
                                "root # udevadm control --reload-rules && udevadm trigger\n", VENDOR_ID, PRODUCT_ID)
                        , EMsgType.INFO);
            // Let's make a bit dirty workaround since such shit happened
            logPrinter.print("Requested context close", EMsgType.INFO);
            LibUsb.exit(contextNS);
            return;         // And close
        }
        else
            logPrinter.print("Open NS USB device", EMsgType.PASS);

        logPrinter.print("Free device list", EMsgType.INFO);
        LibUsb.freeDeviceList(deviceList, true);

        // DO some stuff to connected NS
        // Actually, there are not drivers in Linux kernel that are using this device..
        if (LibUsb.setAutoDetachKernelDriver(handlerNS, true) == LibUsb.SUCCESS)
            logPrinter.print("Handle kernel driver attach & detach", EMsgType.PASS);
        else
            logPrinter.print("Skip kernel driver attach & detach", EMsgType.INFO);
        /*
        // Reset device
        result = LibUsb.resetDevice(handlerNS);
        if (result == 0)
            logPrinter.print("Reset device", EMsgType.PASS);
        else {
            logPrinter.print("Reset device returned: " + result, EMsgType.FAIL);
            updateAndClose();
            return;
        }
        */
        if ( ! initForRCM){
            // Set configuration (soft reset if needed)
            result = LibUsb.setConfiguration(handlerNS, 1);     // 1 - configuration all we need
            if (result != LibUsb.SUCCESS){
                logPrinter.print("Set active configuration to device\n         Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                close();
                return;
            }
            logPrinter.print("Set active configuration to device.", EMsgType.PASS);
        }
        // Claim interface
        result = LibUsb.claimInterface(handlerNS, DEFAULT_INTERFACE);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("Claim interface\n         Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            close();
            return;
        }
        logPrinter.print("Claim interface", EMsgType.PASS);

        this.connected = true;
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
            int result = LibUsb.releaseInterface(handlerNS, DEFAULT_INTERFACE);

            if (result != LibUsb.SUCCESS)
                logPrinter.print("Release interface" +
                        "\n         Returned: "+result+" (sometimes it's not an issue)", EMsgType.WARNING);
            else
                logPrinter.print("Release interface", EMsgType.PASS);

            LibUsb.close(handlerNS);
            logPrinter.print("Requested handler close", EMsgType.INFO);
        }
        // Close context in the end
        if (contextNS != null) {
            LibUsb.exit(contextNS);
            logPrinter.print("Requested context close", EMsgType.INFO);
        }
    }
}
