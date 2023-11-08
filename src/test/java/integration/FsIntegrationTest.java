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
        pathToFirmware = pathToFirmware + File.separator + "Firmware 13.0.0";
    }

    @DisplayName("FS Integration validation - everything")
    @Test
    void makeFss() throws Exception{
        File[] fwDirs = new File(pathToFirmwares).listFiles((file, s) -> {
            return (s.matches("^Firmware (9\\.|[0-9][0-9]\\.).*") && ! s.endsWith(".zip"));
            //return s.matches("^Firmware 10.0.1.*");
        });
        assert fwDirs != null;
        Arrays.sort(fwDirs);

        for (File dir : fwDirs){
            System.out.println("\n\t\t\t"+dir.getName());
            FsPatchMaker fsPatchMaker = new FsPatchMaker(dir.getAbsolutePath(), pathToKeysFile, saveTo);
            Thread workThread = new Thread(fsPatchMaker);
            workThread.start();
            workThread.join();
        }
    }

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
