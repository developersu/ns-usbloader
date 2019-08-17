package nsusbloader.USB;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.RainbowHexDump;
import org.usb4java.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

import java.util.*;
// TODO: add filter option to show only NSP files
public class UsbCommunications extends Task<Void> {
    private final int DEFAULT_INTERFACE = 0;

    private LogPrinter logPrinter;
    private EFileStatus status = EFileStatus.FAILED;

    private LinkedHashMap<String, File> nspMap;

    private Context contextNS;
    private DeviceHandle handlerNS;

    private String protocol;

    private boolean nspFilterForGl;
    private boolean proxyForGL = false;

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
                logPrinter.print("Double check that you have administrator privileges (you're 'root') or check 'udev' rules set for this user (linux only)!\n\n" +
                        "Steps to set 'udev' rules:\n" +
                        "root # vim /etc/udev/rules.d/99-NS.rules\n" +
                        "SUBSYSTEM==\"usb\", ATTRS{idVendor}==\"057e\", ATTRS{idProduct}==\"3000\", GROUP=\"plugdev\"\n" +
                        "root # udevadm control --reload-rules && udevadm trigger\n", EMsgType.INFO);
            // Let's make a bit dirty workaround since such shit happened
            if (contextNS != null) {
                LibUsb.exit(contextNS);
                logPrinter.print("Requested context close", EMsgType.INFO);
            }

