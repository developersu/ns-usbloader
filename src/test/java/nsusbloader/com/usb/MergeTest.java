package nsusbloader.com.usb;

import nsusbloader.NSLMain;
import nsusbloader.Utilities.splitmerge.SplitMergeTaskExecutor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MergeTest {
    private List<File> files;
    @TempDir
    File testFilesLocation;
    @TempDir
    File saveToPath;

    static Random random;

    @BeforeAll
    static void init(){
        NSLMain.isCli = true;
        MergeTest.random = new Random();
    }

    void makeSplittedFiles() throws Exception{
        String parentLocation = testFilesLocation.getAbsolutePath();
        this.files = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            files.add(createSplitFile(parentLocation, "TestFile4Merge_" + i));
        }
    }
    File createSplitFile(String parent, String name) throws Exception{
        Path file = Paths.get(parent, name);
        File newlyCreatedSplitFile = Files.createDirectory(file).toFile();

        Assertions.assertTrue(newlyCreatedSplitFile.exists());
        Assertions.assertTrue(newlyCreatedSplitFile.canRead());
        Assertions.assertTrue(newlyCreatedSplitFile.canWrite());

        int chunksCount = random.nextInt(6 + 1) + 1; // At min = 1, max = 6
        populateSplittedFile(newlyCreatedSplitFile, chunksCount);

        return newlyCreatedSplitFile;
    }
    void populateSplittedFile(File splitFileContainer, int chunksCount) throws Exception{
        int chunkSize = random.nextInt(8192 + 1) + 8192; // At min = 8192, max = 8192*2

        for (int i = 0; i < chunksCount; i++){
            File chunkFile = new File(splitFileContainer, String.format("%02d", i));
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(chunkFile))){
                byte[] zero = new byte[chunkSize];
                bos.write(zero);
            }

            Assertions.assertTrue(chunkFile.exists());
            Assertions.assertTrue(chunkFile.canRead());
        }
    }

    @DisplayName("Test test-files location for merge")
    @Test
    void testTempLocation(){
        Assertions.assertTrue(testFilesLocation.isDirectory());
    }

//    @Disabled("Current test is not ready")
    @DisplayName("Test merge functionality")
    //@Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
    @Test
    void testMerge() throws Exception{
        makeSplittedFiles();
        SplitMergeTaskExecutor splitMergeTaskExecutor = new SplitMergeTaskExecutor(false, files, saveToPath.getAbsolutePath());
        Thread thread = new Thread(splitMergeTaskExecutor);
        thread.setDaemon(true);
        thread.start();
        thread.join();
    }
}
