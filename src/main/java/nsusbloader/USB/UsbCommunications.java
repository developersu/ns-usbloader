package nsusbloader.USB;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.USB.PFS.PFSProvider;
import org.usb4java.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class UsbCommunications extends Task<Void> {
    private final int DEFAULT_INTERFACE = 0;

    private LogPrinter logPrinter;
    private EFileStatus status = EFileStatus.FAILED;

    private HashMap<String, File> nspMap;

    private Context contextNS;
    private DeviceHandle handlerNS;

    private String protocol;
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
    public UsbCommunications(List<File> nspList, String protocol){
        this.protocol = protocol;
        this.nspMap = new HashMap<>();
        for (File f: nspList)
            nspMap.put(f.getName(), f);
        this.logPrinter = new LogPrinter();
    }

    @Override
    protected Void call() {
        int result = -9999;

        logPrinter.print("\tStart chain", EMsgType.INFO);
        // Creating Context required by libusb. Optional. TODO: Consider removing.
        contextNS = new Context();
        result = LibUsb.init(contextNS);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("libusb initialization\n  Returned: "+result, EMsgType.FAIL);
            close();
            return null;
        }
        else
            logPrinter.print("libusb initialization", EMsgType.PASS);

        // Searching for NS in devices: obtain list of all devices
        DeviceList deviceList = new DeviceList();
        result = LibUsb.getDeviceList(contextNS, deviceList);
        if (result < 0) {
            logPrinter.print("Get device list\n  Returned: "+result, EMsgType.FAIL);
            close();
            return null;
        }
        else {
            logPrinter.print("Get device list", EMsgType.PASS);
        }
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
                return null;
            }
            if ((descriptor.idVendor() == 0x057E) && descriptor.idProduct() == 0x3000){
                deviceNS = device;
                logPrinter.print("Read file descriptors for USB devices", EMsgType.PASS);
                break;
            }
        }
        // Free device list.

        ////////////////////////////////////////// DEBUG INFORMATION START ///////////////////////////////////////////
        /*
        ConfigDescriptor configDescriptor = new ConfigDescriptor();
                //result = LibUsb.getConfigDescriptor(deviceNS, (byte)0x01, configDescriptor);
        result = LibUsb.getActiveConfigDescriptor(deviceNS, configDescriptor);

        switch (result){
            case 0:
                logPrinter.print("DBG: getActiveConfigDescriptor\n"+configDescriptor.dump(), EMsgType.PASS);
                break;
            case LibUsb.ERROR_NOT_FOUND:
                logPrinter.print("DBG: getActiveConfigDescriptor: ERROR_NOT_FOUND", EMsgType.FAIL);
                break;
            default:
                logPrinter.print("DBG: getActiveConfigDescriptor: "+result, EMsgType.FAIL);
                break;
        }

        LibUsb.freeConfigDescriptor(configDescriptor);
        //*/
        /*
         * So what did we learn?
         * bConfigurationValue     1
         * bInterfaceNumber = 0
         * bEndpointAddress      0x81  EP 1 IN
         * Transfer Type             Bulk
         * bEndpointAddress      0x01  EP 1 OUT
         * Transfer Type             Bulk
         *
         * Or simply run this on your *nix host:
         * # lsusb -v -d 057e:3000
         * */
        ////////////////////////////////////////// DEBUG INFORMATION END /////////////////////////////////////////////

        if (deviceNS != null){
            logPrinter.print("NS in connected USB devices found", EMsgType.PASS);
        }
        else {
            logPrinter.print("NS in connected USB devices not found", EMsgType.FAIL);
            close();
            return null;
        }
        // Handle NS device
        handlerNS = new DeviceHandle();
        result = LibUsb.open(deviceNS, handlerNS);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("Open NS USB device\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            if (result == LibUsb.ERROR_ACCESS)
                logPrinter.print("Double check that you have administrator privileges (you're 'root') or check 'udev' rules set for this user (linux only)!", EMsgType.INFO);
            close();
            return null;
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
            if (usedByKernel == LibUsb.SUCCESS){
                logPrinter.print("Can proceed with libusb driver", EMsgType.PASS);   // we're good
            }
            else if (usedByKernel == 1) {      // used by kernel
                result = LibUsb.detachKernelDriver(handlerNS, DEFAULT_INTERFACE);
                logPrinter.print("Detach kernel required", EMsgType.INFO);
                if (result != 0) {
                    logPrinter.print("Detach kernel\n  Returned: " + UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    close();
                    return null;
                } else
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
            return null;
        }
        */
        // Set configuration (soft reset if needed)
        result = LibUsb.setConfiguration(handlerNS, 1);     // 1 - configuration all we need
        if (result != LibUsb.SUCCESS){
            logPrinter.print("Set active configuration to device\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            close();
            return null;
        }
        else {
            logPrinter.print("Set active configuration to device.", EMsgType.PASS);
        }

        // Claim interface
        result = LibUsb.claimInterface(handlerNS, DEFAULT_INTERFACE);
        if (result != LibUsb.SUCCESS) {
            logPrinter.print("Claim interface\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            close();
            return null;
        }
        else
            logPrinter.print("Claim interface", EMsgType.PASS);

        //--------------------------------------------------------------------------------------------------------------
        if (protocol.equals("TinFoil")) {
            new TinFoil();
        } else {
            new GoldLeaf();
        }

        close();
        logPrinter.print("\tEnd chain", EMsgType.INFO);
        return null;
    }
    /**
     * Tinfoil processing
     * */
    private class TinFoil{
        TinFoil(){

            if (!sendListOfNSP())
                return;

            if (proceedCommands())                              // REPORT SUCCESS
                status = EFileStatus.UPLOADED;     // Don't change status that is already set to FAILED
        }
        /**
         * Send what NSP will be transferred
         * */
        private boolean sendListOfNSP(){
            // Send list of NSP files:
            // Proceed "TUL0"
            if (!writeToUsb("TUL0".getBytes(StandardCharsets.US_ASCII))) {  // new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x76, (byte) 0x30}
                logPrinter.print("TF Send list of files: handshake", EMsgType.FAIL);
                return false;
            }
            else
                logPrinter.print("TF Send list of files: handshake", EMsgType.PASS);
            //Collect file names
            StringBuilder nspListNamesBuilder = new StringBuilder();    // Add every title to one stringBuilder
            for(String nspFileName: nspMap.keySet()) {
                nspListNamesBuilder.append(nspFileName);   // And here we come with java string default encoding (UTF-16)
                nspListNamesBuilder.append('\n');
            }

            byte[] nspListNames = nspListNamesBuilder.toString().getBytes(StandardCharsets.UTF_8);
            ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);         // integer = 4 bytes; BTW Java is stored in big-endian format
            byteBuffer.putInt(nspListNames.length);                                                             // This way we obtain length in int converted to byte array in correct Big-endian order. Trust me.
            byte[] nspListSize = byteBuffer.array();                                                            // TODO: rewind? not sure..
            //byteBuffer.reset();

            // Sending NSP list
            logPrinter.print("TF Send list of files", EMsgType.INFO);
            if (!writeToUsb(nspListSize)) {                                           // size of the list we're going to transfer goes...
                logPrinter.print("  [send list length]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [send list length]", EMsgType.PASS);

            if (!writeToUsb(new byte[8])) {                                           // 8 zero bytes goes...
                logPrinter.print("  [send padding]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [send padding]", EMsgType.PASS);

            if (!writeToUsb(nspListNames)) {                                           // list of the names goes...
                logPrinter.print("  [send list itself]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [send list itself]", EMsgType.PASS);

            return true;
        }
        /**
         * After we sent commands to NS, this chain starts
         * */
        private boolean proceedCommands(){
            logPrinter.print("TF Awaiting for NS commands.", EMsgType.INFO);

            /*  byte[] magic = new byte[4];
                ByteBuffer bb = StandardCharsets.UTF_8.encode("TUC0").rewind().get(magic);
            // Let's rephrase this 'string'  */
            final byte[] magic = new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30};  // eq. 'TUC0' @ UTF-8 (actually ASCII lol, u know what I mean)

            byte[] receivedArray;

            while (true){
                if (isCancelled())     // Check if user interrupted process.
                    return false;
                receivedArray = readFromUsb();
                if (receivedArray == null)
                    return false;             // catches exception

                if (!Arrays.equals(Arrays.copyOfRange(receivedArray, 0,4), magic))      // Bytes from 0 to 3 should contain 'magic' TUC0, so must be verified like this
                    continue;

                // 8th to 12th(explicits) bytes in returned data stands for command ID as unsigned integer (Little-endian). Actually, we have to compare arrays here, but in real world it can't be greater then 0/1/2, thus:
                // BTW also protocol specifies 4th byte to be 0x00 kinda indicating that that this command is valid. But, as you may see, never happens other situation when it's not = 0.
                if (receivedArray[8] == 0x00){                           //0x00 - exit
                    logPrinter.print("TF Received EXIT command. Terminating.", EMsgType.PASS);
                    return true;                     // All interaction with USB device should be ended (expected);
                }
                else if ((receivedArray[8] == 0x01) || (receivedArray[8] == 0x02)){           //0x01 - file range; 0x02 unknown bug on backend side (dirty hack).
                    logPrinter.print("TF Received FILE_RANGE command. Proceeding: [0x0"+receivedArray[8]+"]", EMsgType.PASS);
                /*// We can get in this pocket a length of file name (+32). Why +32? I dunno man.. Do we need this? Definitely not. This app can live without it.
                long receivedSize = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 12,20)).order(ByteOrder.LITTLE_ENDIAN).getLong();
                logsArea.appendText("[V] Received FILE_RANGE command. Size: "+Long.toUnsignedString(receivedSize)+"\n");            // this shit returns string that will be chosen next '+32'. And, BTW, can't be greater then 512
                */
                    if (!fileRangeCmd()) {
                        return false;      // catches exception
                    }
                }
            }
        }
        /**
         * This is what returns requested file (files)
         * Executes multiple times
         * @return 'true' if everything is ok
         *          'false' is error/exception occurs
         * */
        private boolean fileRangeCmd(){
            boolean isProgessBarInitiated = false;

            byte[] receivedArray;
            // Here we take information of what other side wants
            receivedArray = readFromUsb();
            if (receivedArray == null)
                return false;

            // range_offset of the requested file. In the begining it will be 0x10.
            long receivedRangeSize = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 0,8)).order(ByteOrder.LITTLE_ENDIAN).getLong();          // Note - it could be unsigned long. Unfortunately, this app won't support files greater then 8796093022208 Gb
            byte[] receivedRangeSizeRAW = Arrays.copyOfRange(receivedArray, 0,8);                                                               // used (only) when we use sendResponse(). It's just simply.
            long receivedRangeOffset = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 8,16)).order(ByteOrder.LITTLE_ENDIAN).getLong();      // Note - it could be unsigned long. Unfortunately, this app won't support files greater then 8796093022208 Gb
            /* Below, it's REAL NSP file name length that we sent before among others (WITHOUT +32 byes). It can't be greater then... see what is written in the beginning of this code.
            We don't need this since in next pocket we'll get name itself UTF-8 encoded. Could be used to double-checks or something like that.
            long receivedNspNameLen = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 16,24)).order(ByteOrder.LITTLE_ENDIAN).getLong(); */

            // Requesting UTF-8 file name required:
            receivedArray = readFromUsb();
            if (receivedArray == null)
                return false;

            String receivedRequestedNSP = new String(receivedArray, StandardCharsets.UTF_8);
            logPrinter.print("TF Reply to requested file: "+receivedRequestedNSP
                    +"\n  Range Size: "+receivedRangeSize
                    +"\n  Range Offset: "+receivedRangeOffset, EMsgType.INFO);

            // Sending response header
            if (!sendResponse(receivedRangeSizeRAW))   // Get receivedRangeSize in 'RAW' format exactly as it has been received. It's simply.
                return false;

            try {

                BufferedInputStream bufferedInStream = new BufferedInputStream(new FileInputStream(nspMap.get(receivedRequestedNSP)));      // TODO: refactor?
                byte[] bufferCurrent ;//= new byte[1048576];        // eq. Allocate 1mb
                int bufferLength;
                if (bufferedInStream.skip(receivedRangeOffset) != receivedRangeOffset){
                    logPrinter.print("TF Requested skip is out of file size. Nothing to transmit.", EMsgType.FAIL);
                    return false;
                }

                long currentOffset = 0;
                // 'End Offset' equal to receivedRangeSize.
                int readPice = 8388608;                     // = 8Mb

                while (currentOffset < receivedRangeSize){
                    if (isCancelled())     // Check if user interrupted process.
                        return true;
                    if ((currentOffset + readPice) >= receivedRangeSize )
                        readPice = Math.toIntExact(receivedRangeSize - currentOffset);
                    //System.out.println("CO: "+currentOffset+"\t\tEO: "+receivedRangeSize+"\t\tRP: "+readPice);  // TODO: NOTE: DEBUG
                    // updating progress bar (if a lot of data requested) START BLOCK
                    if (isProgessBarInitiated){
                        try {
                            if (currentOffset+readPice == receivedRangeOffset){
                                logPrinter.updateProgress(1.0);
                                isProgessBarInitiated = false;
                            }
                            else
                                logPrinter.updateProgress((currentOffset+readPice)/(receivedRangeSize/100.0) / 100.0);
                        }catch (InterruptedException ie){
                            getException().printStackTrace();               // TODO: Do something with this
                        }
                    }
                    else {
                        if ((readPice == 8388608) && (currentOffset == 0))
                            isProgessBarInitiated = true;
                    }
                    // updating progress bar if needed END BLOCK

                    bufferCurrent = new byte[readPice];                                                         // TODO: not perfect moment, consider refactoring.

                    bufferLength = bufferedInStream.read(bufferCurrent);

                    if (bufferLength != -1){
                        //write to USB
                        if (!writeToUsb(bufferCurrent)) {
                            logPrinter.print("TF Failure during NSP transmission.", EMsgType.FAIL);
                            return false;
                        }
                        currentOffset += readPice;
                    }
                    else {
                        logPrinter.print("TF Reading of stream suddenly ended.", EMsgType.WARNING);
                        return false;
                    }

                }
                bufferedInStream.close();
            } catch (FileNotFoundException fnfe){
                logPrinter.print("TF FileNotFoundException:\n  "+fnfe.getMessage(), EMsgType.FAIL);
                fnfe.printStackTrace();
                return false;
            } catch (IOException ioe){
                logPrinter.print("TF IOException:\n  "+ioe.getMessage(), EMsgType.FAIL);
                ioe.printStackTrace();
                return false;
            } catch (ArithmeticException ae){
                logPrinter.print("TF ArithmeticException (can't cast end offset minus current to 'integer'):\n  "+ae.getMessage(), EMsgType.FAIL);
                ae.printStackTrace();
                return false;
            }

            return true;
        }
        /**
         * Send response header.
         * @return true if everything OK
         *         false if failed
         * */
        private boolean sendResponse(byte[] rangeSize){                                 // This method as separate function itself for application needed as a cookie in the middle of desert.
            logPrinter.print("TF Sending response", EMsgType.INFO);
            if (!writeToUsb(new byte[] { (byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30,    // 'TUC0'
                    (byte) 0x01,                                                // CMD_TYPE_RESPONSE = 1
                    (byte) 0x00, (byte) 0x00, (byte) 0x00,                      // kinda padding. Guys, didn't you want to use integer value for CMD semantic?
                    (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00} )       // Send integer value of '1' in Little-endian format.
            ){
                logPrinter.print("  [1/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [1/3]", EMsgType.PASS);
            if(!writeToUsb(rangeSize)) {                                                          // Send EXACTLY what has been received
                logPrinter.print("  [2/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [2/3]", EMsgType.PASS);
            if(!writeToUsb(new byte[12])) {                                                       // kinda another one padding
                logPrinter.print("  [3/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [3/3]", EMsgType.PASS);
            return true;
        }

    }
    /**
     * GoldLeaf processing
     * */
    private class GoldLeaf{
        //                     CMD                                G     L     U     C     ID    0     0     0
        private final byte[] CMD_ConnectionRequest =  new byte[]{0x47, 0x4c, 0x55, 0x43, 0x00, 0x00, 0x00, 0x00};    // Write-only command
        private final byte[] CMD_NSPName =            new byte[]{0x47, 0x4c, 0x55, 0x43, 0x02, 0x00, 0x00, 0x00};    // Write-only command
        private final byte[] CMD_NSPData =            new byte[]{0x47, 0x4c, 0x55, 0x43, 0x04, 0x00, 0x00, 0x00};    // Write-only command

        private final byte[] CMD_ConnectionResponse = new byte[]{0x47, 0x4c, 0x55, 0x43, 0x01, 0x00, 0x00, 0x00};
        private final byte[] CMD_Start =              new byte[]{0x47, 0x4c, 0x55, 0x43, 0x03, 0x00, 0x00, 0x00};
        private final byte[] CMD_NSPContent =         new byte[]{0x47, 0x4c, 0x55, 0x43, 0x05, 0x00, 0x00, 0x00};
        private final byte[] CMD_NSPTicket =          new byte[]{0x47, 0x4c, 0x55, 0x43, 0x06, 0x00, 0x00, 0x00};
        private final byte[] CMD_Finish =             new byte[]{0x47, 0x4c, 0x55, 0x43, 0x07, 0x00, 0x00, 0x00};

        GoldLeaf(){
            logPrinter.print("===========================================================================", EMsgType.INFO);
            PFSProvider pfsElement = new PFSProvider(nspMap.get(nspMap.keySet().toArray()[0]), logPrinter);
            if (!pfsElement.init()) {
                logPrinter.print("GL File provided have incorrect structure and won't be uploaded", EMsgType.FAIL);
                status = EFileStatus.INCORRECT_FILE_FAILED;
                return;
            }
            logPrinter.print("GL File structure validated and it will be uploaded", EMsgType.PASS);

            if (initGoldLeafProtocol(pfsElement))
                status = EFileStatus.UPLOADED;            // else - no change status that is already set to FAILED
        }
        private boolean initGoldLeafProtocol(PFSProvider pfsElement){
            // Go parse commands
            byte[] readByte;

            // Go connect to GoldLeaf
            if (writeToUsb(CMD_ConnectionRequest))
                logPrinter.print("GL Initiating GoldLeaf connection", EMsgType.PASS);
            else {
                logPrinter.print("GL Initiating GoldLeaf connection", EMsgType.FAIL);
                return false;
            }
            while (true) {
                readByte = readFromUsb();
                if (readByte == null)
                    return false;

                if (Arrays.equals(readByte, CMD_ConnectionResponse)) {
                    if (!handleConnectionResponse(pfsElement))
                        return false;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_Start)) {
                    if (!handleStart(pfsElement))
                        return false;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_NSPContent)) {
                    if (!handleNSPContent(pfsElement, true))
                        return false;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_NSPTicket)) {
                    if (!handleNSPContent(pfsElement, false))
                        return false;
                    else
                        continue;
                }
                if (Arrays.equals(readByte, CMD_Finish)) {
                    logPrinter.print("GL Closing GoldLeaf connection: Transfer successful.", EMsgType.PASS);
                    break;
                }
            }
            return true;
        }
        /**
         * ConnectionResponse command handler
         * */
        private boolean handleConnectionResponse(PFSProvider pfsElement){
            logPrinter.print("GL 'ConnectionResponse' command:", EMsgType.INFO);
            if (!writeToUsb(CMD_NSPName)) {
                logPrinter.print("  [1/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [1/3]", EMsgType.PASS);

            if (!writeToUsb(pfsElement.getBytesNspFileNameLength())) {
                logPrinter.print("  [2/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [2/3]", EMsgType.PASS);

            if (!writeToUsb(pfsElement.getBytesNspFileName())) {
                logPrinter.print("  [3/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [3/3]", EMsgType.PASS);

            return true;
        }
        /**
         * Start command handler
         * */
        private boolean handleStart(PFSProvider pfsElement){
            logPrinter.print("GL Handle 'Start' command:", EMsgType.INFO);
            if (!writeToUsb(CMD_NSPData)) {
                logPrinter.print("  [Send command]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [Send command]", EMsgType.PASS);

            if (!writeToUsb(pfsElement.getBytesCountOfNca())) {
                logPrinter.print("  [Send length]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [Send length]", EMsgType.PASS);

            int ncaCount = pfsElement.getIntCountOfNca();
            logPrinter.print("  [Send information for "+ncaCount+" files]", EMsgType.INFO);
            for (int i = 0; i < ncaCount; i++){
                if (!writeToUsb(pfsElement.getNca(i).getNcaFileNameLength())) {
                    logPrinter.print("    [1/4] File #"+i, EMsgType.FAIL);
                    return false;
                }
                logPrinter.print("    [1/4] File #"+i, EMsgType.PASS);

                if (!writeToUsb(pfsElement.getNca(i).getNcaFileName())) {
                    logPrinter.print("    [2/4] File #"+i, EMsgType.FAIL);
                    return false;
                }
                logPrinter.print("    [2/4] File #"+i, EMsgType.PASS);
                if (!writeToUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getBodySize()+pfsElement.getNca(i).getNcaOffset()).array())) {   // offset. real.
                    logPrinter.print("    [2/4] File #"+i, EMsgType.FAIL);
                    return false;
                }
                logPrinter.print("    [3/4] File #"+i, EMsgType.PASS);
                if (!writeToUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getNca(i).getNcaSize()).array())) {  // size
                    logPrinter.print("    [4/4] File #"+i, EMsgType.FAIL);
                    return false;
                }
                logPrinter.print("    [4/4] File #"+i, EMsgType.PASS);
            }
            return true;
        }
        /**
         * NSPContent command handler
         * isItRawRequest - if True, just ask NS what's needed
         *                - if False, send ticket
         * */
        private boolean handleNSPContent(PFSProvider pfsElement, boolean isItRawRequest){
            int requestedNcaID;
            boolean isProgessBarInitiated = false;
            if (isItRawRequest) {
                logPrinter.print("GL Handle 'Content' command", EMsgType.INFO);
                byte[] readByte = readFromUsb();
                if (readByte == null || readByte.length != 4) {
                    logPrinter.print("  [Read requested ID]", EMsgType.FAIL);
                    return false;
                }
                requestedNcaID = ByteBuffer.wrap(readByte).order(ByteOrder.LITTLE_ENDIAN).getInt();
                logPrinter.print("  [Read requested ID = "+requestedNcaID+" ]", EMsgType.PASS);
            }
            else {
                requestedNcaID = pfsElement.getNcaTicketID();
                logPrinter.print("GL Handle 'Ticket' command (ID = "+requestedNcaID+" )", EMsgType.INFO);
            }

            long realNcaOffset = pfsElement.getNca(requestedNcaID).getNcaOffset()+pfsElement.getBodySize();
            long realNcaSize = pfsElement.getNca(requestedNcaID).getNcaSize();

            long readFrom = 0;

            int readPice = 8388608; // 8mb NOTE: consider switching to 1mb 1048576
            byte[] readBuf;
            File nspFile = nspMap.get(pfsElement.getStringNspFileName());       // wuuuut ( >< )
            try{
                BufferedInputStream bufferedInStream = new BufferedInputStream(new FileInputStream(nspFile));      // TODO: refactor?
                if (bufferedInStream.skip(realNcaOffset) != realNcaOffset)
                    return false;

                while (readFrom < realNcaSize){

                    if (isCancelled())     // Check if user interrupted process.
                        return false;

                    if (realNcaSize - readFrom < readPice)
                        readPice = Math.toIntExact(realNcaSize - readFrom);    // it's safe, I guarantee
                    readBuf = new byte[readPice];
                    if (bufferedInStream.read(readBuf) != readPice)
                        return false;
                    //System.out.println("S: "+readFrom+" T: "+realNcaSize+" P: "+readPice);    //  DEBUG
                    if (!writeToUsb(readBuf))
                        return false;
                    //-----------------------------------------/
                    if (isProgessBarInitiated){
                        try {
                            if (readFrom+readPice == realNcaSize){
                                logPrinter.updateProgress(1.0);
                                isProgessBarInitiated = false;
                            }
                            else
                                logPrinter.updateProgress((readFrom+readPice)/(realNcaSize/100.0) / 100.0);
                        }catch (InterruptedException ie){
                            getException().printStackTrace();               // TODO: Do something with this
                        }
                    }
                    else {
                        if ((readPice == 8388608) && (readFrom == 0))
                            isProgessBarInitiated = true;
                    }
                    //-----------------------------------------/
                    readFrom += readPice;
                }
                bufferedInStream.close();
            }
            catch (IOException ioe){
                logPrinter.print("  Failed to read NCA ID "+requestedNcaID+". IO Exception:\n  "+ioe.getMessage(), EMsgType.FAIL);
                ioe.printStackTrace();
                return false;
            }
            return true;
        }
    }
    //------------------------------------------------------------------------------------------------------------------
    /**
     * Correct exit
     * */
    private void close(){
        // Close handler in the end
        if (handlerNS != null) {
            // Try to release interface
            int result = LibUsb.releaseInterface(handlerNS, DEFAULT_INTERFACE);

            if (result != LibUsb.SUCCESS)
                logPrinter.print("Release interface\n  Returned: "+result+" (sometimes it's not an issue)", EMsgType.WARNING);
            else
                logPrinter.print("Release interface", EMsgType.PASS);

            LibUsb.close(handlerNS);
            logPrinter.print("Requested handler updateAndClose", EMsgType.INFO);
        }
        // Close context in the end
        if (contextNS != null) {
            LibUsb.exit(contextNS);
            logPrinter.print("Requested context updateAndClose", EMsgType.INFO);
        }

        // Report status and close
        logPrinter.update(nspMap, status);
        logPrinter.close();
    }
    /**
     * Sending any byte array to USB device
     * @return 'true' if no issues
     *          'false' if errors happened
     * */
    private boolean writeToUsb(byte[] message){
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(message.length);   //writeBuffer.order() equals BIG_ENDIAN;
        writeBuffer.put(message);
                                                    // DONT EVEN THINK OF USING writeBuffer.rewind();        // well..
        IntBuffer writeBufTransferred = IntBuffer.allocate(1);
        int result;
        result = LibUsb.bulkTransfer(handlerNS, (byte) 0x01, writeBuffer, writeBufTransferred, 0);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint OUT = 0x01
        if (result != LibUsb.SUCCESS){
            logPrinter.print("Data transfer (write) issue\n  Returned: "+ UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            logPrinter.print("Execution stopped", EMsgType.FAIL);
            return false;
        }else {
            if (writeBufTransferred.get() != message.length){
                logPrinter.print("Data transfer (write) issue\n  Requested: "+message.length+"\n  Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                return false;
            }
            else {
                return true;
            }
        }
    }
    /**
     * Reading what USB device responded.
     * @return byte array if data read successful
     *         'null' if read failed
     * */
    private byte[] readFromUsb(){
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(512);//      //readBuffer.order() equals BIG_ENDIAN; DON'T TOUCH. And we will always allocate readBuffer for max-size endpoint supports (512 bytes)
                                                                    // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
        IntBuffer readBufTransferred = IntBuffer.allocate(1);

        int result;
        result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 0);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

        if (result != LibUsb.SUCCESS){
            logPrinter.print("Data transfer (read) issue\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
            logPrinter.print("Execution stopped", EMsgType.FAIL);
            return null;
        } else {
            int trans = readBufTransferred.get();
            byte[] receivedBytes = new byte[trans];
            readBuffer.get(receivedBytes);
            /* DEBUG START----------------------------------------------------------------------------------------------*
            hexDumpUTF8(receivedBytes);
            // DEBUG END----------------------------------------------------------------------------------------------*/
            return receivedBytes;
        }
    }
}