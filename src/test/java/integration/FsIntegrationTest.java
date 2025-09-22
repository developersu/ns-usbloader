/* Copyright WTFPL */
package integration;

import nsusbloader.NSLMain;
import nsusbloader.Utilities.patches.fs.FsPatchMaker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

@Disabled
public class FsIntegrationTest {
    static String pathToFirmware;
    static String pathToFirmwares;
    static String pathToKeysFile;
    static String saveTo;

    @BeforeAll
    static void init() throws Exception{
        NSLMain.isCli = true;
        Environment environment = new Environment();
        pathToKeysFile = environment.getProdkeysLocation();
        saveTo = environment.getSaveToLocation() + File.separator + "FS_LPR";
        pathToFirmwares = environment.getFirmwaresLocation();
        pathToFirmware = environment.getFirmwaresLocation() + File.separator + "Firmware 15.0.0";
    }

    @DisplayName("FS Integration validation - everything")
    @Test
    void makeFss() throws Exception{
        File[] fwDirs = new File(pathToFirmwares).listFiles((file, s) ->
                s.matches("^Firmware (9\\.|[0-9][0-9]\\.).*") && ! s.endsWith(".zip"));
        assert fwDirs != null;
        Arrays.sort(fwDirs);
        Arrays.stream(fwDirs).forEach(System.out::println);
        for (File dir : fwDirs){
            System.out.println("\n\t\t\t"+dir.getName());
            FsPatchMaker fsPatchMaker = new FsPatchMaker(dir.getAbsolutePath(), pathToKeysFile, saveTo);
            Thread workThread = new Thread(fsPatchMaker);
            workThread.start();
            workThread.join();
        }
    }

    @Disabled
    @DisplayName("FS Integration validation - one particular firmware")
    @Test
    void makeFs() throws Exception{
        System.out.println(pathToFirmware);
        FsPatchMaker fsPatchMaker = new FsPatchMaker(pathToFirmware, pathToKeysFile, saveTo);
        Thread workThread = new Thread(fsPatchMaker);
        workThread.start();
        workThread.join();
    }
}
