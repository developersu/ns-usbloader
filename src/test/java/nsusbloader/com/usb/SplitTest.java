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

class SplitTest {
    private List<File> files;
    @TempDir
    File testFilesLocation;
    @TempDir
    File saveToPath;

    static Random random;

    @BeforeAll
    static void init(){
        NSLMain.isCli = true;
        SplitTest.random = new Random();
    }

    void makeRegularFiles() throws Exception {
        String parentLocation = testFilesLocation.getAbsolutePath();
        Assertions.assertTrue(Files.exists(Paths.get(parentLocation)));
        this.files = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            files.add(createRegularFile(parentLocation, "TestFile4Split_" + i));
        }
    }
    File createRegularFile(String parent, String name) throws Exception{
        Path file = Paths.get(parent, name);
        File newlyCreatedFile = Files.createFile(file).toFile();

        Assertions.assertTrue(newlyCreatedFile.exists());
        Assertions.assertTrue(newlyCreatedFile.canRead());
        Assertions.assertTrue(newlyCreatedFile.canWrite());

        int randomValue = random.nextInt(8192 + 1) + 8192; // At min = 8192, max = 8192*2
        fulfillFile(newlyCreatedFile, randomValue);

        return newlyCreatedFile;
    }
    void fulfillFile(File file, int fileSize) throws Exception{
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            byte[] zero = new byte[fileSize];
            bos.write(zero);
        }
    }

    @DisplayName("Test test-files location for split")
    @Test
    void testTempLocation(){
        Assertions.assertTrue(testFilesLocation.isDirectory());
    }

    @DisplayName("Test split functionality")
    //@Timeout(value = 60000, unit = TimeUnit.MILLISECONDS)
    @Test
    void testSplit() throws Exception{
        makeRegularFiles();
        SplitMergeTaskExecutor splitMergeTaskExecutor = new SplitMergeTaskExecutor(true, files, saveToPath.getAbsolutePath());
        Thread thread = new Thread(splitMergeTaskExecutor);
        thread.setDaemon(true);
        thread.start();
        thread.join();
    }
}
