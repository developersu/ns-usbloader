/*
    Copyright 2019-2026 Dmitry Isaenko

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
package nsusbloader.com.usb.gl;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import nsusbloader.MediatorControl;
import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.com.helpers.NSSplitReader;
import nsusbloader.com.usb.TransferModule;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static nsusbloader.NSLDataTypes.EMsgType.*;
import static nsusbloader.com.DataConvertUtils.*;

/**
 * GoldLeaf v0.10 processing
 */
public class GoldLeaf_010 extends TransferModule {

    private final static int PACKET_SIZE = 4096;
    //                     CMD
    public final static byte[] EXCEPTION_CAUGHT =    Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, (byte) 0xF1, (byte) 0xBA}, PACKET_SIZE);
    public final static byte[] INVALID_INDEX =       Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, (byte) 0xF2, (byte) 0xBA}, PACKET_SIZE);
    public final static byte[] SELECTION_CANCELLED = Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, (byte) 0xF4, (byte) 0xBA}, PACKET_SIZE);

    private final static byte[] CMD_GLCO_SUCCESS_FLAG = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, 0x00, 0x00};

    private final static byte[] CMD_GLCO_SUCCESS =
            Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, 0x00, 0x00}, PACKET_SIZE);

    // System.out.println((356 & 0x1FF) | ((1 + 100) & 0x1FFF) << 9); // 52068 // 0x00 0x00 0xCB 0x64
    private final byte[] GL_OBJECT_TYPE_FILE = new byte[]{0x01, 0x00, 0x00, 0x00};
    private final byte[] GL_OBJECT_TYPE_DIR = new byte[]{0x02, 0x00, 0x00, 0x00};

    private final boolean nspFilter;

    private String recentPath;
    private String[] recentDirs;
    private String[] recentFiles;

    private final String[] nspMapKeySetIndexes;

    protected String openReadFileNameAndPath;
    protected RandomAccessFile randAccessFile;
    protected NSSplitReader splitReader;

    private final HashMap<String, BufferedOutputStream> writeFilesMap = new HashMap<>();
    protected long virtDriveSize;
    private final HashMap<String, Long> splitFileSize = new HashMap<>();

    private final boolean isWindows = System.getProperty("os.name").contains("Windows");
    protected final String homePath = System.getProperty("user.home");
    // For using in CMD_SelectFile with SPEC:/ prefix
    protected File selectedFile;

    public GoldLeaf_010(DeviceHandle handler,
                        LinkedHashMap<String, File> nspMap,
                        CancellableRunnable task,
                        ILogPrinter logPrinter,
                        boolean nspFilter) {
        super(handler, nspMap, task, logPrinter);

        this.nspFilter = nspFilter;

        printWelcomeMessage();

        // Let's collect file names to the array (simplifies flow)
        nspMapKeySetIndexes = nspMap.keySet().toArray(new String[0]);

        // Calculate size of VIRT:/ drive
        nspMap.values().forEach(nspFile -> {
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
        });

        if (workLoop())
            return;

        writeFilesMap.values().forEach(stream -> {
            try{
                stream.close();
            } catch (IOException | NullPointerException ignored){}
        });
        closeOpenedReadFilesGl();
    }

    protected void printWelcomeMessage(){
        print("=========== GoldLeaf v0.10+ ===========\n\t" +
                "VIRT:/ equals files added into the application\n\t" +
                "HOME:/ equals " + homePath, INFO);
    }

    protected boolean workLoop() {
        try {
            while (true) {                          // Till user interrupted process.
                GlString glString1;
                var readByte = readUsb();

                if (notGLCI(readByte))
                    continue;

                switch (GoldleafCmd.get(readByte[4])) {
                    case GetDriveCount:
                        getDriveCount();
                        break;
                    case GetDriveInfo:
                        getDriveInfo(arrToIntLE(readByte,8));
                        break;
                    case GetSpecialPathCount:
                        getSpecialPathCount();
                        break;
                    case GetSpecialPath:
                        getSpecialPath(arrToIntLE(readByte,8));
                        break;
                    case GetDirectoryCount:
                        getDirectoryOrFileCount(readString(readByte, 8).toString(), true);
                        break;
                    case GetFileCount:
                        getDirectoryOrFileCount(readString(readByte, 8).toString(), false);
                        break;
                    case GetDirectory:
                        glString1 = readString(readByte, 8);
                        getDirectory(glString1.toString(), arrToIntLE(readByte, glString1.length()+12));
                        break;
                    case GetFile:
                        glString1 = readString(readByte, 8);
                        getFile(glString1.toString(), arrToIntLE(readByte, glString1.length()+12));
                        break;
                    case StatPath:
                        statPath(readString(readByte, 8).toString());
                        break;
                    case Rename:
                        glString1 = readString(readByte, 8);
                        var glString2 = readString(readByte, 12+glString1.length());
                        rename(glString1.toString(), glString2.toString());
                        break;
                    case Delete:
                        delete(readString(readByte, 8).toString());
                        break;
                    case Create:
                        create(readString(readByte, 8).toString(), readByte[8]);
                        break;
                    case ReadFile:
                        glString1 = readString(readByte, 8);
                        readFile(glString1.toString(),
                                arrToLongLE(readByte, 12+glString1.length()),
                                arrToLongLE(readByte, 12+glString1.length()+8));
                        break;
                    case WriteFile:
                        writeFile(readString(readByte, 8).toString());
                        break;
                    case SelectFile:
                        selectFile();
                        break;
                    case StartFile:
                    case EndFile:
                        startOrEndFile();
                        break;
                    case CMD_UNKNOWN:
                    default:
                        writeGL_FAIL(EXCEPTION_CAUGHT, "GL Unknown command: "+readByte[4]+" [it's a very bad sign]");
                }
            }
        }
        catch (InterruptedException ie) {
            ie.printStackTrace();
            return true;
        }
        catch (Exception e) {
            print(e.getMessage(), FAIL);
            e.printStackTrace();
            return false;
        }
    }

    protected GlString readString(byte[] readByte, int startPosition) {
        return new GlString010(readByte, startPosition);
    }

    protected boolean notGLCI(byte[] inputBytes) {
        return ! "GLCI".equals(new String(inputBytes, 0, 4, US_ASCII));
    }

    /**
     * Handle StartFile & EndFile
     * NOTE: It's something internal for GL and used somehow by GL-PC-app, so just ignore this, at least for v0.8.
     * @return true - failed, false - passed
     * */
    protected void startOrEndFile() throws Exception {
        writeGL_PASS("GL Handle 'StartFile' command");
    }
    /**
     * Handle GetDriveCount
     * 2 drives declared in current implementation
     * @return true - failed, false - passed
     */
    protected void getDriveCount() throws Exception {
        writeGL_PASS(intToArrLE(2),"GL Handle 'ListDrives' command");
    }
    /**
     * Handle GetDriveInfo
     * @return true - failed, false - passed
     */
    protected void getDriveInfo(int driveNo) throws Exception {
        if (driveNo < 0 || driveNo > 1) {
            writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetDriveInfo' command [no such drive]");
            return;
        }

        byte[] driveLabel,
                driveLabelLen,
                driveLetter,
                driveLetterLen,
                totalFreeSpace;
        long totalSizeLong;

        if (driveNo == 0){ // 0 == VIRTUAL DRIVE
            driveLabel = "Virtual".getBytes(UTF_8);
            driveLabelLen = intToArrLE(driveLabel.length);
            driveLetter = "VIRT".getBytes(UTF_8);
            driveLetterLen = intToArrLE(driveLetter.length);
            totalFreeSpace = new byte[4];
            totalSizeLong = virtDriveSize;
        }
        else { //1 == User home dir
            driveLabel = "Home".getBytes(UTF_8);
            driveLabelLen = intToArrLE(driveLabel.length);
            driveLetter = "HOME".getBytes(UTF_8);
            driveLetterLen = intToArrLE(driveLetter.length);
            var userHomeDir = new File(System.getProperty("user.home"));
            totalFreeSpace = Arrays.copyOfRange(longToArrLE(userHomeDir.getFreeSpace()), 0, 4);;
            totalSizeLong = userHomeDir.getTotalSpace();
        }
        var totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);

        var command = Arrays.asList(
                driveLabelLen,
                driveLabel,
                driveLetterLen,
                driveLetter,
                totalFreeSpace,
                totalSize);

        writeGL_PASS(command, "GL Handle 'GetDriveInfo' command");
    }
    /**
     * Handle SpecialPathCount
     * Let's declare nothing. Write count of special paths
     * @return true - failed, false - passed
     * */
    protected void getSpecialPathCount() throws Exception {
        writeGL_PASS(intToArrLE(0), "GL Handle 'SpecialPathCount' command");
    }
    /**
     * Handle SpecialPath
     * @return true - failed, false - passed
     * */
    protected void getSpecialPath(int specialPathNo) throws Exception {
        writeGL_FAIL(INVALID_INDEX, "GL Handle 'SpecialPath' command [not supported]");
    }
    /**
     * Handle GetDirectoryCount & GetFileCount
     * @return true - failed, false - passed
     * */
    protected void getDirectoryOrFileCount(String glFileName, boolean isGetDirectoryCount) throws Exception {
        if (glFileName.equals("VIRT:/")) {
            if (isGetDirectoryCount)
                writeGL_PASS("GL Handle 'GetDirectoryCount' command");
            else
                writeGL_PASS(intToArrLE(nspMap.size()), "GL Handle 'GetFileCount' command Count = " + nspMap.size());
            return;
        }
        else if (glFileName.startsWith("HOME:/")){
            var path = decodeGlPath(glFileName);
            var pathDir = new File(path);

            if (notExistsOrDirectory(pathDir)) {
                writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'GetDirectoryOrFileCount' command [doesn't exist or not a folder] " + pathDir);
                return;
            }

            this.recentPath = path; // Save recent dir path
            var filesOrDirs = isGetDirectoryCount ?
                    pathDir.list(this::isDirectoryAndNotHidden) :
                    pathDir.list(this::isFileAndNotHidden);

            // If no folders, let's say 0;
            if (filesOrDirs == null) {
                writeGL_PASS("GL Handle 'GetDirectoryOrFileCount' command");
                return;
            }
            // Sorting is mandatory NOTE: Proxy tail
            Arrays.sort(filesOrDirs, String.CASE_INSENSITIVE_ORDER);

            if (isGetDirectoryCount)
                this.recentDirs = filesOrDirs;
            else
                this.recentFiles = filesOrDirs;
            // Otherwise, let's tell how may folders are in there
            writeGL_PASS(intToArrLE(filesOrDirs.length), "GL Handle 'GetDirectoryOrFileCount' command");
            return;
        }
        else if (glFileName.startsWith("SPEC:/")){
            if (isGetDirectoryCount) {        // If dir request then 0 dirs
                writeGL_PASS("GL Handle 'GetDirectoryCount' command");
                return;
            }
            else if (selectedFile != null) {  // Else it's file request, if we have selected then we will report 1.
                writeGL_PASS(intToArrLE(1), "GL Handle 'GetFileCount' command Count = 1");
                return;
            }
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'GetDirectoryOrFileCount' command [unknown drive request] (file) - "+glFileName);
            return;
        }
        // If requested drive is not VIRT and not HOME then reply error
        writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'GetDirectoryOrFileCount' command [unknown drive request] "+(isGetDirectoryCount?"(dir) - ":"(file) - ")+glFileName);
    }

    /**
     * Handle GetDirectory
     * @return true - failed, false - passed
     * */
    protected void getDirectory(String dirName, int subDirNo) throws Exception{
        if (dirName.startsWith("HOME:/")) {
            dirName = decodeGlPath(dirName);

            var command = new ArrayList<byte[]>();

            if (dirName.equals(recentPath) && recentDirs != null && recentDirs.length != 0){
                var dirNameBytes = recentDirs[subDirNo].getBytes(UTF_8);
                command.add(intToArrLE(dirNameBytes.length));
                command.add(dirNameBytes);
            }
            else {
                var pathDir = new File(dirName);
                // Make sure it's exists and it's path
                if (notExistsOrDirectory(pathDir)) {
                    writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
                    return;
                }
                this.recentPath = dirName;
                // Now collecting every folder or file inside
                this.recentDirs = pathDir.list(this::isDirectoryAndNotHidden);
                // Check that we still don't have any fuckups
                if (this.recentDirs != null && this.recentDirs.length > subDirNo){
                    Arrays.sort(recentFiles, String.CASE_INSENSITIVE_ORDER);
                    var dirBytesName = recentDirs[subDirNo].getBytes(UTF_8);
                    command.add(intToArrLE(dirBytesName.length));
                    command.add(dirBytesName);
                }
                else {
                    writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetDirectory' command [doesn't exist or not a folder]");
                    return;
                }
            }
            writeGL_PASS(command, "GL Handle 'GetDirectory' command.");
            return;
        }
        // VIRT:// and any other
        writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetDirectory' command for virtual drive [no folders support]");
    }
    /**
     * Handle GetFile
     * @return true - failed, false - passed
     * */
    protected void getFile(String glDirName, int subDirNo) throws Exception {
        var command = new LinkedList<byte[]>();

        if (glDirName.startsWith("HOME:/")) {
            var dirName = decodeGlPath(glDirName);

            if (dirName.equals(recentPath) && recentFiles != null && recentFiles.length != 0){
                var fileNameBytes = recentFiles[subDirNo].getBytes(UTF_8);
                command.add(intToArrLE(fileNameBytes.length));
                command.add(fileNameBytes);
            }
            else {
                var pathDir = new File(dirName);
                if (notExistsOrDirectory(pathDir)) {
                    writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetFile' command [doesn't exist or not a folder]");
                    return;
                }
                this.recentPath = dirName;
                this.recentFiles = pathDir.list(this::isFileAndNotHidden);
                // Check that we still don't have any fuckups
                if (this.recentFiles != null && this.recentFiles.length > subDirNo){
                    Arrays.sort(recentFiles, String.CASE_INSENSITIVE_ORDER);
                    var fileNameBytes = recentFiles[subDirNo].getBytes(UTF_8);
                    command.add(intToArrLE(fileNameBytes.length));
                    command.add(fileNameBytes);
                }
                else {
                    writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetFile' command [doesn't exist or not a folder]");
                    return;
                }
            }
            writeGL_PASS(command, "GL Handle 'GetFile' command.");
            return;
        }
        else if (glDirName.equals("VIRT:/") && (! nspMap.isEmpty())){ // thus nspMapKeySetIndexes also != 0
            var fileNameBytes = nspMapKeySetIndexes[subDirNo].getBytes(UTF_8);
            command.add(intToArrLE(fileNameBytes.length));
            command.add(fileNameBytes);
            writeGL_PASS(command, "GL Handle 'GetFile' command.");
            return;
        }
        else if (glDirName.equals("SPEC:/") && (selectedFile != null)){
            var fileNameBytes = selectedFile.getName().getBytes(UTF_8);
            command.add(intToArrLE(fileNameBytes.length));
            command.add(fileNameBytes);
            writeGL_PASS(command, "GL Handle 'GetFile' command.");
            return;
        }
        //  any other cases
        writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetFile' command for virtual drive [no folders support?]");
    }
    /**
     * Handle StatPath
     * @return true - failed, false - passed
     * */
    protected void statPath(String glFileName) throws Exception {
        var command = new ArrayList<byte[]>();

        if (glFileName.startsWith("HOME:/")){
            var fileDirElement = new File(decodeGlPath(glFileName));

            if (fileDirElement.exists()){
                if (fileDirElement.isDirectory())
                    command.add(GL_OBJECT_TYPE_DIR);
                else {
                    command.add(GL_OBJECT_TYPE_FILE);
                    command.add(longToArrLE(fileDirElement.length()));
                }
                writeGL_PASS(command, "GL Handle 'StatPath' command for "+glFileName);
                return;
            }
        }
        else if (glFileName.startsWith("VIRT:/")) {
            var fileName = glFileName.replaceFirst("^.*?:/", "");
            if (nspMap.containsKey(fileName)){
                command.add(GL_OBJECT_TYPE_FILE);                              // THIS IS INT
                if (nspMap.get(fileName).isDirectory())
                    command.add(longToArrLE(splitFileSize.get(fileName)));    // YES, THIS IS LONG!;
                else
                    command.add(longToArrLE(nspMap.get(fileName).length()));    // YES, THIS IS LONG!

                writeGL_PASS(command, "GL Handle 'StatPath' command for "+glFileName);
                return;
            }
        }
        else if (glFileName.startsWith("SPEC:/")){
            var fileName = glFileName.replaceFirst("^.*?:/", "");
            if (selectedFile.getName().equals(fileName)){
                command.add(GL_OBJECT_TYPE_FILE);
                command.add(longToArrLE(selectedFile.length()));
                writeGL_PASS(command, "GL Handle 'StatPath' command for "+glFileName);
                return;
            }
        }
        writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'StatPath' command [no such path]: "+glFileName);
    }
    /**
     * Handle 'Rename' that is actually 'mv'
     * @return true - failed, false - passed
     * */
    protected void rename(String glFileName, String glNewFileName) throws Exception {
        if (glFileName.startsWith("HOME:/")){
            // Prevent GL failures
            this.recentPath = null;
            this.recentFiles = null;
            this.recentDirs = null;

            var fileName = decodeGlPath(glFileName);
            var newFile = new File(decodeGlPath(glNewFileName));

            try {
                if (new File(fileName).renameTo(newFile)){
                    writeGL_PASS("GL Handle 'Rename' command.");
                    return;
                }
            }
            catch (SecurityException se){
                writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'Rename' command failed:\n\t" +se.getMessage());
                return;
            }
        }
        // For VIRT:/ and others we don't serve requests
        writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'Rename' command is not supported for virtual drive, selected files," +
                " if file with such name already exists in folder, read-only directories");
    }
    /**
     * Handle 'Delete'
     * @return true - failed, false - passed
     * */
    protected void delete(String glFileName) throws Exception {
        if (! glFileName.startsWith("HOME:/")) {
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'Delete' command [not supported for virtual drive/wrong drive/read-only directory] "+glFileName);
            return;
        }

        var file = new File(decodeGlPath(glFileName));
        try {
            if (file.delete()) {
                writeGL_PASS("GL Handle 'Rename' command.");
                return;
            }
        }
        catch (SecurityException ignored){} // Ah, leave it

        writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'Create' command [unknown drive/read-only directory]");
    }
    /**
     * Handle 'Create'
     * @param type 1 → file,  2 → folder
     * @param glFileName full path including new file name in the end
     * @return true - failed, false - passed
     * */
    protected void create(String glFileName, byte type) throws Exception {
        if (! glFileName.startsWith("HOME:/")) {    // For VIRT:/ and others we don't serve requests
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'Create' command [not supported for virtual drive/wrong drive/read-only directory]" + glFileName);
            return;
        }

        var file = new File(decodeGlPath(glFileName));
        try {
            var result = switch (type) {
                case 1 -> file.createNewFile();
                case 2 -> file.mkdir();
                default -> false;
            };

            if (result) {
                writeGL_PASS("GL Handle 'Create' command.");
                return;
            }
        }
        catch (SecurityException | IOException ignored){}

        writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'Create' command [unknown drive/read-only directory]");
    }

    /**
     * Handle 'ReadFile'
     * @param glFileName full path including new file name in the end in format of Goldleaf
     * @param offset requested offset
     * @param size requested size
     * @return true - failed, false - passed
     * */
    protected void readFile(String glFileName, long offset, long size) throws Exception {
        var fileName = glFileName.replaceFirst("^.*?:/", "");
        if (glFileName.startsWith("VIRT:/")){                                              // Could have split-file
            // Let's find out which file requested
            var fNamePath = nspMap.get(fileName).getAbsolutePath(); // NOTE: 6 = "VIRT:/".length
            // If we don't have this file opened, let's open it
            if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(fNamePath))) {
                if (openReadFileNameAndPath != null)                                      // (Try to) close what opened
                    closeRAFandSplitReader();
                try{                                                                      // And open the rest
                    var tempFile = nspMap.get(fileName);
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
                    writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
                    return;
                }
            }
        }
        else { // SPEC:/ & HOME:/
            String filePath;

            if (glFileName.startsWith("SPEC:/")) {
                if (! fileName.equals(selectedFile.getName())) {
                    writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command\n\trequested != selected:\n\t"
                            + glFileName + "\n\t" + selectedFile);
                    return;
                }
                filePath = selectedFile.getAbsolutePath();
            }
            else {
                filePath = decodeGlPath(glFileName); // What requested?
            }
            // If we don't have this file opened, let's open it
            if (openReadFileNameAndPath == null || (! openReadFileNameAndPath.equals(filePath))) {
                if (openReadFileNameAndPath != null) // Try close what opened
                    closeRAF();
                try{                                   // Open what has to be opened
                    randAccessFile = new RandomAccessFile(filePath, "r");
                    openReadFileNameAndPath = filePath;
                } catch (IOException | NullPointerException ioe){
                    writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command\n\t"+ioe.getMessage());
                    return;
                }
            }
        }
        //----------------------- Actual transfer chain ------------------------
        try{
            var chunk = new byte[(int)size];
            int bytesRead;

            if (randAccessFile == null){
                splitReader.seek(offset);
                bytesRead = splitReader.read(chunk);   // How many bytes we got?
            }
            else {
                randAccessFile.seek(offset);
                bytesRead = randAccessFile.read(chunk); // How many bytes we got?
            }

            if (bytesRead != (int) size) {    // Let's check that we read expected size
                writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' command [CMD]" +
                        "\n         At offset: " + offset +
                        "\n         Requested: " + size +
                        "\n         Received:  " + bytesRead);
                return;
            }
            writeGL_PASS(longToArrLE(size), "GL Handle 'ReadFile' command [CMD]"); // Reporting result
            writeUsb(chunk, "GL Handle 'ReadFile' command");    // Bypassing bytes we read total // FIXME: move failure message into method
        }
        catch (Exception ioe){
            closeOpenedReadFilesGl();
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'ReadFile' transfer chain\n\t"+ioe.getMessage());
        }
    }
    /**
     * Handle 'WriteFile'
     * @param glFileName full path including new file name in the end
     * @return true - failed, false - passed
     * */
    void writeFile(String glFileName) throws Exception{
        if (glFileName.startsWith("VIRT:/")) {
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'WriteFile' command [not supported for virtual drive]");
            return;
        }

        glFileName = decodeGlPath(glFileName);
        // Check if this file being used during this session
        if (! writeFilesMap.containsKey(glFileName)){
            try{                                     // If this file exists GL will take care; Otherwise, let's add it
                writeFilesMap.put(glFileName,
                        new BufferedOutputStream(new FileOutputStream(glFileName, true))); // Open what we have to open
            } catch (IOException ioe){
                writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'WriteFile' command [IOException]\n\t"+ioe.getMessage());
                return;
            }
        }

        var transferredData = readGL_file();

        if (transferredData == null){
            print("GL Handle 'WriteFile' command [1/1]", FAIL);
            return;
        }
        try{
            writeFilesMap.get(glFileName).write(transferredData, 0, transferredData.length);
        }
        catch (IOException ioe){
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL Handle 'WriteFile' command [1/1]\n\t"+ioe.getMessage());
            return;
        }
        // Report we're good
        writeGL_PASS("GL Handle 'WriteFile' command");
    }

    /**
     * Handle 'SelectFile'
     * @return true - failed, false - passed
     * */
    protected void selectFile() throws Exception {
        var selectedFile = CompletableFuture.supplyAsync(() -> {
            var fChooser = new FileChooser();
            fChooser.setTitle(MediatorControl.INSTANCE.getResourceBundle().getString("btn_OpenFile"));   // TODO: FIX BAD IMPLEMENTATION
            fChooser.setInitialDirectory(new File(System.getProperty("user.home")));                         // TODO: Consider fixing; not a priority.
            fChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("*", "*"));
            return fChooser.showOpenDialog(null);    // Leave as is for now.
        }, Platform::runLater).join();

        if (selectedFile == null){    // Nothing selected
            this.selectedFile = null;
            writeGL_FAIL(SELECTION_CANCELLED, "GL Handle 'SelectFile' command: Nothing selected");
            return;
        }

        var selectedFileNameBytes = ("SPEC:/"+selectedFile.getName()).getBytes(UTF_8);
        var command = Arrays.asList(
                intToArrLE(selectedFileNameBytes.length),
                selectedFileNameBytes);

        this.selectedFile = null;
        writeGL_PASS(command, "GL Handle 'SelectFile' command");

        this.selectedFile = selectedFile;
    }

    /*--------------------------------*/
    /*           GL HELPERS           */
    /*--------------------------------*/
    /**
     * Convert path received from GL to host-default structure
     */
    protected String decodeGlPath(String glPath){
        if (isWindows)
            glPath = glPath.replace('/', '\\');
        return homePath + glPath.substring(5);     // e.g. HOME:/some/file/
    }

    private boolean isFileAndNotHidden(File parent, String child){
            var entry = new File(parent, child);
            return (! entry.isDirectory()) && (nspFilter ?
                                                child.toLowerCase().endsWith(".nsp") :
                                                ! entry.isHidden());
    }

    private boolean isDirectoryAndNotHidden(File parent, String child){
        var dir = new File(parent, child);
        return (dir.isDirectory() && ! dir.isHidden());
    }

    private boolean notExistsOrDirectory(File pathDir){
        return (! pathDir.exists() ) || (! pathDir.isDirectory());
    }

    /**
     * Close files opened for read/write
     */
    protected void closeOpenedReadFilesGl(){
        if (openReadFileNameAndPath != null){
            closeRAFandSplitReader();
            openReadFileNameAndPath = null;
            randAccessFile = null;
            splitReader = null;
        }
    }
    protected void closeRAFandSplitReader(){
        closeRAF();
        try{
            if (splitReader != null)
                splitReader.close();
        }
        catch (Exception e){
            print("Unable to close: "+openReadFileNameAndPath+"\n\t"+e.getMessage(), WARNING);
        }
    }
    protected void closeRAF(){
        try{
            if (randAccessFile != null)
                randAccessFile.close();
        }
        catch (Exception e){
            print("Unable to close: "+openReadFileNameAndPath+"\n\t"+e.getMessage(), WARNING);
        }
    }
    /*----------------------------------------------------*/
    /*           GL READ/WRITE USB SPECIFIC               */
    /*----------------------------------------------------*/

    protected byte[] readUsb() throws Exception {
        var readBuffer = ByteBuffer.allocateDirect(PACKET_SIZE);
        var readBufTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            int result = LibUsb.bulkTransfer(handlerNS, IN_EP, readBuffer, readBufTransferred, 1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    var receivedBytes = new byte[readBufTransferred.get()];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    closeOpenedReadFilesGl();       // Could be a problem if GL glitches and slow down process. Or if user has extra-slow SD card. TODO: refactor?
                    continue;
                default:
                    throw new Exception("Data transfer issue [read]" +
                            "\n         Returned: " + LibUsb.errorName(result)+
                            "\n         (execution stopped)");
            }
        }
        throw new InterruptedException("Execution interrupted");
    }
    private byte[] readGL_file() {
        var readBuffer = ByteBuffer.allocateDirect(8388608); // Just don't ask..
        var rBufferTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled() ) {
            int result = LibUsb.bulkTransfer(handlerNS,
                    IN_EP,
                    readBuffer,
                    rBufferTransferred,
                    1000);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

            switch (result) {
                case LibUsb.SUCCESS:
                    var receivedBytes = new byte[rBufferTransferred.get()];
                    readBuffer.get(receivedBytes);
                    return receivedBytes;
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    print("GL Data transfer issue [read]" +
                            "\n         Returned: " + LibUsb.errorName(result) +
                            "\n         GL Execution stopped", FAIL);
                    return null;
            }
        }
        print("GL Execution interrupted", INFO);
        return null;
    }

    protected void writeGL_PASS(String onFailureText) throws Exception {
        writeUsb(CMD_GLCO_SUCCESS, onFailureText);
    }
    protected void writeGL_PASS(byte[] message, String onFailureText) throws Exception {
        writeUsb(ByteBuffer.allocate(PACKET_SIZE)
                .put(CMD_GLCO_SUCCESS_FLAG)
                .put(message)
                .array(), onFailureText);
    }
    protected void writeGL_PASS(List<byte[]> messages, String onFailureText) throws Exception {
        var writeBuffer = ByteBuffer.allocate(PACKET_SIZE)
                .put(CMD_GLCO_SUCCESS_FLAG);
        messages.forEach(writeBuffer::put);
        writeUsb(writeBuffer.array(), onFailureText);
    }

    protected void writeGL_FAIL(byte[] failurePacket, String failureMessage) throws Exception {
        writeUsb(failurePacket, failureMessage);
        print(failureMessage, WARNING);
    }
    /**
     * Sending anything to USB device
     * @param message is payload
     * @param operation is operation description
     * */
    private void writeUsb(byte[] message, String operation) throws Exception {
        var wBufferTransferred = IntBuffer.allocate(1);

        while (! task.isCancelled()) {
            int result = LibUsb.bulkTransfer(handlerNS,
                    OUT_EP,
                    ByteBuffer.allocateDirect(message.length).put(message), // order -> BIG_ENDIAN; Don't writeBuffer.rewind();
                    wBufferTransferred,
                    1000);  // TIMEOUT. 0 stands for infinite

            switch (result){
                case LibUsb.SUCCESS:
                    if (wBufferTransferred.get() == message.length)
                        return;
                    print(operation +
                            "\n         Data transfer issue [write]" +
                            "\n         Requested: " + message.length +
                            "\n         Transferred: " + wBufferTransferred.get(), FAIL);
                    throw new LibUsbException("Transferred amount of data mismatch", LibUsb.SUCCESS);
                case LibUsb.ERROR_TIMEOUT:
                    continue;
                default:
                    print(operation +
                            "\n         Data transfer issue [write]" +
                            "\n         Returned: " + LibUsb.errorName(result) +
                            "\n         GL Execution stopped", FAIL);
                    throw new LibUsbException(result);
            }
        }
        throw new InterruptedException("Execution interrupted");
    }
}
