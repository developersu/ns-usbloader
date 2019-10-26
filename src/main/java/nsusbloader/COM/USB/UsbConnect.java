package nsusbloader.COM.USB;

import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.*;

class UsbConnect {
    private final int DEFAULT_INTERFACE = 0;

    private Context contextNS;
    private DeviceHandle handlerNS;

    private LogPrinter logPrinter;

    private boolean connected;
    
    UsbConnect(LogPrinter logPrinter){
        this.logPrinter = logPrinter;
        this.connected = false;

        int result;

        // Creating Context required by libusb. Optional. TODO: Consider removing.
        contextNS = new Context();
        result = LibUsb.init(contextNS);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("libusb initialization\n  Returned: "+result, EMsgType.FAIL);
            close();
            return;
        }
        else
            logPrinter.print("libusb initialization", EMsgType.PASS);

        // Searching for NS in devices: obtain list of all devices
        DeviceList deviceList = new DeviceList();
        result = LibUsb.getDeviceList(contextNS, deviceList);
        if (result < 0) {
            logPrinter.print("Get device list\n  Returned: "+result, EMsgType.FAIL);
            close();
            return;
        }
        else
            logPrinter.print("Get device list", EMsgType.PASS);
        // Searching for NS in devices: looking for NS
        DeviceDescriptor descriptor;
        Device deviceNS = null;
        for (Device device: deviceList){
            descriptor = new DeviceDescriptor();                // mmm.. leave it as is.
            result = LibUsb.getDeviceDescriptor(device, descriptor);
            if (result != LibUsb.SUCCESS){
                logPrinter.print("Read file descriptors for USB devices\n  Returned: "+result, EMsgType.FAIL);
                LibUsb.freeDeviceList(deviceList, true);
                close();
                return;
            }
            if ((descriptor.idVendor() == 0x057E) && descriptor.idProduct() == 0x3000){
                deviceNS = device;
                logPrinter.print("Read file descriptors for USB devices", EMsgType.PASS);
                break;
            }
        }
        // Free device list.
        if (deviceNS != null){
            logPrinter.print("NS in connected USB devices found", EMsgType.PASS);
        }
        else {
            logPrinter.print("NS in connected USB devices not found", EMsgType.FAIL);
            close();
            return;
        }
        // Handle NS device
        handlerNS = new DeviceHandle();
        result = LibUsb.open(deviceNS, handlerNS);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("Open NS USB device\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            if (result == LibUsb.ERROR_ACCESS)
                logPrinter.print("Double check that you have administrator privileges (you're 'root') or check 'udev' rules set for this user (linux only)!\n\n" +
                        "Steps to set 'udev' rules:\n" +
                        "root # vim /etc/udev/rules.d/99-NS.rules\n" +
                        "SUBSYSTEM==\"usb\", ATTRS{idVendor}==\"057e\", ATTRS{idProduct}==\"3000\", GROUP=\"plugdev\"\n" +
                        "root # udevadm control --reload-rules && udevadm trigger\n", EMsgType.INFO);
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
        // Check if this device uses kernel driver and detach if possible:
        boolean canDetach = LibUsb.hasCapability(LibUsb.CAP_SUPPORTS_DETACH_KERNEL_DRIVER); // if cant, it's windows ot old lib
        if (canDetach){
            int usedByKernel = LibUsb.kernelDriverActive(handlerNS, DEFAULT_INTERFACE);
            if (usedByKernel == LibUsb.SUCCESS)
                logPrinter.print("Can proceed with libusb driver", EMsgType.PASS);   // we're good
            else if (usedByKernel == 1) {      // used by kernel
                result = LibUsb.detachKernelDriver(handlerNS, DEFAULT_INTERFACE);
                logPrinter.print("Detach kernel required", EMsgType.INFO);
                if (result != 0) {
                    logPrinter.print("Detach kernel\n  Returned: " + UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    close();
                    return;
                }
                else
                    logPrinter.print("Detach kernel", EMsgType.PASS);
            }
            else
                logPrinter.print("Can't proceed with libusb driver\n  Returned: "+UsbErrorCodes.getErrCode(usedByKernel), EMsgType.FAIL);
        }
        else
            logPrinter.print("libusb doesn't support function 'CAP_SUPPORTS_DETACH_KERNEL_DRIVER'. It's normal. Proceeding.", EMsgType.WARNING);
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
        // Set configuration (soft reset if needed)
        result = LibUsb.setConfiguration(handlerNS, 1);     // 1 - configuration all we need
        if (result != LibUsb.SUCCESS){
            logPrinter.print("Set active configuration to device\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            close();
            return;
        }
        else
            logPrinter.print("Set active configuration to device.", EMsgType.PASS);

        // Claim interface
        result = LibUsb.claimInterface(handlerNS, DEFAULT_INTERFACE);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("Claim interface\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            close();
            return;
        }
        else
            logPrinter.print("Claim interface", EMsgType.PASS);

        this.connected = true;
    }

    /**
     * Get USB status
     * @return status of connection
     */
    boolean isConnected() { return connected; }
    /**
     * Getter for handler
     * @return DeviceHandle of NS
     */
    DeviceHandle getHandlerNS(){ return handlerNS; }
    /**
     * Correct exit
     * */
    void close(){
        // Close handler in the end
        if (handlerNS != null) {
            // Try to release interface
            int result = LibUsb.releaseInterface(handlerNS, DEFAULT_INTERFACE);

            if (result != LibUsb.SUCCESS)
                logPrinter.print("Release interface\n  Returned: "+result+" (sometimes it's not an issue)", EMsgType.WARNING);
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
