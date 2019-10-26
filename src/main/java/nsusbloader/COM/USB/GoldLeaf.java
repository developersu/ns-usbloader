package nsusbloader.COM.USB;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import nsusbloader.MediatorControl;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.COM.Helpers.NSSplitReader;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * GoldLeaf 0.7 - 0.7.3 processing
 */
class GoldLeaf extends TransferModule {
    private boolean nspFilterForGl;

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
    private NSSplitReader splitReader;

    private HashMap<String, BufferedOutputStream> writeFilesMap;
    private long virtDriveSize;
    private HashMap<String, Long> splitFileSize;

    private boolean isWindows;
    private String homePath;
    // For using in CMD_SelectFile with SPEC:/ prefix
    private File selectedFile;

    GoldLeaf(DeviceHandle handler, LinkedHashMap<String, File> nspMap, Task<Void> task, LogPrinter logPrinter, boolean nspFilter){
        super(handler, nspMap, task, logPrinter);

        final byte CMD_GetDriveCount       = 0x00;
        final byte CMD_GetDriveInfo        = 0x01;
        final byte CMD_StatPath            = 0x02; // proxy done [proxy: in case if folder contains ENG+RUS+UKR file names works incorrect]
        final byte CMD_GetFileCount        = 0x03;
        final byte CMD_GetFile             = 0x04; // proxy done
        final byte CMD_GetDirectoryCount   = 0x05;
        final byte CMD_GetDirectory        = 0x06; // proxy done
        final byte CMD_ReadFile            = 0x07; // no way to do poxy
        final byte CMD_WriteFile           = 0x08; // add predictable behavior
        final byte CMD_Create              = 0x09;
        final byte CMD_Delete              = 0x0a;//10
        final byte CMD_Rename              = 0x0b;//11
        final byte CMD_GetSpecialPathCount = 0x0c;//12  // Special folders count;             simplified usage @ NS-UL
        final byte CMD_GetSpecialPath      = 0x0d;//13  // Information about special folders; simplified usage @ NS-UL
        final byte CMD_SelectFile          = 0x0e;//14
        //final byte CMD_Max                 = 0x0f;//15  // not used @ NS-UL & GT

        final byte[] CMD_GLCI = new byte[]{0x47, 0x4c, 0x43, 0x49};

        this.nspFilterForGl = nspFilter;

        logPrinter.print("============= GoldLeaf =============\n\tVIRT:/ equals files added into the application\n\tHOME:/ equals "
                +System.getProperty("user.home"), EMsgType.INFO);

        // Let's collect file names to the array to simplify our life
        writeFilesMap = new HashMap<>();
        int i = 0;
        nspMapKeySetIndexes = new String[nspMap.size()];
        for (String fileName : nspMap.keySet())
            nspMapKeySetIndexes[i++] = fileName;

        isWindows = System.getProperty("os.name").contains("Windows");

        homePath = System.getProperty("user.home")+File.separator;

        splitFileSize = new HashMap<>();

        // Calculate size of VIRT:/ drive
        for (File nspFile : nspMap.values()){
            if (nspFile.isDirectory()) {
                File[] subFiles = nspFile.listFiles((file, name) -> name.matches("[0-9]{2}"));
                long size = 0;
                for (File subFile : subFiles)   // Validated by parent class
                    size += subFile.length();
                virtDriveSize += size;
                splitFileSize.put(nspFile.getName(), size);
            }
            else
                virtDriveSize += nspFile.length();
        }

        // Go parse commands
        byte[] readByte;
        int someLength1,
                someLength2;
        main_loop:
        while (true) {                          // Till user interrupted process.
            readByte = readGL();

            if (readByte == null)              // Issue @ readFromUsbGL method
                return;

            //RainbowHexDump.hexDumpUTF16LE(readByte);   // DEBUG
            //System.out.println("CHOICE: "+readByte[4]); // DEBUG

            if (Arrays.equals(Arrays.copyOfRange(readByte, 0,4), CMD_GLCI)) {
                switch (readByte[4]) {
                    case CMD_GetDriveCount:
                        if (getDriveCount())
                            break main_loop;
                        break;
                    case CMD_GetDriveInfo:
                        if (getDriveInfo(arrToIntLE(readByte,8)))
                            break main_loop;
                        break;
                    case CMD_GetSpecialPathCount:
                        if (getSpecialPathCount())
                            break main_loop;
                        break;
                    case CMD_GetSpecialPath:
                        if (getSpecialPath(arrToIntLE(readByte,8)))
                            break main_loop;
                        break;
                    case CMD_GetDirectoryCount:
                        someLength1 = arrToIntLE(readByte, 8) * 2; // Since GL 0.7
                        if (getDirectoryOrFileCount(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE), true))
                            break main_loop;
                        break;
                    case CMD_GetFileCount:
                        someLength1 = arrToIntLE(readByte, 8) * 2; // Since GL 0.7
                        if (getDirectoryOrFileCount(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE), false))
                            break main_loop;
                        break;
                    case CMD_GetDirectory:
                        someLength1 = arrToIntLE(readByte, 8) * 2; // Since GL 0.7
                        if (getDirectory(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE), arrToIntLE(readByte, someLength1+12)))
                            break main_loop;
                        break;
                    case CMD_GetFile:
                        someLength1 = arrToIntLE(readByte, 8) * 2; // Since GL 0.7
                        if (getFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE), arrToIntLE(readByte, someLength1+12)))
                            break main_loop;
                        break;
                    case CMD_StatPath:
                        someLength1 = arrToIntLE(readByte, 8) * 2; // Since GL 0.7
                        if (statPath(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE)))
                            break main_loop;
                        break;
                    case CMD_Rename:
                        someLength1 = arrToIntLE(readByte, 12) * 2; // Since GL 0.7
                        someLength2 = arrToIntLE(readByte, 16+someLength1) * 2; // Since GL 0.7
                        if (rename(new String(readByte, 16, someLength1, StandardCharsets.UTF_16LE),
                                new String(readByte, 16+someLength1+4, someLength2, StandardCharsets.UTF_16LE)))
                            break main_loop;
                        break;
                    case CMD_Delete:
                        someLength1 = arrToIntLE(readByte, 12) * 2; // Since GL 0.7
                        if (delete(new String(readByte, 16, someLength1, StandardCharsets.UTF_16LE)))
                            break main_loop;
                        break;
                    case CMD_Create:
                        someLength1 = arrToIntLE(readByte, 12) * 2; // Since GL 0.7
                        if (create(new String(readByte, 16, someLength1, StandardCharsets.UTF_16LE), readByte[8]))
                            break main_loop;
                        break;
                    case CMD_ReadFile:
                        someLength1 = arrToIntLE(readByte, 8) * 2; // Since GL 0.7
                        if (readFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE),
                                arrToLongLE(readByte, 12+someLength1),
                                arrToLongLE(readByte, 12+someLength1+8)))
                            break main_loop;
                        break;
                    case CMD_WriteFile:
                        someLength1 = arrToIntLE(readByte, 8) * 2; // Since GL 0.7
                        //if (writeFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE), arrToLongLE(readByte, 12+someLength1)))
                        if (writeFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_16LE)))
                            break main_loop;
                        break;
                    case CMD_SelectFile:
                        if (selectFile())
                            break main_loop;
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
                }catch (IOException | NullPointerException ignored){}
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
            catch (IOException | NullPointerException ignored){}
            try{
                splitReader.close();
            }
            catch (IOException | NullPointerException ignored){}
            openReadFileNameAndPath = null;
            randAccessFile = null;
            splitReader = null;
        }
    }
    /**
     * Handle GetDriveCount
     * @return true if failed
     *         false if everything is ok
     */
    private boolean getDriveCount(){
        // Let's declare 2 drives
        byte[] drivesCnt = intToArrLE(2);   //2
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
        long totalSizeLong;

        // 0 == VIRTUAL DRIVE
        if (driveNo == 0){
            driveLabel = "Virtual".getBytes(StandardCharsets.UTF_16LE);
            driveLabelLen = intToArrLE(driveLabel.length / 2); // since GL 0.7
            driveLetter = "VIRT".getBytes(StandardCharsets.UTF_16LE);      // TODO: Consider moving to class field declaration
            driveLetterLen = intToArrLE(driveLetter.length / 2);// since GL 0.7
            totalFreeSpace = new byte[4];
            totalSizeLong = virtDriveSize;
            totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);  // Dirty hack; now for GL!
        }
        else { //1 == User home dir
            driveLabel = "Home".getBytes(StandardCharsets.UTF_16LE);
            driveLabelLen = intToArrLE(driveLabel.length / 2);// since GL 0.7
            driveLetter = "HOME".getBytes(StandardCharsets.UTF_16LE);
            driveLetterLen = intToArrLE(driveLetter.length / 2);// since GL 0.7
            File userHomeDir = new File(System.getProperty("user.home"));
            long totalFreeSpaceLong = userHomeDir.getFreeSpace();
            totalFreeSpace = Arrays.copyOfRange(longToArrLE(totalFreeSpaceLong), 0, 4);  // Dirty hack; now for GL!;
            totalSizeLong = userHomeDir.getTotalSpace();
            totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);  // Dirty hack; now for GL!

            //System.out.println("totalSize: "+totalSizeLong+"totalFreeSpace: "+totalFreeSpaceLong);
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
        byte[] specialPathCnt = intToArrLE(0);
        // Write count of special paths
        if (writeGL_PASS(specialPathCnt)) {
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
    private boolean getSpecialPath(int specialPathNo){
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
                    return (dir.isDirectory() && ! dir.isHidden());
                });
            }
            else {
                if (nspFilterForGl){
                    filesOrDirs = pathDir.list((current, name) -> {
                        File dir = new File(current, name);
                        return (! dir.isDirectory() && name.toLowerCase().endsWith(".nsp"));
                    });
                }
                else {
                    filesOrDirs = pathDir.list((current, name) -> {
                        File dir = new File(current, name);
                        return (! dir.isDirectory() && (! dir.isHidden()));
                    });
                }
            }
            // If somehow there are no folders, let's say 0;
            if (filesOrDirs == null){
                if (writeGL_PASS()) {
                    logPrinter.print("GL Handle 'GetDirectoryOrFileCount' command", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
            // Sorting is mandatory TODO: NOTE: Proxy tail
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
        else if (path.startsWith("SPEC:/")){
            if (isGetDirectoryCount){       // If dir request then 0 dirs
                if (writeGL_PASS()) {
                    logPrinter.print("GL Handle 'GetDirectoryCount' command", EMsgType.FAIL);
                    return true;
                }
            }
            else if (selectedFile != null){ // Else it's file request, if we have selected then we will report 1.
                if (writeGL_PASS(intToArrLE(1))) {
                    logPrinter.print("GL Handle 'GetFileCount' command Count = 1", EMsgType.FAIL);
                    return true;
                }
            }
            else
                return writeGL_FAIL("GL Handle 'GetDirectoryOrFileCount' command [unknown drive request] (file) - "+path);
        }
        else { // If requested drive is not VIRT and not HOME then reply error
            return writeGL_FAIL("GL Handle 'GetDirectoryOrFileCount' command [unknown drive request] "+(isGetDirectoryCount?"(dir) - ":"(file) - ")+path);
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
                byte[] dirNameBytes = recentDirs[subDirNo].getBytes(StandardCharsets.UTF_16LE);

                command.add(intToArrLE(dirNameBytes.length / 2)); // Since GL 0.7
                command.add(dirNameBytes);
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
                    byte[] dirBytesName = recentDirs[subDirNo].getBytes(StandardCharsets.UTF_16LE);
                    command.add(intToArrLE(dirBytesName.length / 2)); // Since GL 0.7
                    command.add(dirBytesName);
                }
                else
                    return writeGL_FAIL("GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
            }
            //if (proxyForGL) // TODO: NOTE: PROXY TAILS
            //    return proxyGetDirFile(true);

            if (writeGL_PASS(command)) {
                logPrinter.print("GL Handle 'GetDirectory' command.", EMsgType.FAIL);
                return true;
            }
            return false;
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
                byte[] fileNameBytes = recentFiles[subDirNo].getBytes(StandardCharsets.UTF_16LE);

                command.add(intToArrLE(fileNameBytes.length / 2)); //Since GL 0.7
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
                    byte[] fileNameBytes = recentFiles[subDirNo].getBytes(StandardCharsets.UTF_16LE);
                    command.add(intToArrLE(fileNameBytes.length / 2)); //Since GL 0.7
                    command.add(fileNameBytes);
                }
                else
                    return writeGL_FAIL("GL Handle 'GetFile' command [doesn't exist or not a folder]");
            }
            //if (proxyForGL) // TODO: NOTE: PROXY TAILS
            //    return proxyGetDirFile(false);
            if (writeGL_PASS(command)) {
                logPrinter.print("GL Handle 'GetFile' command.", EMsgType.FAIL);
                return true;
            }
            return false;
        }
        else if (dirName.equals("VIRT:/")){
            if (nspMap.size() != 0){    // therefore nspMapKeySetIndexes also != 0
                byte[] fileNameBytes = nspMapKeySetIndexes[subDirNo].getBytes(StandardCharsets.UTF_16LE);
                command.add(intToArrLE(fileNameBytes.length / 2)); // since GL 0.7
                command.add(fileNameBytes);
                if (writeGL_PASS(command)) {
                    logPrinter.print("GL Handle 'GetFile' command.", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
        }
        else if (dirName.equals("SPEC:/")){
            if (selectedFile != null){
                byte[] fileNameBytes = selectedFile.getName().getBytes(StandardCharsets.UTF_16LE);
                command.add(intToArrLE(fileNameBytes.length / 2)); // since GL 0.7
                command.add(fileNameBytes);
                if (writeGL_PASS(command)) {
                    logPrinter.print("GL Handle 'GetFile' command.", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
        }
        //  any other cases
        return writeGL_FAIL("GL Handle 'GetFile' command for virtual drive [no folders support?]");
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
            //if (proxyForGL)                     // TODO:NOTE PROXY TAILS
            //    return proxyStatPath(filePath); // dirty name

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
                if (nspMap.get(filePath).isDirectory()) {
                    command.add(longToArrLE(splitFileSize.get(filePath)));    // YES, THIS IS LONG!;
                }
                else
                    command.add(longToArrLE(nspMap.get(filePath).length()));    // YES, THIS IS LONG!

                if (writeGL_PASS(command)) {
                    logPrinter.print("GL Handle 'StatPath' command.", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
        }
        else if (filePath.startsWith("SPEC:/")){
            //System.out.println(filePath);
            filePath = filePath.replaceFirst("SPEC:/","");
            if (selectedFile.getName().equals(filePath)){
                command.add(GL_OBJ_TYPE_FILE);
                command.add(longToArrLE(selectedFile.length()));
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
        //System.out.println("readFile "+fileName+" "+offset+" "+size+"\n");
        if (fileName.startsWith("VIRT:/")){
            // Let's find out which file requested
            String fNamePath = nspMap.get(fileName.substring(6)).getAbsolutePath();     // NOTE: 6 = "VIRT:/".length
            // If we don't have this file opened, let's open it
            if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(fNamePath))) {
                // Try close what opened
                if (openReadFileNameAndPath != null){
                    try{
                        randAccessFile.close();
                    }catch (Exception ignored){}
                    try{
                        splitReader.close();
                    }catch (Exception ignored){}
                }
                // Open what has to be opened
                try{
                    File tempFile = nspMap.get(fileName.substring(6));
                    if (tempFile.isDirectory()) {
                        randAccessFile = null;
                        splitReader = new NSSplitReader(tempFile, 0);
                    }
                    else {
                        splitReader = null;
                        randAccessFile = new RandomAccessFile(tempFile, "r");
                    }
                    openReadFileNameAndPath = fNamePath;
                }
                catch (IOException | NullPointerException ioe){
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
                    }catch (IOException | NullPointerException ignored){}
                }
                // Open what has to be opened
                try{
                    randAccessFile = new RandomAccessFile(fileName, "r");
                    openReadFileNameAndPath = fileName;
                }catch (IOException | NullPointerException ioe){
                    return writeGL_FAIL("GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
                }
            }
        }
        //----------------------- Actual transfer chain ------------------------
        try{
            if (randAccessFile == null){
                splitReader.seek(offset);
                byte[] chunk = new byte[(int)size]; // WTF MAN?
                // Let's find out how much bytes we got
                int bytesRead = splitReader.read(chunk);
                // Let's check that we read expected size
                if (bytesRead != (int)size)
                    return writeGL_FAIL("GL Handle 'ReadFile' command [CMD]\n" +
                            "    At offset: "+offset+"\n    Requested: "+size+"\n    Received:  "+bytesRead);
                // Let's tell as a command about our result.
                if (writeGL_PASS(longToArrLE(size))) {
                    logPrinter.print("GL Handle 'ReadFile' command [CMD]", EMsgType.FAIL);
                    return true;
                }
                // Let's bypass bytes we read total
                if (writeToUsb(chunk)) {
                    logPrinter.print("GL Handle 'ReadFile' command", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
            else {
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
                // Let's bypass bytes we read total
                if (writeToUsb(chunk)) {
                    logPrinter.print("GL Handle 'ReadFile' command", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
        }
        catch (Exception ioe){
            try{
                randAccessFile.close();
            }
            catch (NullPointerException ignored){}
            catch (IOException ioe_){
                logPrinter.print("GL Handle 'ReadFile' command: unable to close: "+openReadFileNameAndPath+"\n\t"+ioe_.getMessage(), EMsgType.WARNING);
            }
            try{
                splitReader.close();
            }
            catch (NullPointerException ignored){}
            catch (IOException ioe_){
                logPrinter.print("GL Handle 'ReadFile' command: unable to close: "+openReadFileNameAndPath+"\n\t"+ioe_.getMessage(), EMsgType.WARNING);
            }
            openReadFileNameAndPath = null;
            randAccessFile = null;
            splitReader = null;
            return writeGL_FAIL("GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
        }
    }
    /**
     * Handle 'WriteFile'
     * @param fileName full path including new file name in the end
     *
     * @return true if failed
     *          false if everything is ok
     * */
    //@param size requested size
    //private boolean writeFile(String fileName, long size) {
    private boolean writeFile(String fileName) {
        if (fileName.startsWith("VIRT:/")){
            return writeGL_FAIL("GL Handle 'WriteFile' command [not supported for virtual drive]");
        }
        else {
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

    /**
     * Handle 'SelectFile'
     * @return true if failed
     *          false if everything is ok
     * */
    private boolean selectFile(){
        File selectedFile = CompletableFuture.supplyAsync(() -> {
            FileChooser fChooser = new FileChooser();
            fChooser.setTitle(MediatorControl.getInstance().getContoller().getResourceBundle().getString("btn_OpenFile")); // TODO: FIX BAD IMPLEMENTATION
            fChooser.setInitialDirectory(new File(System.getProperty("user.home")));                                            // TODO: Consider fixing; not a prio.
            fChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("*", "*"));
            return fChooser.showOpenDialog(null);    // Leave as is for now.
        }, Platform::runLater).join();

        if (selectedFile != null){
            List<byte[]> command = new LinkedList<>();
            byte[] selectedFileNameBytes = ("SPEC:/"+selectedFile.getName()).getBytes(StandardCharsets.UTF_16LE);
            command.add(intToArrLE(selectedFileNameBytes.length / 2)); // since GL 0.7
            command.add(selectedFileNameBytes);
            if (writeGL_PASS(command)) {
                logPrinter.print("GL Handle 'SelectFile' command", EMsgType.FAIL);
                this.selectedFile = null;
                return true;
            }
            this.selectedFile = selectedFile;
            return false;
        }
        // Nothing selected; Report failure.
        this.selectedFile = null;
        return writeGL_FAIL("GL Handle 'SelectFile' command: Nothing selected");
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

    //------------------------------------------------------------------------------------------------------------------

    /*----------------------------------------------------*/
    /*           GL READ/WRITE USB SPECIFIC               */
    /*----------------------------------------------------*/

    private byte[] readGL(){
        ByteBuffer readBuffer;
        readBuffer = ByteBuffer.allocateDirect(4096);    // GL really?

        IntBuffer readBufTransferred = IntBuffer.allocate(1);

        int result;

        while (! task.isCancelled()) {
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    int trans = readBufTransferred.get();
                    byte[] receivedBytes = new byte[trans];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    closeOpenedReadFilesGl();       // Could be a problem if GL glitches and slow down process. Or if user has extra-slow SD card. TODO: refactor
                    continue;
                default:
                    logPrinter.print("GL Data transfer issue [read]\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    logPrinter.print("GL Execution stopped", EMsgType.FAIL);
                    return null;
            }
        }
        logPrinter.print("GL Execution interrupted", EMsgType.INFO);
        return null;
    }
    private byte[] readGL_file(){
        ByteBuffer readBuffer;
        readBuffer = ByteBuffer.allocateDirect(8388608); // Just don't ask..

        IntBuffer readBufTransferred = IntBuffer.allocate(1);

        int result;

        while (! task.isCancelled()) {
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    int trans = readBufTransferred.get();
                    byte[] receivedBytes = new byte[trans];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    logPrinter.print("GL Data transfer issue [read]\n  Returned: "+UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    logPrinter.print("GL Execution stopped", EMsgType.FAIL);
                    return null;
            }
        }
        logPrinter.print("GL Execution interrupted", EMsgType.INFO);
        return null;
    }
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
    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private boolean writeToUsb(byte[] message){
        ByteBuffer writeBuffer = ByteBuffer.allocateDirect(message.length);   //writeBuffer.order() equals BIG_ENDIAN;
        writeBuffer.put(message);                                             // Don't do writeBuffer.rewind();
        IntBuffer writeBufTransferred = IntBuffer.allocate(1);
        int result;

        while (! task.isCancelled()) {
            result = LibUsb.bulkTransfer(handlerNS, (byte) 0x01, writeBuffer, writeBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint OUT = 0x01

            switch (result){
                case LibUsb.SUCCESS:
                    if (writeBufTransferred.get() == message.length)
                        return false;
                    else {
                        logPrinter.print("GL Data transfer issue [write]\n  Requested: "+message.length+"\n  Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                        return true;
                    }
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    logPrinter.print("GL Data transfer issue [write]\n  Returned: "+ UsbErrorCodes.getErrCode(result), EMsgType.FAIL);
                    logPrinter.print("GL Execution stopped", EMsgType.FAIL);
                    return true;
            }
        }
        logPrinter.print("GL Execution interrupted", EMsgType.INFO);
        return true;
    }

    /*----------------------------------------------------*/
    /*                  GL EXPERIMENTAL PART              */
    /*                  (left for better times)           */
    /*----------------------------------------------------*/
        /*
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
                    byte[] name = dirName.getBytes(StandardCharsets.UTF_16LE);
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
                    byte[] name = dirName.getBytes(StandardCharsets.UTF_16LE);
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
        */
}
