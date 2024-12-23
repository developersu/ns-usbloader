/* Copyright WTFPL */
package integration;

import nsusbloader.NSLMain;
import nsusbloader.Utilities.patches.es.EsPatchMaker;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.Arrays;

@Disabled
public class EsIntegrationTest {
    static String pathToFirmware;
    static String pathToFirmwares;
    static String pathToKeysFile;
    static String saveTo;

    @BeforeAll
    static void init() throws Exception{
        NSLMain.isCli = true;
        Environment environment = new Environment();
        pathToKeysFile = environment.getProdkeysLocation();
        saveTo = environment.getSaveToLocation() + File.separator + "ES_LPR";
        pathToFirmwares = environment.getFirmwaresLocation();
        pathToFirmware = environment.getFirmwaresLocation() + File.separator + "Firmware 17.0.0";
    }

    @DisplayName("ES Integration validation - everything")
    @Test
    void makeEss() throws Exception{
        File[] fwDirs = new File(pathToFirmwares).listFiles((file, s) -> {
            return s.matches("^Firmware (9\\.|[0-9][0-9]\\.).*");
            //return s.matches("^Firmware 10.0.1.*");
        });
        assert fwDirs != null;
        Arrays.sort(fwDirs);
        for (File dir : fwDirs){
            EsPatchMaker esPatchMaker = new EsPatchMaker(dir.getAbsolutePath(), pathToKeysFile, saveTo);
            Thread workThread = new Thread(esPatchMaker);
            workThread.start();
            workThread.join();
        }
    }

    @DisplayName("ES Integration validation - one particular firmware")
    @Test
    void makeEs() throws Exception{
        EsPatchMaker esPatchMaker = new EsPatchMaker(pathToFirmware, pathToKeysFile, saveTo);
        Thread workThread = new Thread(esPatchMaker);
        workThread.start();
        workThread.join();
    }
}
