package nsusbloader.COM.USB;

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.LogPrinterCli;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.usb4java.DeviceHandle;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferModuleTest{

    @TempDir
    File testFilesLocation;

    final String regularFileName1 = "file1.nsp";
    final String regularFileName2 = "file2.nsz";
    final String regularFileName3 = "file3.xci";
    final String regularFileName4 = "file4.xcz";
    final String splitFileName1 = "splitFile1.nsp";
    final String splitFileName2 = "splitFile2.nsp";
    final String splitFileName3 = "splitFile3.nsp";
    final String splitFileName4 = "splitFile4.nsp";
    final String splitFileName5 = "splitFile5.nsp";
    final String splitFileName6 = "splitFile6.nsp";
    final String splitFileName7 = "splitFile7.nsp";
    final String splitFileName8 = "splitFile8.nsp";
    final String splitFileName9 = "splitFile9.nsp";
    final String splitFileName10 = "splitFile10.nsp";
    final String splitFileName11 = "splitFile11.nsp";

    TransferModuleImplementation transferModule;

    @BeforeEach
    void createFiles() throws Exception{
        String parentTempDirectory = testFilesLocation.getAbsolutePath();

        File regularFile1 = createFile(parentTempDirectory, regularFileName1);
        File regularFile2 = createFile(parentTempDirectory, regularFileName2);
        File regularFile3 = createFile(parentTempDirectory, regularFileName3);
        File regularFile4 = createFile(parentTempDirectory, regularFileName4);
        File splitFile1 = createSplitInvalidEmpty(parentTempDirectory, splitFileName1);
        File splitFile2 = createSplitInvalidEmpty(parentTempDirectory, splitFileName2);
        File splitFile3 = createSplitValid(parentTempDirectory, splitFileName3);
        File splitFile4 = createSplitValid(parentTempDirectory, splitFileName4);
        File splitFile5 = createSplitValidWithExtras(parentTempDirectory, splitFileName5);
        File splitFile6 = createSplitInvalidVariant1(parentTempDirectory, splitFileName6);
        File splitFile7 = createSplitInvalidVariant2(parentTempDirectory, splitFileName7);
        File splitFile8 = createSplitInvalidVariant3(parentTempDirectory, splitFileName8);
        File splitFile9 = createSplitInvalidVariant4(parentTempDirectory, splitFileName9);
        File splitFile10 = createSplitInvalidVariant5(parentTempDirectory, splitFileName10);
        File splitFile11 = createSplitValidSingleChunk(parentTempDirectory, splitFileName11);


        LinkedHashMap<String, File> filesMap = new LinkedHashMap<>();

        filesMap.put(regularFileName1, regularFile1);
        filesMap.put(regularFileName2, regularFile2);
        filesMap.put(regularFileName3, regularFile3);
        filesMap.put(regularFileName4, regularFile4);
        filesMap.put(splitFileName1, splitFile1);
        filesMap.put(splitFileName2, splitFile2);
        filesMap.put(splitFileName3, splitFile3);
        filesMap.put(splitFileName4, splitFile4);
        filesMap.put(splitFileName5, splitFile5);
        filesMap.put(splitFileName6, splitFile6);
        filesMap.put(splitFileName7, splitFile7);
        filesMap.put(splitFileName8, splitFile8);
        filesMap.put(splitFileName9, splitFile9);
        filesMap.put(splitFileName10, splitFile10);
        filesMap.put(splitFileName11, splitFile11);

        ILogPrinter printer = new LogPrinterCli();
        this.transferModule = new TransferModuleImplementation((DeviceHandle)null, filesMap, (CancellableRunnable)null, printer);
    }

    File createFile(String parent, String name) throws Exception{
        Path file = Paths.get(parent, name);
        Files.createFile(file);
        return new File(parent, name);
    }

    File createSplitInvalidEmpty(String parent, String name) throws Exception{
        Path file = Paths.get(parent, name);
        Files.createDirectory(file);
        return new File(parent, name);
    }

    File createSplitValid(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntryBigger(path, 0);
        makeSplitFileEntryBigger(path, 1);
        makeSplitFileEntryBigger(path, 2);
        makeSplitFileEntryBigger(path, 3);
        makeSplitFileEntrySmaller(path, 4);
        return new File(parent, name);
    }

    File createSplitValidWithExtras(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntrySmaller(path, 0);
        makeSplitFileEntrySmaller(path, 1);
        makeSplitFileEntrySmaller(path, 2);
        makeSplitFileEntryWeired(path);
        return new File(parent, name);
    }

    File createSplitInvalidVariant1(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntrySmaller(path, 0);
        makeSplitFileEntrySmaller(path, 1);
        makeSplitFileEntryBigger(path, 2); //incorrect
        makeSplitFileEntrySmaller(path, 3);
        return new File(parent, name);
    }
    File createSplitInvalidVariant2(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntryBigger(path, 0); //incorrect
        makeSplitFileEntrySmaller(path, 1);
        makeSplitFileEntrySmaller(path, 2);
        makeSplitFileEntrySmaller(path, 3);
        return new File(parent, name);
    }
    File createSplitInvalidVariant3(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntrySmaller(path, 0);
        makeSplitFileEntryBigger(path, 1); //incorrect
        makeSplitFileEntrySmaller(path, 2);
        makeSplitFileEntrySmaller(path, 3);
        return new File(parent, name);
    }
    File createSplitInvalidVariant4(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntrySmaller(path, 0); //incorrect
        makeSplitFileEntryBigger(path, 1);
        makeSplitFileEntryBigger(path, 2);
        makeSplitFileEntryBigger(path, 3);
        return new File(parent, name);
    }
    File createSplitInvalidVariant5(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntrySmaller(path, 0);
        makeSplitFileEntrySmaller(path, 1);
        makeSplitFileEntrySmaller(path, 2);
        makeSplitFileEntryBigger(path, 3); //incorrect: Could be only smaller
        return new File(parent, name);
    }

    File createSplitValidSingleChunk(String parent, String name) throws Exception{
        Path path = Paths.get(parent, name);
        Files.createDirectory(path);
        makeSplitFileEntryBigger(path, 0);
        return new File(parent, name);
    }

    void makeSplitFileEntrySmaller(Path path, int entryNum) throws Exception{
        try (FileWriter writer = new FileWriter(String.format("%s%s%02x", path.toString(), File.separator, entryNum))){
            writer.write("test");
            writer.flush();
        }
    }
    void makeSplitFileEntryBigger(Path path, int entryNum) throws Exception{
        try (FileWriter writer = new FileWriter(String.format("%s%s%02x", path.toString(), File.separator, entryNum))){
            writer.write("test_");
            writer.flush();
        }
    }
    void makeSplitFileEntryWeired(Path path) throws Exception{
        try (FileWriter writer = new FileWriter(String.format("%s%sNOT_A_VALID_FILE.nsp", path.toString(), File.separator))){
            writer.write("literally anything");
            writer.flush();
        }
    }

    private static class TransferModuleImplementation extends TransferModule{
        TransferModuleImplementation(DeviceHandle handler,
                                     LinkedHashMap<String, File> nspMap,
                                     CancellableRunnable task,
                                     ILogPrinter printer)
        {
            super(handler, nspMap, task, printer);
        }

        LinkedHashMap<String, File>  getFiles(){ return nspMap; }
    }

    @DisplayName("Test 'split-files' filter-validator")
    @Test
    void validateTransferModule() {
        LinkedHashMap<String, File> files = transferModule.getFiles();

        assertTrue(files.containsKey(regularFileName1));
        assertTrue(files.containsKey(regularFileName2));
        assertTrue(files.containsKey(regularFileName3));
        assertTrue(files.containsKey(regularFileName4));
        assertFalse(files.containsKey(splitFileName1));
        assertFalse(files.containsKey(splitFileName1));
        assertFalse(files.containsKey(splitFileName2));
        assertTrue(files.containsKey(splitFileName3));
        assertTrue(files.containsKey(splitFileName4));
        assertTrue(files.containsKey(splitFileName5));
        assertFalse(files.containsKey(splitFileName6));
        assertFalse(files.containsKey(splitFileName7));
        assertFalse(files.containsKey(splitFileName8));
        assertFalse(files.containsKey(splitFileName9));
        assertFalse(files.containsKey(splitFileName10));
        assertTrue(files.containsKey(splitFileName11));
    }
}