            // Report status and close
            logPrinter.update(nspMap, status);
            logPrinter.print("\tEnd chain", EMsgType.INFO);
            logPrinter.close();
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
            if (writeToUsb("TUL0".getBytes(StandardCharsets.US_ASCII))) {  // new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x76, (byte) 0x30}
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
            if (writeToUsb(nspListSize)) {                                           // size of the list we're going to transfer goes...
                logPrinter.print("  [send list length]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [send list length]", EMsgType.PASS);

            if (writeToUsb(new byte[8])) {                                           // 8 zero bytes goes...
                logPrinter.print("  [send padding]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [send padding]", EMsgType.PASS);

            if (writeToUsb(nspListNames)) {                                           // list of the names goes...
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
                    //-----------------------------------------/
                    try {
                        logPrinter.updateProgress((currentOffset+readPice)/(receivedRangeSize/100.0) / 100.0);
                    }catch (InterruptedException ie){
                        getException().printStackTrace();               // TODO: Do something with this
                    }
                    //-----------------------------------------/
                    bufferCurrent = new byte[readPice];                                                         // TODO: not perfect moment, consider refactoring.

                    if (bufferedInStream.read(bufferCurrent) != readPice) {                                      // changed since @ v0.3.2
                        logPrinter.print("TF Reading of stream suddenly ended.", EMsgType.WARNING);
                        return false;
                    }
                    //write to USB
                    if (writeToUsb(bufferCurrent)) {
                        logPrinter.print("TF Failure during NSP transmission.", EMsgType.FAIL);
                        return false;
                    }
                    currentOffset += readPice;
                }
                bufferedInStream.close();
                //-----------------------------------------/
                try{
                    logPrinter.updateProgress(1.0);
                }
                catch (InterruptedException ie){
                    getException().printStackTrace();               // TODO: Do something with this
                }
                //-----------------------------------------/
            } catch (FileNotFoundException fnfe){
                logPrinter.print("TF FileNotFoundException:\n  "+fnfe.getMessage(), EMsgType.FAIL);
                fnfe.printStackTrace();
                return false;
            } catch (IOException ioe){
                logPrinter.print("TF IOException:\n  "+ioe.getMessage(), EMsgType.FAIL);
                ioe.printStackTrace();
                return false;
            } catch (ArithmeticException ae){
                logPrinter.print("TF ArithmeticException (can't cast 'offset end' - 'offsets current' to 'integer'):\n  "+ae.getMessage(), EMsgType.FAIL);
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
            if (writeToUsb(new byte[] { (byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30,    // 'TUC0'
                    (byte) 0x01,                                                // CMD_TYPE_RESPONSE = 1
                    (byte) 0x00, (byte) 0x00, (byte) 0x00,                      // kinda padding. Guys, didn't you want to use integer value for CMD semantic?
                    (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00} )       // Send integer value of '1' in Little-endian format.
            ){
                logPrinter.print("  [1/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [1/3]", EMsgType.PASS);
            if(writeToUsb(rangeSize)) {                                                          // Send EXACTLY what has been received
                logPrinter.print("  [2/3]", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("  [2/3]", EMsgType.PASS);
            if(writeToUsb(new byte[12])) {                                                       // kinda another one padding
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
        //                     CMD
        private final byte[] CMD_GLCO_SUCCESS = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, 0x00, 0x00};         // used @ writeToUsb_GLCMD
        private final byte[] CMD_GLCO_FAILURE = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x64, (byte) 0xcb, 0x00, 0x00};  // used @ writeToUsb_GLCMD

        // System.out.println((356 & 0x1FF) | ((1 + 100) & 0x1FFF) << 9); // 52068 // 0x00 0x00 0xCB 0x64
        private final byte[] GL_OBJ_TYPE_FILE = new byte[]{0x01, 0x00, 0x00, 0x00};
        private final byte[] GL_OBJ_TYPE_DIR  = new byte[]{0x02, 0x00, 0x00, 0x00};

        private String recentPath = null;
        private String[] recentDirs = null;
        private String[] recentFiles = null;

        private String[] nspMapKeySetIndexes;

        private String openReadFileNameAndPath;
        private RandomAccessFile randAccessFile;

        private HashMap<String, BufferedOutputStream> writeFilesMap;

        private boolean isWindows;
        private String homePath;

        GoldLeaf(){
            final byte CMD_GetDriveCount       = 0x00;
            final byte CMD_GetDriveInfo        = 0x01;
            final byte CMD_StatPath            = 0x02; // TODO: proxy done [proxy: in case if folder contains ENG+RUS+UKR file names works incorrect]
            final byte CMD_GetFileCount        = 0x03;
            final byte CMD_GetFile             = 0x04; // TODO: proxy done
            final byte CMD_GetDirectoryCount   = 0x05;
            final byte CMD_GetDirectory        = 0x06; // TODO: proxy done
            final byte CMD_ReadFile            = 0x07; // TODO: no way to do poxy
            final byte CMD_WriteFile           = 0x08; // TODO: add predictable behavior
            final byte CMD_Create              = 0x09;
            final byte CMD_Delete              = 0x0a;//10
            final byte CMD_Rename              = 0x0b;//11
            final byte CMD_GetSpecialPathCount = 0x0c;//12  // Special folders count;             simplified usage @ NS-UL
            final byte CMD_GetSpecialPath      = 0x0d;//13  // Information about special folders; simplified usage @ NS-UL
            final byte CMD_SelectFile          = 0x0e;//14  // WTF? Ignoring for now. For future: execute another thread within this(?) context for FileChooser
            final byte CMD_Max                 = 0x0f;//15  // not used @ NS-UL & GT

            final byte[] CMD_GLCI = new byte[]{0x47, 0x4c, 0x43, 0x49};

            logPrinter.print("============= GoldLeaf =============\n\tVIRT:/ equals files added into the application\n\tHOME:/ equals "
                    +System.getProperty("user.home"), EMsgType.INFO);
            // Let's collect file names to the array to simplify our life
            writeFilesMap = new HashMap<>();
            int i = 0;
            nspMapKeySetIndexes = new String[nspMap.size()];
            for (String fileName : nspMap.keySet())
                nspMapKeySetIndexes[i++] = fileName;

            status = EFileStatus.UNKNOWN;

            isWindows = System.getProperty("os.name").contains("Windows");

            homePath = System.getProperty("user.home")+File.separator;

            // Go parse commands
            byte[] readByte;
            int someLength;
            while (! isCancelled()) {                          // Till user interrupted process.
                readByte = readGL();

                if (readByte == null)              // Issue @ readFromUsbGL method
                    return;
                else if(readByte.length < 4096) {           // Just timeout of waiting for reply; continue loop
                    closeOpenedReadFilesGl();
                    continue;
                }

                //RainbowHexDump.hexDumpUTF8(readByte);   // TODO: DEBUG
                //System.out.println("CHOICE: "+readByte[4]); // TODO: DEBUG

                if (Arrays.equals(Arrays.copyOfRange(readByte, 0,4), CMD_GLCI)) {
                    switch (readByte[4]) {
                        case CMD_GetDriveCount:
                            if (getDriveCount())
                                return;
                            break;
                        case CMD_GetDriveInfo:
                            if (getDriveInfo(arrToIntLE(readByte,8)))
                                return;
                            break;
                        case CMD_GetSpecialPathCount:
                            if (getSpecialPathCount())
                                return;
                            break;
                        case CMD_GetSpecialPath:
                            if (getSpecialPath(arrToIntLE(readByte,8)))
                                return;
                            break;
                        case CMD_GetDirectoryCount:
                            if (getDirectoryOrFileCount(new String(readByte, 12, arrToIntLE(readByte, 8), StandardCharsets.UTF_8), true))
                                return;
                            break;
                        case CMD_GetFileCount:
                            if (getDirectoryOrFileCount(new String(readByte, 12, arrToIntLE(readByte, 8), StandardCharsets.UTF_8), false))
                                return;
                            break;
                        case CMD_GetDirectory:
                            someLength = arrToIntLE(readByte, 8);
                            if (getDirectory(new String(readByte, 12, someLength, StandardCharsets.UTF_8), arrToIntLE(readByte, someLength+12)))
                                return;
                            break;
                        case CMD_GetFile:
                            someLength = arrToIntLE(readByte, 8);
                            if (getFile(new String(readByte, 12, someLength, StandardCharsets.UTF_8), arrToIntLE(readByte, someLength+12)))
                                return;
                            break;
                        case CMD_StatPath:
                            if (statPath(new String(readByte, 12, arrToIntLE(readByte, 8), StandardCharsets.UTF_8)))
                                return;
                            break;
                        case CMD_Rename:
                            someLength = arrToIntLE(readByte, 12);
                            if (rename(new String(readByte, 16, someLength, StandardCharsets.UTF_8),
                                    new String(readByte, 16+someLength+4, arrToIntLE(readByte, 16+someLength), StandardCharsets.UTF_8)))
                                return;
                            break;
                        case CMD_Delete:
                            if (delete(new String(readByte, 16, arrToIntLE(readByte, 12), StandardCharsets.UTF_8)))
                                return;
                            break;
                        case CMD_Create:
                            if (create(new String(readByte, 16, arrToIntLE(readByte, 12), StandardCharsets.UTF_8), readByte[8]))
                                return;
                            break;
                        case CMD_ReadFile:
                            someLength = arrToIntLE(readByte, 8);
                            if (readFile(new String(readByte, 12, someLength, StandardCharsets.UTF_8),
                                    arrToLongLE(readByte, 12+someLength),
                                    arrToLongLE(readByte, 12+someLength+8)))
                                return;
                            break;
                        case CMD_WriteFile:
                            someLength = arrToIntLE(readByte, 8);
                            if (writeFile(new String(readByte, 12, someLength, StandardCharsets.UTF_8),
                                    arrToLongLE(readByte, 12+someLength)))
                                return;
                            break;
                        default:
                            writeGL_FAIL("GL Unknown command: "+readByte[4]+" [it's a very bad sign]");
                    }
                }
            }
            // Close (and flush) all opened streams.
            if (writeFilesMap.size() != 0){
                for (BufferedOutputStream fBufOutStream: writeFilesMap.values()){
                    try{
                        fBufOutStream.close();
                    }catch (IOException ignored){}
                }
            }
            closeOpenedReadFilesGl();
        }

        /**
         * Close files opened for read/write
         */
        private void closeOpenedReadFilesGl(){
            if (openReadFileNameAndPath != null){     // Perfect time to close our opened files
                try{
                    randAccessFile.close();
                }
                catch (IOException ignored){}
                openReadFileNameAndPath = null;
                randAccessFile = null;
            }
        }
        /**
         * Handle GetDriveCount
         * @return true if failed
         *         false if everything is ok
         */
        private boolean getDriveCount(){
            // Let's declare 2 drives
            byte[] drivesCnt = intToArrLE(2);//2
            // Write count of drives
            if (writeGL_PASS(drivesCnt)) {
                logPrinter.print("GL Handle 'ListDrives' command", EMsgType.FAIL);
                return true;
            }
            return false;
        }
        /**
         * Handle GetDriveInfo
         * @return true if failed
         *         false if everything is ok
         */
        private boolean getDriveInfo(int driveNo){
            if (driveNo < 0 || driveNo > 1){
                return writeGL_FAIL("GL Handle 'GetDriveInfo' command [no such drive]");
            }

            byte[] driveLabel,
                    driveLabelLen,
                    driveLetter,
                    driveLetterLen,
                    totalFreeSpace,
                    totalSize;
            long totalSizeLong = 0;

            // 0 == VIRTUAL DRIVE
            if (driveNo == 0){
                driveLabel = "Virtual".getBytes(StandardCharsets.UTF_8);
                driveLabelLen = intToArrLE(driveLabel.length);
                driveLetter = "VIRT".getBytes(StandardCharsets.UTF_8);      // TODO: Consider moving to class field declaration
                driveLetterLen = intToArrLE(driveLetter.length);
                totalFreeSpace = new byte[4];
                for (File nspFile : nspMap.values()){
                    totalSizeLong += nspFile.length();
                }
                totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);  // Dirty hack; now for GL!
            }
            else { //1 == User home dir
                driveLabel = "Home".getBytes(StandardCharsets.UTF_8);
                driveLabelLen = intToArrLE(driveLabel.length);
                driveLetter = "HOME".getBytes(StandardCharsets.UTF_8);
                driveLetterLen = intToArrLE(driveLetter.length);
                File userHomeDir = new File(System.getProperty("user.home"));
                long totalFreeSpaceLong = userHomeDir.getFreeSpace();
                totalFreeSpace = Arrays.copyOfRange(longToArrLE(totalFreeSpaceLong), 0, 4);  // Dirty hack; now for GL!;
                totalSizeLong = userHomeDir.getTotalSpace();
                totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);  // Dirty hack; now for GL!
            }

            List<byte[]> command = new LinkedList<>();
            command.add(driveLabelLen);
            command.add(driveLabel);
            command.add(driveLetterLen);
            command.add(driveLetter);
            command.add(totalFreeSpace);
            command.add(totalSize);

            if (writeGL_PASS(command)) {
                logPrinter.print("GL Handle 'GetDriveInfo' command", EMsgType.FAIL);
                return true;
            }

            return false;
        }
        /**
         * Handle SpecialPathCount
         *  @return true if failed
         *          false if everything is ok
         * */
        private boolean getSpecialPathCount(){
            // Let's declare nothing =)
            byte[] virtDrivesCnt = intToArrLE(0);
            // Write count of special paths
            if (writeGL_PASS(virtDrivesCnt)) {
                logPrinter.print("GL Handle 'SpecialPathCount' command", EMsgType.FAIL);
                return true;
            }
            return false;
        }
        /**
         * Handle SpecialPath
         *  @return true if failed
         *          false if everything is ok
         * */
        private boolean getSpecialPath(int virtDriveNo){
            return writeGL_FAIL("GL Handle 'SpecialPath' command [not supported]");
        }
        /**
         * Handle GetDirectoryCount & GetFileCount
         *  @return true if failed
         *          false if everything is ok
         * */
        private boolean getDirectoryOrFileCount(String path, boolean isGetDirectoryCount) {
            if (path.equals("VIRT:/")) {
                if (isGetDirectoryCount){
                    if (writeGL_PASS()) {
                        logPrinter.print("GL Handle 'GetDirectoryCount' command", EMsgType.FAIL);
                        return true;
                    }
                }
                else {
                    if (writeGL_PASS(intToArrLE(nspMap.size()))) {
                        logPrinter.print("GL Handle 'GetFileCount' command Count = "+nspMap.size(), EMsgType.FAIL);
                        return true;
                    }
                }
            }
            else if (path.startsWith("HOME:/")){
                // Let's make it normal path
                path = updateHomePath(path);
                // Open it
                File pathDir = new File(path);

                // Make sure it's exists and it's path
                if ((! pathDir.exists() ) || (! pathDir.isDirectory()) )
                    return writeGL_FAIL("GL Handle 'GetDirectoryOrFileCount' command [doesn't exist or not a folder]");
                // Save recent dir path
                this.recentPath = path;
                String[] filesOrDirs;
                // Now collecting every folder or file inside
                if (isGetDirectoryCount){
                    filesOrDirs = pathDir.list((current, name) -> {
                        File dir = new File(current, name);
                        return (dir.isDirectory() && ! dir.isHidden());      // TODO: FIX FOR WIN ?
                    });
                }
                else {
                    if (nspFilterForGl){
                        filesOrDirs = pathDir.list((current, name) -> {
                            File dir = new File(current, name);
                            return (! dir.isDirectory() && name.toLowerCase().endsWith(".nsp"));      // TODO: FIX FOR WIN ?
                        });
                    }
                    else {
                        filesOrDirs = pathDir.list((current, name) -> {
                            File dir = new File(current, name);
                            return (! dir.isDirectory() && (! dir.isHidden()));      // TODO: MOVE TO PROD
                        });
                    }
                }
                // If somehow there are no folders, let's say 0;
                if (filesOrDirs == null){
                    if (writeGL_PASS()) {
                        logPrinter.print("GL Handle 'GetDirectoryOrFileCount' command", EMsgType.FAIL);
                        return true;
                    }
                    //logPrinter.print("GL Handle 'GetDirectoryOrFileCount' command", EMsgType.PASS);
                    return false;
                }
                // Sorting is mandatory
                Arrays.sort(filesOrDirs, String.CASE_INSENSITIVE_ORDER);

                if (isGetDirectoryCount)
                    this.recentDirs = filesOrDirs;
                else
                    this.recentFiles = filesOrDirs;
                // Otherwise, let's tell how may folders are in there
                if (writeGL_PASS(intToArrLE(filesOrDirs.length))) {
                    logPrinter.print("GL Handle 'GetDirectoryOrFileCount' command", EMsgType.FAIL);
                    return true;
                }
            }
            // If requested drive is not VIRT and not HOME then reply error
            else {
                return writeGL_FAIL("GL Handle 'GetDirectoryOrFileCount' command [unknown drive request]");
            }
            return false;
        }
        /**
         * Handle GetDirectory
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean getDirectory(String dirName, int subDirNo){
            if (dirName.startsWith("HOME:/")) {
                dirName = updateHomePath(dirName);

                List<byte[]> command = new LinkedList<>();

                if (dirName.equals(recentPath) && recentDirs != null && recentDirs.length != 0){
                    command.add(intToArrLE(recentDirs[subDirNo].getBytes(StandardCharsets.UTF_8).length));
                    command.add(recentDirs[subDirNo].getBytes(StandardCharsets.UTF_8));
                }
                else {
                    File pathDir = new File(dirName);
                    // Make sure it's exists and it's path
                    if ((! pathDir.exists() ) || (! pathDir.isDirectory()) )
                        return writeGL_FAIL("GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
                    this.recentPath = dirName;
                    // Now collecting every folder or file inside
                    this.recentDirs = pathDir.list((current, name) -> {
                        File dir = new File(current, name);
                        return (dir.isDirectory() && ! dir.isHidden());      // TODO: FIX FOR WIN ?
                    });
                    // Check that we still don't have any fuckups
                    if (this.recentDirs != null && this.recentDirs.length > subDirNo){
                        Arrays.sort(recentFiles, String.CASE_INSENSITIVE_ORDER);
                        byte[] dirBytesName = recentDirs[subDirNo].getBytes(StandardCharsets.UTF_8);
                        command.add(intToArrLE(dirBytesName.length));
                        command.add(dirBytesName);
                    }
                    else
                        return writeGL_FAIL("GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
                }
                if (proxyForGL)
                    return proxyGetDirFile(true);
                else {
                    if (writeGL_PASS(command)) {
                        logPrinter.print("GL Handle 'GetDirectory' command.", EMsgType.FAIL);
                        return true;
                    }
                    return false;
                }
            }
            // VIRT:// and any other
            return writeGL_FAIL("GL Handle 'GetDirectory' command for virtual drive [no folders support]");
        }
        /**
         * Handle GetFile
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean getFile(String dirName, int subDirNo){
            List<byte[]> command = new LinkedList<>();

            if (dirName.startsWith("HOME:/")) {
                dirName = updateHomePath(dirName);

                if (dirName.equals(recentPath) && recentFiles != null && recentFiles.length != 0){
                    byte[] fileNameBytes = recentFiles[subDirNo].getBytes(StandardCharsets.UTF_8);

                    command.add(intToArrLE(fileNameBytes.length));
                    command.add(fileNameBytes);
                }
                else {
                    File pathDir = new File(dirName);
                    // Make sure it's exists and it's path
                    if ((! pathDir.exists() ) || (! pathDir.isDirectory()) )
                        writeGL_FAIL("GL Handle 'GetFile' command [doesn't exist or not a folder]");
                    this.recentPath = dirName;
                    // Now collecting every folder or file inside
                    if (nspFilterForGl){
                        this.recentFiles = pathDir.list((current, name) -> {
                            File dir = new File(current, name);
                            return (! dir.isDirectory() && name.toLowerCase().endsWith(".nsp"));      // TODO: FIX FOR WIN ? MOVE TO PROD
                        });
                    }
                    else {
                        this.recentFiles = pathDir.list((current, name) -> {
                            File dir = new File(current, name);
                            return (! dir.isDirectory() && (! dir.isHidden()));    // TODO: FIX FOR WIN
                        });
                    }
                    // Check that we still don't have any fuckups
                    if (this.recentFiles != null && this.recentFiles.length > subDirNo){
                        Arrays.sort(recentFiles, String.CASE_INSENSITIVE_ORDER);        // TODO: NOTE: array sorting is an overhead for using poxy loops
                        byte[] fileNameBytes = recentFiles[subDirNo].getBytes(StandardCharsets.UTF_8);
                        command.add(intToArrLE(fileNameBytes.length));
                        command.add(fileNameBytes);
                    }
                    else
                        return writeGL_FAIL("GL Handle 'GetFile' command [doesn't exist or not a folder]");
                }
                if (proxyForGL)
                    return proxyGetDirFile(false);
                else {
                    if (writeGL_PASS(command)) {
                        logPrinter.print("GL Handle 'GetFile' command.", EMsgType.FAIL);
                        return true;
                    }
                    return false;
                }
            }
            else if (dirName.equals("VIRT:/")){
                if (nspMap.size() != 0){    // therefore nspMapKeySetIndexes also != 0
                    byte[] fileNameBytes = nspMapKeySetIndexes[subDirNo].getBytes(StandardCharsets.UTF_8);
                    command.add(intToArrLE(fileNameBytes.length));
                    command.add(fileNameBytes);
                    if (writeGL_PASS(command)) {
                        logPrinter.print("GL Handle 'GetFile' command.", EMsgType.FAIL);
                        return true;
                    }
                    return false;
                }
            }
            //  any other cases
            return writeGL_FAIL("GL Handle 'GetFile' command for virtual drive [no folders support]");
        }
        /**
         * Handle StatPath
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean statPath(String filePath){
            List<byte[]> command = new LinkedList<>();

            if (filePath.startsWith("HOME:/")){
                filePath = updateHomePath(filePath);
                if (proxyForGL)
                    return proxyStatPath(filePath); // dirty name

                File fileDirElement = new File(filePath);
                if (fileDirElement.exists()){
                    if (fileDirElement.isDirectory())
                        command.add(GL_OBJ_TYPE_DIR);
                    else {
                        command.add(GL_OBJ_TYPE_FILE);
                        command.add(longToArrLE(fileDirElement.length()));
                    }
                    if (writeGL_PASS(command)) {
                        logPrinter.print("GL Handle 'StatPath' command.", EMsgType.FAIL);
                        return true;
                    }
                    return false;
                }
            }
            else if (filePath.startsWith("VIRT:/")) {
                filePath = filePath.replaceFirst("VIRT:/", "");
                if (nspMap.containsKey(filePath)){
                    command.add(GL_OBJ_TYPE_FILE);                              // THIS IS INT
                    command.add(longToArrLE(nspMap.get(filePath).length()));    // YES, THIS IS LONG!
                    if (writeGL_PASS(command)) {
                        logPrinter.print("GL Handle 'StatPath' command.", EMsgType.FAIL);
                        return true;
                    }
                    return false;
                }
            }
            return writeGL_FAIL("GL Handle 'StatPath' command [no such folder] - "+filePath);
        }
        /**
         * Handle 'Rename' that is actually 'mv'
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean rename(String fileName, String newFileName){
            if (fileName.startsWith("HOME:/")){
                // This shit takes too much time to explain, but such behaviour won't let GL to fail
                this.recentPath = null;
                this.recentFiles = null;
                this.recentDirs = null;
                fileName = updateHomePath(fileName);
                newFileName = updateHomePath(newFileName);

                File currentFile = new File(fileName);
                File newFile = new File(newFileName);
                if (! newFile.exists()){        // Else, report error
                    try {
                        if (currentFile.renameTo(newFile)){
                            if (writeGL_PASS()) {
                                logPrinter.print("GL Handle 'Rename' command.", EMsgType.FAIL);
                                return true;
                            }
                            return false;
                        }
                    }
                    catch (SecurityException ignored){} // Ah, leave it
                }
            }
            // For VIRT:/ and others we don't serve requests
            return writeGL_FAIL("GL Handle 'Rename' command [not supported for virtual drive/wrong drive/file with such name already exists/read-only directory]");
        }
        /**
         * Handle 'Delete'
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean delete(String fileName) {
            if (fileName.startsWith("HOME:/")) {
                fileName = updateHomePath(fileName);

                File fileToDel = new File(fileName);
                try {
                    if (fileToDel.delete()){
                        if (writeGL_PASS()) {
                            logPrinter.print("GL Handle 'Rename' command.", EMsgType.FAIL);
                            return true;
                        }
                        return false;
                    }
                }
                catch (SecurityException ignored){} // Ah, leave it
            }
            // For VIRT:/ and others we don't serve requests
            return writeGL_FAIL("GL Handle 'Delete' command [not supported for virtual drive/wrong drive/read-only directory]");
        }
        /**
         * Handle 'Create'
         * @param type 1 for file
         *             2 for folder
         * @param fileName full path including new file name in the end
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean create(String fileName, byte type) {
            if (fileName.startsWith("HOME:/")) {
                fileName = updateHomePath(fileName);
                File fileToCreate = new File(fileName);
                boolean result = false;
                if (type == 1){
                    try {
                        result = fileToCreate.createNewFile();
                    }
                    catch (SecurityException | IOException ignored){}
                }
                else if (type == 2){
                    try {
                        result = fileToCreate.mkdir();
                    }
                    catch (SecurityException ignored){}
                }
                if (result){
                    if (writeGL_PASS()) {
                        logPrinter.print("GL Handle 'Create' command.", EMsgType.FAIL);
                        return true;
                    }
                    //logPrinter.print("GL Handle 'Create' command.", EMsgType.PASS);
                    return false;
                }
            }
            // For VIRT:/ and others we don't serve requests
            return writeGL_FAIL("GL Handle 'Delete' command [not supported for virtual drive/wrong drive/read-only directory]");
        }

        /**
         * Handle 'ReadFile'
         * @param fileName full path including new file name in the end
         * @param offset requested offset
         * @param size requested size
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean readFile(String fileName, long offset, long size) {
            if (fileName.startsWith("VIRT:/")){
                // Let's find out which file requested
                String fNamePath = nspMap.get(fileName.substring(6)).getAbsolutePath();     // NOTE: 6 = "VIRT:/".length
                // If we don't have this file opened, let's open it
                if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(fNamePath))) {
                    // Try close what opened
                    if (openReadFileNameAndPath != null){
                        try{
                            randAccessFile.close();
                        }catch (IOException ignored){}
                    }
                    // Open what has to be opened
                    try{
                        randAccessFile = new RandomAccessFile(nspMap.get(fileName.substring(6)), "r");
                        openReadFileNameAndPath = fNamePath;
                    }
                    catch (IOException ioe){
                        return writeGL_FAIL("GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
                    }
                }
            }
            else {
                // Let's find out which file requested
                fileName = updateHomePath(fileName);
                // If we don't have this file opened, let's open it
                if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(fileName))) {
                    // Try close what opened
                    if (openReadFileNameAndPath != null){
                        try{
                            randAccessFile.close();
                        }catch (IOException ignored){}
                    }
                    // Open what has to be opened
                    try{
                        randAccessFile = new RandomAccessFile(fileName, "r");
                        openReadFileNameAndPath = fileName;
                    }catch (IOException ioe){
                        return writeGL_FAIL("GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
                    }
                }
            }
            //----------------------- Actual transfer chain ------------------------
            try{
                randAccessFile.seek(offset);
                byte[] chunk = new byte[(int)size]; // WTF MAN?
                // Let's find out how much bytes we got
                int bytesRead = randAccessFile.read(chunk);
                // Let's check that we read expected size
                if (bytesRead != (int)size)
                    return writeGL_FAIL("GL Handle 'ReadFile' command [CMD] Requested = "+size+" Read from file = "+bytesRead);
                // Let's tell as a command about our result.
                if (writeGL_PASS(longToArrLE(size))) {
                    logPrinter.print("GL Handle 'ReadFile' command [CMD]", EMsgType.FAIL);
                    return true;
                }
                if (bytesRead > 8388608){
                    // Let's bypass bytes we read part 1
                    if (writeToUsb(Arrays.copyOfRange(chunk, 0, 8388608))) {
                        logPrinter.print("GL Handle 'ReadFile' command [Data 1/2]", EMsgType.FAIL);
                        return true;
                    }
                    // Let's bypass bytes we read part 2
                    if (writeToUsb(Arrays.copyOfRange(chunk, 8388608, chunk.length))) {
                        logPrinter.print("GL Handle 'ReadFile' command [Data 2/2]", EMsgType.FAIL);
                        return true;
                    }
                    return false;
                }
                // Let's bypass bytes we read total
                if (writeToUsb(chunk)) {
                    logPrinter.print("GL Handle 'ReadFile' command [Data 1/1]", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
            catch (IOException ioe){
                try{
                    randAccessFile.close();
                }
                catch (IOException ioee){
                    logPrinter.print("GL Handle 'ReadFile' command: unable to close: "+openReadFileNameAndPath+"\n\t"+ioee.getMessage(), EMsgType.WARNING);
                }
                openReadFileNameAndPath = null;
                randAccessFile = null;
                return writeGL_FAIL("GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
            }
        }
        /**
         * Handle 'WriteFile'
         * @param fileName full path including new file name in the end
         * @param size requested size
         * @return true if failed
         *          false if everything is ok
         * */
        private boolean writeFile(String fileName, long size) {
            if (fileName.startsWith("VIRT:/")){
                return writeGL_FAIL("GL Handle 'WriteFile' command [not supported for virtual drive]");
            }
            else {
                if ((int)size > 8388608){
                    logPrinter.print("GL Handle 'WriteFile' command [Files greater than 8mb are not supported]", EMsgType.FAIL);
                    return true;
                }

                fileName = updateHomePath(fileName);
                // Check if we didn't see this (or any) file during this session
                if (writeFilesMap.size() == 0 || (! writeFilesMap.containsKey(fileName))){
                    // Open what we have to open
                    File writeFile = new File(fileName);
                    // If this file exists GL will take care
                    // Otherwise, let's add it
                    try{
                        BufferedOutputStream writeFileBufOutStream = new BufferedOutputStream(new FileOutputStream(writeFile, true));
                        writeFilesMap.put(fileName, writeFileBufOutStream);
                    } catch (IOException ioe){
                        return writeGL_FAIL("GL Handle 'WriteFile' command [IOException]\n\t"+ioe.getMessage());
                    }
                }
                // Now we have stream
                BufferedOutputStream myStream = writeFilesMap.get(fileName);

                byte[] transferredData;

                if ((transferredData = readGL_file()) == null){
                    logPrinter.print("GL Handle 'WriteFile' command [1/1]", EMsgType.FAIL);
                    return true;
                }
                try{
                    myStream.write(transferredData, 0, transferredData.length);
                }
                catch (IOException ioe){
                    return writeGL_FAIL("GL Handle 'WriteFile' command [1/1]\n\t"+ioe.getMessage());
                }
                // Report we're good
                if (writeGL_PASS()) {
                    logPrinter.print("GL Handle 'WriteFile' command", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
        }

        /*----------------------------------------------------*/
        /*           GL READ/WRITE USB SPECIFIC               */
        /*----------------------------------------------------*/

        /**
         * Write new command. Shitty implementation.
         * */
        private boolean writeGL_PASS(byte[] message){
            ByteBuffer writeBuffer = ByteBuffer.allocate(4096);
            writeBuffer.put(CMD_GLCO_SUCCESS);
            writeBuffer.put(message);
            return writeToUsb(writeBuffer.array());
        }
        private boolean writeGL_PASS(){
            return writeToUsb(Arrays.copyOf(CMD_GLCO_SUCCESS, 4096));
        }
        private boolean writeGL_PASS(List<byte[]> messages){
            ByteBuffer writeBuffer = ByteBuffer.allocate(4096);
            writeBuffer.put(CMD_GLCO_SUCCESS);
            for (byte[] arr : messages)
                writeBuffer.put(arr);
            return writeToUsb(writeBuffer.array());
        }

        private boolean writeGL_FAIL(String reportToUImsg){
            if (writeToUsb(Arrays.copyOf(CMD_GLCO_FAILURE, 4096))){
                logPrinter.print(reportToUImsg, EMsgType.WARNING);
                return true;
            }
            logPrinter.print(reportToUImsg, EMsgType.FAIL);
            return false;
        }
        private byte[] readGL(){
            ByteBuffer readBuffer = ByteBuffer.allocateDirect(4096);    // GL really?
            // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
            IntBuffer readBufTransferred = IntBuffer.allocate(1);

            int result;
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 5000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            if (result != LibUsb.SUCCESS && result != LibUsb.ERROR_TIMEOUT){
                logPrinter.print("GL Data transfer (read) issue\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                logPrinter.print("Execution stopped", EMsgType.FAIL);
                return null;
            }
            else {
                int trans = readBufTransferred.get();
                byte[] receivedBytes = new byte[trans];
                readBuffer.get(receivedBytes);
                return receivedBytes;
            }
        }
        private byte[] readGL_file(){
            ByteBuffer readBuffer = ByteBuffer.allocateDirect(8388608); // Just don't ask..
            // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
            IntBuffer readBufTransferred = IntBuffer.allocate(1);   // Works for 8mb

            int result;
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 0);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            if (result != LibUsb.SUCCESS && result != LibUsb.ERROR_TIMEOUT){
                logPrinter.print("GL Data transfer (read) issue\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                logPrinter.print("Execution stopped", EMsgType.FAIL);
                return null;
            }
            else {
                int trans = readBufTransferred.get();
                byte[] receivedBytes = new byte[trans];
                readBuffer.get(receivedBytes);
                return receivedBytes;
            }
        }
        /*----------------------------------------------------*/
        /*                     GL HELPERS                     */
        /*----------------------------------------------------*/
        /**
         * Convert path received from GL to normal
         */
        private String updateHomePath(String glPath){
            if (isWindows)
                glPath = glPath.replaceAll("/", "\\\\");
            glPath = homePath+glPath.substring(6);    // Do not use replaceAll since it will consider \ as special directive
            return glPath;
        }
        /**
         * Convert INT (Little endian) value to bytes-array representation
         * */
        private byte[] intToArrLE(int value){
            ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.putInt(value);
            return byteBuffer.array();
        }
        /**
         * Convert LONG (Little endian) value to bytes-array representation
         * */
        private byte[] longToArrLE(long value){
            ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            byteBuffer.putLong(value);
            return byteBuffer.array();
        }
        /**
         * Convert bytes-array to INT value (Little endian)
         * */
        private int arrToIntLE(byte[] byteArrayWithInt, int intStartPosition){
            return ByteBuffer.wrap(byteArrayWithInt).order(ByteOrder.LITTLE_ENDIAN).getInt(intStartPosition);
        }
        /**
         * Convert bytes-array to LONG value (Little endian)
         * */
        private long arrToLongLE(byte[] byteArrayWithLong, int intStartPosition){
            return ByteBuffer.wrap(byteArrayWithLong).order(ByteOrder.LITTLE_ENDIAN).getLong(intStartPosition);
        }

        /*----------------------------------------------------*/
        /*                     GL EXPERIMENTAL PART           */
        /*----------------------------------------------------*/

        private boolean proxyStatPath(String path) {
            ByteBuffer writeBuffer = ByteBuffer.allocate(4096);
            List<byte[]> fileBytesSize = new LinkedList<>();
            if ((recentDirs.length == 0) && (recentFiles.length == 0)) {
                return writeGL_FAIL("proxyStatPath");
            }
            if (recentDirs.length > 0){
                writeBuffer.put(CMD_GLCO_SUCCESS);
                writeBuffer.put(GL_OBJ_TYPE_DIR);
                byte[] resultingDir = writeBuffer.array();
                writeToUsb(resultingDir);
                for (int i = 1; i < recentDirs.length; i++) {
                    readGL();
                    writeToUsb(resultingDir);
                }
            }
            if (recentFiles.length > 0){
                path = path.replaceAll(recentDirs[0]+"$", "");  // Remove the name from path
                for (String fileName : recentFiles){
                    File f = new File(path+fileName);
                    fileBytesSize.add(longToArrLE(f.length()));
                }
                writeBuffer.clear();
                for (int i = 0; i < recentFiles.length; i++){
                    readGL();
                    writeBuffer.clear();
                    writeBuffer.put(CMD_GLCO_SUCCESS);
                    writeBuffer.put(GL_OBJ_TYPE_FILE);
                    writeBuffer.put(fileBytesSize.get(i));
                    writeToUsb(writeBuffer.array());
                }
            }
            return false;
        }

        private boolean proxyGetDirFile(boolean forDirs){
            ByteBuffer writeBuffer = ByteBuffer.allocate(4096);
            List<byte[]> dirBytesNameSize = new LinkedList<>();
            List<byte[]> dirBytesName = new LinkedList<>();
            if (forDirs) {
                if (recentDirs.length <= 0)
                    return writeGL_FAIL("proxyGetDirFile");
                for (String dirName : recentDirs) {
                    byte[] name = dirName.getBytes(StandardCharsets.UTF_8);
                    dirBytesNameSize.add(intToArrLE(name.length));
                    dirBytesName.add(name);
                }
                writeBuffer.put(CMD_GLCO_SUCCESS);
                writeBuffer.put(dirBytesNameSize.get(0));
                writeBuffer.put(dirBytesName.get(0));
                writeToUsb(writeBuffer.array());
                writeBuffer.clear();
                for (int i = 1; i < recentDirs.length; i++){
                    readGL();
                    writeBuffer.put(CMD_GLCO_SUCCESS);
                    writeBuffer.put(dirBytesNameSize.get(i));
                    writeBuffer.put(dirBytesName.get(i));
                    writeToUsb(writeBuffer.array());
                    writeBuffer.clear();
                }
            }
            else {
                if (recentDirs.length <= 0)
                    return writeGL_FAIL("proxyGetDirFile");
                for (String dirName : recentFiles){
                    byte[] name = dirName.getBytes(StandardCharsets.UTF_8);
                    dirBytesNameSize.add(intToArrLE(name.length));
                    dirBytesName.add(name);
                }
                writeBuffer.put(CMD_GLCO_SUCCESS);
                writeBuffer.put(dirBytesNameSize.get(0));
                writeBuffer.put(dirBytesName.get(0));
                writeToUsb(writeBuffer.array());
                writeBuffer.clear();
                for (int i = 1; i < recentFiles.length; i++){
                    readGL();
                    writeBuffer.put(CMD_GLCO_SUCCESS);
                    writeBuffer.put(dirBytesNameSize.get(i));
                    writeBuffer.put(dirBytesName.get(i));
                    writeToUsb(writeBuffer.array());
                    writeBuffer.clear();
                }
            }
            return false;
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
            logPrinter.print("Requested handler close", EMsgType.INFO);
        }
        // Close context in the end
        if (contextNS != null) {
            LibUsb.exit(contextNS);
            logPrinter.print("Requested context close", EMsgType.INFO);
        }

        // Report status and close
        logPrinter.update(nspMap, status);
        logPrinter.print("\tEnd chain", EMsgType.INFO);
        logPrinter.close();
    }
    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
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
            return true;
        }
        else {
            if (writeBufTransferred.get() != message.length){
                logPrinter.print("Data transfer (write) issue\n  Requested: "+message.length+"\n  Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                return true;
            }
            else {
                return false;
            }
        }
    }
    /**
     * Reading what USB device responded.
     * @return byte array if data read successful
     *         'null' if read failed
     * */
    private byte[] readFromUsb(){
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(512);
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