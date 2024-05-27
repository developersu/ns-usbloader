/*
    Copyright 2019-2024 Dmitry Isaenko

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

import javafx.application.Platform;
import javafx.stage.FileChooser;
import nsusbloader.MediatorControl;
import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.com.helpers.NSSplitReader;
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
 * GoldLeaf 0.8 processing
 */
class GoldLeaf_010 extends TransferModule {
    private boolean nspFilterForGl;

    //                     CMD
    private final byte[] CMD_GLCO_SUCCESS = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, 0x00, 0x00};         // used @ writeToUsb_GLCMD
    private final byte[] CMD_GLCO_FAILURE = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, (byte) 0xAD, (byte) 0xDE};  // used @ writeToUsb_GLCMD TODO: TEST

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

    private final boolean isWindows;
    private final String homePath;
    // For using in CMD_SelectFile with SPEC:/ prefix
    private File selectedFile;

    private final CancellableRunnable task;

    GoldLeaf_010(DeviceHandle handler,
                 LinkedHashMap<String, File> nspMap,
                 CancellableRunnable task,
                 ILogPrinter logPrinter,
                 boolean nspFilter)
    {
        super(handler, nspMap, task, logPrinter);

        this.task = task;

        final byte CMD_GetDriveCount       = 1;
        final byte CMD_GetDriveInfo        = 2;
        final byte CMD_StatPath            = 3;
        final byte CMD_GetFileCount        = 4;
        final byte CMD_GetFile             = 5;
        final byte CMD_GetDirectoryCount   = 6;
        final byte CMD_GetDirectory        = 7;
        final byte CMD_StartFile           = 8; // 1 -open read RAF; 2 open write RAF; 3 open write RAF and seek to EOF (???).
        final byte CMD_ReadFile            = 9;
        final byte CMD_WriteFile           = 10;
        final byte CMD_EndFile             = 11; // 1 - closed read RAF; 2 close write RAF.
        final byte CMD_Create              = 12;
        final byte CMD_Delete              = 13;
        final byte CMD_Rename              = 14;
        final byte CMD_GetSpecialPathCount = 15;
        final byte CMD_GetSpecialPath      = 16;
        final byte CMD_SelectFile          = 17;

        final byte[] CMD_GLCI = new byte[]{0x47, 0x4c, 0x43, 0x49};

        this.nspFilterForGl = nspFilter;

        print("=========== GoldLeaf v0.10 ===========\n\t" +
                "VIRT:/ equals files added into the application\n\t" +
                "HOME:/ equals "
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
                        someLength1 = arrToIntLE(readByte, 8);
                        if (getDirectoryOrFileCount(new String(readByte, 12, someLength1, StandardCharsets.UTF_8), true))
                            break main_loop;
                        break;
                    case CMD_GetFileCount:
                        someLength1 = arrToIntLE(readByte, 8);
                        if (getDirectoryOrFileCount(new String(readByte, 12, someLength1, StandardCharsets.UTF_8), false))
                            break main_loop;
                        break;
                    case CMD_GetDirectory:
                        someLength1 = arrToIntLE(readByte, 8);
                        if (getDirectory(new String(readByte, 12, someLength1, StandardCharsets.UTF_8), arrToIntLE(readByte, someLength1+12)))
                            break main_loop;
                        break;
                    case CMD_GetFile:
                        someLength1 = arrToIntLE(readByte, 8);
                        if (getFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_8), arrToIntLE(readByte, someLength1+12)))
                            break main_loop;
                        break;
                    case CMD_StatPath:
                        someLength1 = arrToIntLE(readByte, 8);
                        if (statPath(new String(readByte, 12, someLength1, StandardCharsets.UTF_8)))
                            break main_loop;
                        break;
                    case CMD_Rename:
                        someLength1 = arrToIntLE(readByte, 12);
                        someLength2 = arrToIntLE(readByte, 16+someLength1);
                        if (rename(new String(readByte, 16, someLength1, StandardCharsets.UTF_8),
                                new String(readByte, 16+someLength1+4, someLength2, StandardCharsets.UTF_8)))
                            break main_loop;
                        break;
                    case CMD_Delete:
                        someLength1 = arrToIntLE(readByte, 12);
                        if (delete(new String(readByte, 16, someLength1, StandardCharsets.UTF_8)))
                            break main_loop;
                        break;
                    case CMD_Create:
                        someLength1 = arrToIntLE(readByte, 12);
                        if (create(new String(readByte, 16, someLength1, StandardCharsets.UTF_8), readByte[8]))
                            break main_loop;
                        break;
                    case CMD_ReadFile:
                        someLength1 = arrToIntLE(readByte, 8);
                        if (readFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_8),
                                arrToLongLE(readByte, 12+someLength1),
                                arrToLongLE(readByte, 12+someLength1+8)))
                            break main_loop;
                        break;
                    case CMD_WriteFile:
                        someLength1 = arrToIntLE(readByte, 8);
                        //if (writeFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_8), arrToLongLE(readByte, 12+someLength1)))
                        if (writeFile(new String(readByte, 12, someLength1, StandardCharsets.UTF_8)))
                            break main_loop;
                        break;
                    case CMD_SelectFile:
                        if (selectFile())
                            break main_loop;
                        break;
                    case CMD_StartFile:
                    case CMD_EndFile:
                        if (startOrEndFile())
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
     * Handle StartFile & EndFile
     * NOTE: It's something internal for GL and used somehow by GL-PC-app, so just ignore this, at least for v0.8.
     * @return true if failed
     *         false if everything is ok
     * */
    private boolean startOrEndFile(){
        if (writeGL_PASS()){
            print("GL Handle 'StartFile' command", EMsgType.FAIL);
            return true;
        }
        return false;
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
            print("GL Handle 'ListDrives' command", EMsgType.FAIL);
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
            driveLabel = "Virtual".getBytes(StandardCharsets.UTF_8);
            driveLabelLen = intToArrLE(driveLabel.length);
            driveLetter = "VIRT".getBytes(StandardCharsets.UTF_8);      // TODO: Consider moving to class field declaration
            driveLetterLen = intToArrLE(driveLetter.length);// since GL 0.7
            totalFreeSpace = new byte[4];
            totalSizeLong = virtDriveSize;
        }
        else { //1 == User home dir
            driveLabel = "Home".getBytes(StandardCharsets.UTF_8);
            driveLabelLen = intToArrLE(driveLabel.length);// since GL 0.7
            driveLetter = "HOME".getBytes(StandardCharsets.UTF_8);
            driveLetterLen = intToArrLE(driveLetter.length);// since GL 0.7
            File userHomeDir = new File(System.getProperty("user.home"));
            long totalFreeSpaceLong = userHomeDir.getFreeSpace();
            totalFreeSpace = Arrays.copyOfRange(longToArrLE(totalFreeSpaceLong), 0, 4);;
            totalSizeLong = userHomeDir.getTotalSpace();
        }
        totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);

        List<byte[]> command = new LinkedList<>();
        command.add(driveLabelLen);
        command.add(driveLabel);
        command.add(driveLetterLen);
        command.add(driveLetter);
        command.add(totalFreeSpace);
        command.add(totalSize);

        if (writeGL_PASS(command)) {
            print("GL Handle 'GetDriveInfo' command", EMsgType.FAIL);
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
            print("GL Handle 'SpecialPathCount' command", EMsgType.FAIL);
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
                    print("GL Handle 'GetDirectoryCount' command", EMsgType.FAIL);
                    return true;
                }
            }
            else {
                if (writeGL_PASS(intToArrLE(nspMap.size()))) {
                    print("GL Handle 'GetFileCount' command Count = "+nspMap.size(), EMsgType.FAIL);
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
                    print("GL Handle 'GetDirectoryOrFileCount' command", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
            // Sorting is mandatory NOTE: Proxy tail
            Arrays.sort(filesOrDirs, String.CASE_INSENSITIVE_ORDER);

            if (isGetDirectoryCount)
                this.recentDirs = filesOrDirs;
            else
                this.recentFiles = filesOrDirs;
            // Otherwise, let's tell how may folders are in there
            if (writeGL_PASS(intToArrLE(filesOrDirs.length))) {
                print("GL Handle 'GetDirectoryOrFileCount' command", EMsgType.FAIL);
                return true;
            }
        }
        else if (path.startsWith("SPEC:/")){
            if (isGetDirectoryCount){       // If dir request then 0 dirs
                if (writeGL_PASS()) {
                    print("GL Handle 'GetDirectoryCount' command", EMsgType.FAIL);
                    return true;
                }
            }
            else if (selectedFile != null){ // Else it's file request, if we have selected then we will report 1.
                if (writeGL_PASS(intToArrLE(1))) {
                    print("GL Handle 'GetFileCount' command Count = 1", EMsgType.FAIL);
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
                byte[] dirNameBytes = recentDirs[subDirNo].getBytes(StandardCharsets.UTF_8);

                command.add(intToArrLE(dirNameBytes.length));
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
                    byte[] dirBytesName = recentDirs[subDirNo].getBytes(StandardCharsets.UTF_8);
                    command.add(intToArrLE(dirBytesName.length));
                    command.add(dirBytesName);
                }
                else
                    return writeGL_FAIL("GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
            }

            if (writeGL_PASS(command)) {
                print("GL Handle 'GetDirectory' command.", EMsgType.FAIL);
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
                byte[] fileNameBytes = recentFiles[subDirNo].getBytes(StandardCharsets.UTF_8);

                command.add(intToArrLE(fileNameBytes.length)); //Since GL 0.7
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
                    command.add(intToArrLE(fileNameBytes.length)); //Since GL 0.7
                    command.add(fileNameBytes);
                }
                else
                    return writeGL_FAIL("GL Handle 'GetFile' command [doesn't exist or not a folder]");
            }

            if (writeGL_PASS(command)) {
                print("GL Handle 'GetFile' command.", EMsgType.FAIL);
                return true;
            }
            return false;
        }
        else if (dirName.equals("VIRT:/")){
            if (nspMap.size() != 0){    // therefore nspMapKeySetIndexes also != 0
                byte[] fileNameBytes = nspMapKeySetIndexes[subDirNo].getBytes(StandardCharsets.UTF_8);
                command.add(intToArrLE(fileNameBytes.length));
                command.add(fileNameBytes);
                if (writeGL_PASS(command)) {
                    print("GL Handle 'GetFile' command.", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
        }
        else if (dirName.equals("SPEC:/")){
            if (selectedFile != null){
                byte[] fileNameBytes = selectedFile.getName().getBytes(StandardCharsets.UTF_8);
                command.add(intToArrLE(fileNameBytes.length));
                command.add(fileNameBytes);
                if (writeGL_PASS(command)) {
                    print("GL Handle 'GetFile' command.", EMsgType.FAIL);
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

            File fileDirElement = new File(filePath);
            if (fileDirElement.exists()){
                if (fileDirElement.isDirectory())
                    command.add(GL_OBJ_TYPE_DIR);
                else {
                    command.add(GL_OBJ_TYPE_FILE);
                    command.add(longToArrLE(fileDirElement.length()));
                }
                if (writeGL_PASS(command)) {
                    print("GL Handle 'StatPath' command.", EMsgType.FAIL);
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
                    print("GL Handle 'StatPath' command.", EMsgType.FAIL);
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
                    print("GL Handle 'StatPath' command.", EMsgType.FAIL);
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
                            print("GL Handle 'Rename' command.", EMsgType.FAIL);
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
                        print("GL Handle 'Rename' command.", EMsgType.FAIL);
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
                    print("GL Handle 'Create' command.", EMsgType.FAIL);
                    return true;
                }
                //print("GL Handle 'Create' command.", EMsgType.PASS);
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
        //System.out.println("readFile "+fileName+"\t"+offset+"\t"+size+"\n");
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
                    return writeGL_FAIL("GL Handle 'ReadFile' command [CMD]" +
                            "\n         At offset: " + offset +
                            "\n         Requested: " + size +
                            "\n         Received:  " + bytesRead);
                // Let's tell as a command about our result.
                if (writeGL_PASS(longToArrLE(size))) {
                    print("GL Handle 'ReadFile' command [CMD]", EMsgType.FAIL);
                    return true;
                }
                // Let's bypass bytes we read total
                if (writeToUsb(chunk)) {
                    print("GL Handle 'ReadFile' command", EMsgType.FAIL);
                    return true;
                }
                return false;
            }
            else {
                randAccessFile.seek(offset);
                byte[] chunk = new byte[(int)size];         // yes, I know, but nothing to do here.
                // Let's find out how much bytes we got
                int bytesRead = randAccessFile.read(chunk);
                // Let's check that we read expected size
                if (bytesRead != (int)size)
                    return writeGL_FAIL("GL Handle 'ReadFile' command [CMD] Requested = "+size+" Read from file = "+bytesRead);
                // Let's tell as a command about our result.
                if (writeGL_PASS(longToArrLE(size))) {
                    print("GL Handle 'ReadFile' command [CMD]", EMsgType.FAIL);
                    return true;
                }
                // Let's bypass bytes we read total
                if (writeToUsb(chunk)) {
                    print("GL Handle 'ReadFile' command", EMsgType.FAIL);
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
                print("GL Handle 'ReadFile' command: unable to close: "+openReadFileNameAndPath+"\n\t"+ioe_.getMessage(), EMsgType.WARNING);
            }
            try{
                splitReader.close();
            }
            catch (NullPointerException ignored){}
            catch (IOException ioe_){
                print("GL Handle 'ReadFile' command: unable to close: "+openReadFileNameAndPath+"\n\t"+ioe_.getMessage(), EMsgType.WARNING);
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
            print("GL Handle 'WriteFile' command [1/1]", EMsgType.FAIL);
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
            print("GL Handle 'WriteFile' command", EMsgType.FAIL);
            return true;
        }
        return false;
    }

    /**
     * Handle 'SelectFile'
     * @return true if failed
     *          false if everything is ok
     * */
    private boolean selectFile(){
        File selectedFile = CompletableFuture.supplyAsync(() -> {
            FileChooser fChooser = new FileChooser();
            fChooser.setTitle(MediatorControl.INSTANCE.getResourceBundle().getString("btn_OpenFile")); // TODO: FIX BAD IMPLEMENTATION
            fChooser.setInitialDirectory(new File(System.getProperty("user.home")));// TODO: Consider fixing; not a priority.
            fChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("*", "*"));
            return fChooser.showOpenDialog(null);    // Leave as is for now.
        }, Platform::runLater).join();

        if (selectedFile == null){    // Nothing selected
            this.selectedFile = null;
            return writeGL_FAIL("GL Handle 'SelectFile' command: Nothing selected");
        }

        List<byte[]> command = new LinkedList<>();
        byte[] selectedFileNameBytes = ("SPEC:/"+selectedFile.getName()).getBytes(StandardCharsets.UTF_8);
        command.add(intToArrLE(selectedFileNameBytes.length));
        command.add(selectedFileNameBytes);
        if (writeGL_PASS(command)) {
            print("GL Handle 'SelectFile' command", EMsgType.FAIL);
            this.selectedFile = null;
            return true;
        }
        this.selectedFile = selectedFile;
        return false;
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
                    closeOpenedReadFilesGl();       // Could be a problem if GL glitches and slow down process. Or if user has extra-slow SD card. TODO: refactor?
                    continue;
                default:
                    print("GL Data transfer issue [read]\n         Returned: " +
                            UsbErrorCodes.getErrCode(result) +
                            "\n         GL Execution stopped", EMsgType.FAIL);
                    return null;
            }
        }
        print("GL Execution interrupted", EMsgType.INFO);
        return null;
    }
    private byte[] readGL_file(){
        ByteBuffer readBuffer = ByteBuffer.allocateDirect(8388608); // Just don't ask..
        IntBuffer readBufTransferred = IntBuffer.allocate(1);

        int result;

        while (! task.isCancelled() ) {
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
                    print("GL Data transfer issue [read]\n         Returned: " +
                            UsbErrorCodes.getErrCode(result) +
                            "\n         GL Execution stopped", EMsgType.FAIL);
                    return null;
            }
        }
        print("GL Execution interrupted", EMsgType.INFO);
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
            print(reportToUImsg, EMsgType.WARNING);
            return true;
        }
        print(reportToUImsg, EMsgType.FAIL);
        return false;
    }
    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private boolean writeToUsb(byte[] message){
        //System.out.println(">");
        //RainbowHexDump.hexDumpUTF16LE(message);   // DEBUG
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
                        print("GL Data transfer issue [write]\n         Requested: " +
                                message.length +
                                "\n         Transferred: "+writeBufTransferred.get(), EMsgType.FAIL);
                        return true;
                    }
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    print("GL Data transfer issue [write]\n         Returned: " +
                            UsbErrorCodes.getErrCode(result) +
                            "\n         GL Execution stopped", EMsgType.FAIL);
                    return true;
            }
        }
        print("GL Execution interrupted", EMsgType.INFO);
        return true;
    }
}
