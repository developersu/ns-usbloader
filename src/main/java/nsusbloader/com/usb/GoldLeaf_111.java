/*
    Copyright 2019-2025 Dmitry Isaenko

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
 * GoldLeaf 1.1.1 processing
 */
class GoldLeaf_111 extends TransferModule {
    private final boolean nspFilterForGl;

    //                     CMD
    private final byte[] CMD_GLCO_SUCCESS = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, 0x00, 0x00};         // used @ writeToUsb_GLCMD
    private final byte[] CMD_GLCO_FAILURE = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, (byte) 0xAD, (byte) 0xDE};  // used @ writeToUsb_GLCMD

    // System.out.println((356 & 0x1FF) | ((1 + 100) & 0x1FFF) << 9); // 52068 // 0x00 0x00 0xCB 0x64
    private final byte[] GL_OBJ_TYPE_FILE = new byte[]{0x01, 0x00, 0x00, 0x00};
    private final byte[] GL_OBJ_TYPE_DIR  = new byte[]{0x02, 0x00, 0x00, 0x00};

    private String recentPath;
    private String[] recentDirs;
    private String[] recentFiles;

    private final String[] nspMapKeySetIndexes;

    private String openReadFileNameAndPath;
    private RandomAccessFile randAccessFile;
    private NSSplitReader splitReader;

    private final HashMap<String, BufferedOutputStream> writeFilesMap = new HashMap<>();
    private long virtDriveSize;
    private final HashMap<String, Long> splitFileSize = new HashMap<>();

    private final boolean isWindows = System.getProperty("os.name").contains("Windows");
    private final String homePath = System.getProperty("user.home") + File.separator;
    // For using in CMD_SelectFile with SPEC:/ prefix
    private File selectedFile;

    private final CancellableRunnable task;

    private enum GL_CMD {
        CMD_GetDriveCount((byte) 1),
        CMD_GetDriveInfo((byte) 2),
        CMD_StatPath((byte) 3),
        CMD_GetFileCount((byte) 4),
        CMD_GetFile((byte) 5),
        CMD_GetDirectoryCount((byte) 6),
        CMD_GetDirectory((byte) 7),
        CMD_StartFile((byte) 8),
        CMD_ReadFile((byte) 9),
        CMD_WriteFile((byte) 10),
        CMD_EndFile((byte) 11),
        CMD_Create((byte) 12),
        CMD_Delete((byte) 13),
        CMD_Rename((byte) 14),
        CMD_GetSpecialPathCount((byte) 15),
        CMD_GetSpecialPath((byte) 16),
        CMD_SelectFile((byte) 17),
        CMD_UNKNOWN((byte) 255);

        private final byte id;

        GL_CMD(byte id) {
            this.id = id;
        }

        public static GL_CMD get(byte id) {
            for(GL_CMD cmd : values()) {
                if(cmd.id == id)
                    return cmd;
            }
            return CMD_UNKNOWN;
        }
    }

    GoldLeaf_111(DeviceHandle handler,
                 LinkedHashMap<String, File> nspMap,
                 CancellableRunnable task,
                 ILogPrinter logPrinter,
                 boolean nspFilter) {
        super(handler, nspMap, task, logPrinter);

        this.task = task;
        this.nspFilterForGl = nspFilter;

        print("=========== GoldLeaf v1.1.1 ===========\n\t" +
                "VIRT:/ equals files added into the application\n\t" +
                "HOME:/ equals "
                +System.getProperty("user.home"), EMsgType.INFO);

        // Let's collect file names to the array to simplify our life
        nspMapKeySetIndexes = nspMap.keySet().toArray(new String[0]);

        // Calculate size of VIRT:/ drive
        for (File nspFile : nspMap.values()) {
            if (nspFile.isDirectory()) {
                var subFiles = nspFile.listFiles((file, name) -> name.matches("[0-9]{2}"));
                assert subFiles != null;
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
        main_loop:
        while (true) {                          // Till user interrupted process.
            int someLength1, someLength2;
            var readByte = readGL();

            if (readByte == null)              // Issue @ readFromUsbGL method
                return;

            //RainbowHexDump.hexDumpUTF16LE(readByte);   // DEBUG
            //System.out.println("CHOICE: "+readByte[4]); // DEBUG
            // Arrays.equals(Arrays.copyOfRange(readByte, 0,4), CMD_GLCI)

            if (notGLCI(readByte))
                continue;

            switch (GL_CMD.get(readByte[4])) {
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
                case CMD_UNKNOWN:
                default:
                    writeGL_FAIL("GL Unknown command: "+readByte[4]+" [it's a very bad sign]");
            }
        }
        // Close (and flush) all opened streams.
        for (var bufferedOutputStream: writeFilesMap.values()){
            try{
                bufferedOutputStream.close();
            } catch (IOException | NullPointerException ignored){}
        }
        closeOpenedReadFilesGl();
    }
    private boolean notGLCI(byte[] inputBytes){
        return ! "GLCI".equals(new String(inputBytes, 0, 4, StandardCharsets.US_ASCII));
    }

    /**
     * Close files opened for read/write
     */
    private void closeOpenedReadFilesGl(){
        if (openReadFileNameAndPath != null){
            closeRAFandSplitReader();
            openReadFileNameAndPath = null;
            randAccessFile = null;
            splitReader = null;
        }
    }
    private void closeRAFandSplitReader(){
        try{
            randAccessFile.close();
        }
        catch (IOException ioe_){
            print("Unable to close: "+openReadFileNameAndPath+"\n\t"+ioe_.getMessage(), EMsgType.WARNING);
        }
        catch (Exception ignored){}
        try{
            splitReader.close();
        }
        catch (IOException ioe_){
            print("Unable to close: "+openReadFileNameAndPath+"\n\t"+ioe_.getMessage(), EMsgType.WARNING);
        }
        catch (Exception ignored){}
    }
    /**
     * Handle StartFile & EndFile
     * NOTE: It's something internal for GL and used somehow by GL-PC-app, so just ignore this, at least for v0.8.
     * @return true - failed, false - passed
     * */
    private boolean startOrEndFile(){
        return writeGL_PASS("GL Handle 'StartFile' command");
    }
    /**
     * Handle GetDriveCount
     * 2 drives declared in current implementation
     * @return true - failed, false - passed
     */
    private boolean getDriveCount(){
        return writeGL_PASS(intToArrLE(2),"GL Handle 'ListDrives' command");
    }
    /**
     * Handle GetDriveInfo
     * @return true - failed, false - passed
     */
    private boolean getDriveInfo(int driveNo){
        if (driveNo < 0 || driveNo > 1)
            return writeGL_FAIL("GL Handle 'GetDriveInfo' command [no such drive]");

        byte[] driveLabel,
                driveLabelLen,
                driveLetter,
                driveLetterLen,
                totalFreeSpace,
                totalSize;
        long totalSizeLong;

        if (driveNo == 0){ // 0 == VIRTUAL DRIVE
            driveLabel = "Virtual".getBytes(StandardCharsets.UTF_8);
            driveLabelLen = intToArrLE(driveLabel.length);
            driveLetter = "VIRT".getBytes(StandardCharsets.UTF_8);
            driveLetterLen = intToArrLE(driveLetter.length);
            totalFreeSpace = new byte[4];
            totalSizeLong = virtDriveSize;
        }
        else { //1 == User home dir
            driveLabel = "Home".getBytes(StandardCharsets.UTF_8);
            driveLabelLen = intToArrLE(driveLabel.length);
            driveLetter = "HOME".getBytes(StandardCharsets.UTF_8);
            driveLetterLen = intToArrLE(driveLetter.length);
            var userHomeDir = new File(System.getProperty("user.home"));
            totalFreeSpace = Arrays.copyOfRange(longToArrLE(userHomeDir.getFreeSpace()), 0, 4);;
            totalSizeLong = userHomeDir.getTotalSpace();
        }
        totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);

        var command = Arrays.asList(
                driveLabelLen,
                driveLabel,
                driveLetterLen,
                driveLetter,
                totalFreeSpace,
                totalSize);

        return writeGL_PASS(command, "GL Handle 'GetDriveInfo' command");
    }
    /**
     * Handle SpecialPathCount
     * Let's declare nothing. Write count of special paths
     * @return true - failed, false - passed
     * */
    private boolean getSpecialPathCount(){
        return writeGL_PASS(intToArrLE(0), "GL Handle 'SpecialPathCount' command");
    }
    /**
     * Handle SpecialPath
     * @return true - failed, false - passed
     * */
    private boolean getSpecialPath(int specialPathNo){
        return writeGL_FAIL("GL Handle 'SpecialPath' command [not supported]");
    }
    /**
     * Handle GetDirectoryCount & GetFileCount
     * @return true - failed, false - passed
     * */
    private boolean getDirectoryOrFileCount(String path, boolean isGetDirectoryCount) {
        if (path.equals("VIRT:/")) {
            return isGetDirectoryCount ?
                    writeGL_PASS("GL Handle 'GetDirectoryCount' command") :
                    writeGL_PASS(intToArrLE(nspMap.size()), "GL Handle 'GetFileCount' command Count = " + nspMap.size());
        }
        else if (path.startsWith("HOME:/")){
            // Let's make it normal path
            path = updateHomePath(path);
            var pathDir = new File(path);

            // Make sure it's exists and it's path
            if ((! pathDir.exists() ) || (! pathDir.isDirectory()) )
                return writeGL_FAIL("GL Handle 'GetDirectoryOrFileCount' command [doesn't exist or not a folder]");
            // Save recent dir path
            this.recentPath = path;
            var filesOrDirs = getFilesOrDirs(isGetDirectoryCount, pathDir);
            // If somehow there are no folders, let's say 0;
            if (filesOrDirs == null)
                return writeGL_PASS("GL Handle 'GetDirectoryOrFileCount' command");
            // Sorting is mandatory NOTE: Proxy tail
            Arrays.sort(filesOrDirs, String.CASE_INSENSITIVE_ORDER);

            if (isGetDirectoryCount)
                this.recentDirs = filesOrDirs;
            else
                this.recentFiles = filesOrDirs;
            // Otherwise, let's tell how may folders are in there
            return writeGL_PASS(intToArrLE(filesOrDirs.length), "GL Handle 'GetDirectoryOrFileCount' command");
        }
        else if (path.startsWith("SPEC:/")){
            if (isGetDirectoryCount)        // If dir request then 0 dirs
                return writeGL_PASS("GL Handle 'GetDirectoryCount' command");
            else if (selectedFile != null)  // Else it's file request, if we have selected then we will report 1.
                return writeGL_PASS(intToArrLE(1), "GL Handle 'GetFileCount' command Count = 1");
            return writeGL_FAIL("GL Handle 'GetDirectoryOrFileCount' command [unknown drive request] (file) - "+path);
        }
        // If requested drive is not VIRT and not HOME then reply error
        return writeGL_FAIL("GL Handle 'GetDirectoryOrFileCount' command [unknown drive request] "+(isGetDirectoryCount?"(dir) - ":"(file) - ")+path);
    }
    private String[] getFilesOrDirs(boolean isGetDirectoryCount, File pathDir) {
        String[] filesOrDirs;
        // Now collecting every folder or file inside
        if (isGetDirectoryCount){
            filesOrDirs = pathDir.list((current, name) -> {
                var dir = new File(current, name);
                return (dir.isDirectory() && ! dir.isHidden());
            });
        }
        else {
            filesOrDirs = pathDir.list((current, name) -> {
                var dir = new File(current, name);
                return (! dir.isDirectory() && nspFilterForGl ?
                                                name.toLowerCase().endsWith(".nsp") :
                                                ! dir.isHidden());
                });
        }
        return filesOrDirs;
    }

    /**
     * Handle GetDirectory
     * @return true - failed, false - passed
     * */
    private boolean getDirectory(String dirName, int subDirNo){
        if (dirName.startsWith("HOME:/")) {
            dirName = updateHomePath(dirName);

            var command = new ArrayList<byte[]>();

            if (dirName.equals(recentPath) && recentDirs != null && recentDirs.length != 0){
                var dirNameBytes = recentDirs[subDirNo].getBytes(StandardCharsets.UTF_8);
                command.add(intToArrLE(dirNameBytes.length));
                command.add(dirNameBytes);
            }
            else {
                var pathDir = new File(dirName);
                // Make sure it's exists and it's path
                if ((! pathDir.exists() ) || (! pathDir.isDirectory()) )
                    return writeGL_FAIL("GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
                this.recentPath = dirName;
                // Now collecting every folder or file inside
                this.recentDirs = pathDir.list((current, name) -> {
                    var dir = new File(current, name);
                    return (dir.isDirectory() && ! dir.isHidden());      // TODO: FIX FOR WIN ?
                });
                // Check that we still don't have any fuckups
                if (this.recentDirs != null && this.recentDirs.length > subDirNo){
                    Arrays.sort(recentFiles, String.CASE_INSENSITIVE_ORDER);
                    var dirBytesName = recentDirs[subDirNo].getBytes(StandardCharsets.UTF_8);
                    command.add(intToArrLE(dirBytesName.length));
                    command.add(dirBytesName);
                }
                else
                    return writeGL_FAIL("GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
            }
            return writeGL_PASS(command, "GL Handle 'GetDirectory' command.");
        }
        // VIRT:// and any other
        return writeGL_FAIL("GL Handle 'GetDirectory' command for virtual drive [no folders support]");
    }
    /**
     * Handle GetFile
     * @return true - failed, false - passed
     * */
    private boolean getFile(String dirName, int subDirNo){
        var command = new LinkedList<byte[]>();

        if (dirName.startsWith("HOME:/")) {
            dirName = updateHomePath(dirName);

            if (dirName.equals(recentPath) && recentFiles != null && recentFiles.length != 0){
                var fileNameBytes = recentFiles[subDirNo].getBytes(StandardCharsets.UTF_8);
                command.add(intToArrLE(fileNameBytes.length));
                command.add(fileNameBytes);
            }
            else {
                var pathDir = new File(dirName);
                // Make sure it's exists and it's path
                if ((! pathDir.exists() ) || (! pathDir.isDirectory()) )
                    writeGL_FAIL("GL Handle 'GetFile' command [doesn't exist or not a folder]");
                this.recentPath = dirName;
                // Now collecting every folder or file inside
                if (nspFilterForGl){
                    this.recentFiles = pathDir.list((current, name) -> {
                        var dir = new File(current, name);
                        return (! dir.isDirectory() && name.toLowerCase().endsWith(".nsp"));
                    });
                }
                else {
                    this.recentFiles = pathDir.list((current, name) -> {
                        var dir = new File(current, name);
                        return (! dir.isDirectory() && (! dir.isHidden()));
                    });
                }
                // Check that we still don't have any fuckups
                if (this.recentFiles != null && this.recentFiles.length > subDirNo){
                    Arrays.sort(recentFiles, String.CASE_INSENSITIVE_ORDER);
                    var fileNameBytes = recentFiles[subDirNo].getBytes(StandardCharsets.UTF_8);
                    command.add(intToArrLE(fileNameBytes.length));
                    command.add(fileNameBytes);
                }
                else
                    return writeGL_FAIL("GL Handle 'GetFile' command [doesn't exist or not a folder]");
            }
            return writeGL_PASS(command, "GL Handle 'GetFile' command.");
        }
        else if (dirName.equals("VIRT:/") && (! nspMap.isEmpty())){ // thus nspMapKeySetIndexes also != 0
            var fileNameBytes = nspMapKeySetIndexes[subDirNo].getBytes(StandardCharsets.UTF_8);
            command.add(intToArrLE(fileNameBytes.length));
            command.add(fileNameBytes);
            return writeGL_PASS(command, "GL Handle 'GetFile' command.");
        }
        else if (dirName.equals("SPEC:/") && (selectedFile != null)){
            byte[] fileNameBytes = selectedFile.getName().getBytes(StandardCharsets.UTF_8);
            command.add(intToArrLE(fileNameBytes.length));
            command.add(fileNameBytes);
            return writeGL_PASS(command, "GL Handle 'GetFile' command.");
        }
        //  any other cases
        return writeGL_FAIL("GL Handle 'GetFile' command for virtual drive [no folders support?]");
    }
    /**
     * Handle StatPath
     * @return true - failed, false - passed
     * */
    private boolean statPath(String filePath){
        var command = new ArrayList<byte[]>();

        if (filePath.startsWith("HOME:/")){
            filePath = updateHomePath(filePath);

            var fileDirElement = new File(filePath);
            if (fileDirElement.exists()){
                if (fileDirElement.isDirectory())
                    command.add(GL_OBJ_TYPE_DIR);
                else {
                    command.add(GL_OBJ_TYPE_FILE);
                    command.add(longToArrLE(fileDirElement.length()));
                }
                return writeGL_PASS(command, "GL Handle 'StatPath' command.");
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

                return writeGL_PASS(command, "GL Handle 'StatPath' command.");
            }
        }
        else if (filePath.startsWith("SPEC:/")){
            //System.out.println(filePath);
            filePath = filePath.replaceFirst("SPEC:/","");
            if (selectedFile.getName().equals(filePath)){
                command.add(GL_OBJ_TYPE_FILE);
                command.add(longToArrLE(selectedFile.length()));
                return writeGL_PASS(command, "GL Handle 'StatPath' command.");
            }
        }
        return writeGL_FAIL("GL Handle 'StatPath' command [no such folder] - "+filePath);
    }
    /**
     * Handle 'Rename' that is actually 'mv'
     * @return true - failed, false - passed
     * */
    private boolean rename(String fileName, String newFileName){
        if (fileName.startsWith("HOME:/")){
            // This shit takes too much time to explain, but such behaviour won't let GL to fail
            this.recentPath = null;
            this.recentFiles = null;
            this.recentDirs = null;
            fileName = updateHomePath(fileName);
            newFileName = updateHomePath(newFileName);

            var newFile = new File(newFileName);
            if (! newFile.exists()){        // Else, report error
                try {
                    if (new File(fileName).renameTo(newFile)){
                        return writeGL_PASS("GL Handle 'Rename' command.");
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
     * @return true - failed, false - passed
     * */
    private boolean delete(String fileName) {
        if (fileName.startsWith("HOME:/")) {
            fileName = updateHomePath(fileName);

            File fileToDel = new File(fileName);
            try {
                if (fileToDel.delete()){
                    return writeGL_PASS("GL Handle 'Rename' command.");
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
     * @return true - failed, false - passed
     * */
    private boolean create(String fileName, byte type) {
        if (! fileName.startsWith("HOME:/"))    // For VIRT:/ and others we don't serve requests
            return writeGL_FAIL("GL Handle 'Delete' command [not supported for virtual drive/wrong drive/read-only directory]");

        fileName = updateHomePath(fileName);

        boolean result = false;
        try {
            if (type == 1)
                result = new File(fileName).createNewFile();
            else if (type == 2)
                result = new File(fileName).mkdir();
        }
        catch (SecurityException | IOException ignored){}

        if (result) {
            return writeGL_PASS("GL Handle 'Create' command.");
        }

        return writeGL_FAIL("GL Handle 'Delete' command [not supported for virtual drive/wrong drive/read-only directory]");
    }

    /**
     * Handle 'ReadFile'
     * @param fileName full path including new file name in the end
     * @param offset requested offset
     * @param size requested size
     * @return true - failed, false - passed
     * */
    private boolean readFile(String fileName, long offset, long size) {
        //System.out.println("readFile "+fileName+"\t"+offset+"\t"+size+"\n");
        if (fileName.startsWith("VIRT:/")){
            // Let's find out which file requested
            var fNamePath = nspMap.get(fileName.substring(6)).getAbsolutePath(); // NOTE: 6 = "VIRT:/".length
            // If we don't have this file opened, let's open it
            if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(fNamePath))) {
                // Try close what opened
                if (openReadFileNameAndPath != null)
                    closeRAFandSplitReader();
                // Open what has to be opened
                try{
                    var tempFile = nspMap.get(fileName.substring(6));
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
                    }catch (Exception ignored){}
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
            var chunk = new byte[(int)size];
            int bytesRead;

            if (randAccessFile == null){
                splitReader.seek(offset);
                bytesRead = splitReader.read(chunk);   // Let's find out how many bytes we got
            }
            else {
                randAccessFile.seek(offset);
                bytesRead = randAccessFile.read(chunk); // Let's find out how many bytes we got
            }

            if (bytesRead != (int) size)    // Let's check that we read expected size
                return writeGL_FAIL("GL Handle 'ReadFile' command [CMD]" +
                        "\n         At offset: " + offset +
                        "\n         Requested: " + size +
                        "\n         Received:  " + bytesRead);
            if (writeGL_PASS(longToArrLE(size), "GL Handle 'ReadFile' command [CMD]")) {    // Let's tell as a command about our result.
                return true;
            }
            if (writeToUsb(chunk)) {    // Let's bypass bytes we read total
                print("GL Handle 'ReadFile' command", EMsgType.FAIL);
                return true;
            }
            return false;
        }
        catch (Exception ioe){
            closeOpenedReadFilesGl();
            return writeGL_FAIL("GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
        }
    }
    /**
     * Handle 'WriteFile'
     * @param fileName full path including new file name in the end
     *
     * @return true - failed, false - passed
     * */
    private boolean writeFile(String fileName) {
        if (fileName.startsWith("VIRT:/"))
            return writeGL_FAIL("GL Handle 'WriteFile' command [not supported for virtual drive]");

        fileName = updateHomePath(fileName);
        // Check if this file being used during this session
        if (! writeFilesMap.containsKey(fileName)){
            try{                                     // If this file exists GL will take care; Otherwise, let's add it
                writeFilesMap.put(fileName,
                        new BufferedOutputStream(new FileOutputStream(fileName, true))); // Open what we have to open
            } catch (IOException ioe){
                return writeGL_FAIL("GL Handle 'WriteFile' command [IOException]\n\t"+ioe.getMessage());
            }
        }

        var transferredData = readGL_file();

        if (transferredData == null){
            print("GL Handle 'WriteFile' command [1/1]", EMsgType.FAIL);
            return true;
        }
        try{
            writeFilesMap.get(fileName).write(transferredData, 0, transferredData.length);
        }
        catch (IOException ioe){
            return writeGL_FAIL("GL Handle 'WriteFile' command [1/1]\n\t"+ioe.getMessage());
        }
        // Report we're good
        return writeGL_PASS("GL Handle 'WriteFile' command");
    }

    /**
     * Handle 'SelectFile'
     * @return true - failed, false - passed
     * */
    private boolean selectFile(){
        var selectedFile = CompletableFuture.supplyAsync(() -> {
            var fChooser = new FileChooser();
            fChooser.setTitle(MediatorControl.INSTANCE.getResourceBundle().getString("btn_OpenFile"));   // TODO: FIX BAD IMPLEMENTATION
            fChooser.setInitialDirectory(new File(System.getProperty("user.home")));                         // TODO: Consider fixing; not a priority.
            fChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("*", "*"));
            return fChooser.showOpenDialog(null);    // Leave as is for now.
        }, Platform::runLater).join();

        if (selectedFile == null){    // Nothing selected
            this.selectedFile = null;
            return writeGL_FAIL("GL Handle 'SelectFile' command: Nothing selected");
        }

        var selectedFileNameBytes = ("SPEC:/"+selectedFile.getName()).getBytes(StandardCharsets.UTF_8);
        var command = Arrays.asList(
                intToArrLE(selectedFileNameBytes.length),
                selectedFileNameBytes);
        if (writeGL_PASS(command, "GL Handle 'SelectFile' command")) {
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
        glPath = homePath + glPath.substring(6);      // Do not use replaceAll since it will consider \ as special directive
        return glPath;
    }
    /**
     * Convert INT (Little endian) value to bytes-array representation
     * */
    private byte[] intToArrLE(int value){
        return ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }
    /**
     * Convert LONG (Little endian) value to bytes-array representation
     * */
    private byte[] longToArrLE(long value){
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(value)
                .array();
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
        var readBuffer = ByteBuffer.allocateDirect(4096);
        var readBufTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            int result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    var receivedBytes = new byte[readBufTransferred.get()];
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
        var readBuffer = ByteBuffer.allocateDirect(8388608); // Just don't ask..
        var readBufTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled() ) {
            int result = LibUsb.bulkTransfer(handlerNS, (byte) 0x81, readBuffer, readBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    var receivedBytes = new byte[readBufTransferred.get()];
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
     * Write new command
     * */
    private boolean writeGL_PASS(String onFailureText){
        if (writeToUsb(Arrays.copyOf(CMD_GLCO_SUCCESS, 4096))){
            print(onFailureText, EMsgType.FAIL);
            return true;
        }
        return false;
    }
    private boolean writeGL_PASS(byte[] message, String onFailureText){
        var result = writeToUsb(ByteBuffer.allocate(4096)
                .put(CMD_GLCO_SUCCESS)
                .put(message)
                .array());

        if(result){
            print(onFailureText, EMsgType.FAIL);
            return true;
        }
        return false;
    }
    private boolean writeGL_PASS(List<byte[]> messages, String onFailureText){
        var writeBuffer = ByteBuffer.allocate(4096)
                .put(CMD_GLCO_SUCCESS);
        messages.forEach(writeBuffer::put);
        if (writeToUsb(writeBuffer.array())){
            print(onFailureText, EMsgType.FAIL);
            return true;
        }
        return false;
    }

    private boolean writeGL_FAIL(String failureMessage){
        if (writeToUsb(Arrays.copyOf(CMD_GLCO_FAILURE, 4096))){
            print(failureMessage, EMsgType.WARNING);
            return true;
        }
        print(failureMessage, EMsgType.FAIL);
        return false;
    }
    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private boolean writeToUsb(byte[] message){
        //RainbowHexDump.hexDumpUTF16LE(message);   // DEBUG
        var writeBufTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            int result = LibUsb.bulkTransfer(handlerNS,
                    (byte) 0x01,
                    ByteBuffer.allocateDirect(message.length).put(message), // order -> BIG_ENDIAN; Don't writeBuffer.rewind();
                    writeBufTransferred,
                    1000);  // TIMEOUT. 0 stands for infinite. Endpoint OUT = 0x01

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
