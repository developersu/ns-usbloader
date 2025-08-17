/* Copyright WTFPL */
package integration;

import nsusbloader.NSLMain;
import nsusbloader.Utilities.patches.loader.LoaderPatchMaker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

//@Disabled
public class LoaderIntegrationTest {
    static String pathToAtmo;
    static String saveTo;

    @BeforeAll
    static void init() throws Exception{
        NSLMain.isCli = true;
        Environment environment = new Environment();
        saveTo = environment.getSaveToLocation() + File.separator + "Loader_LPR";
        pathToAtmo = environment.getAtmosphereLocation();
    }

    @DisplayName("Loader Integration validation")
    @Test
    void makeLoader() throws Exception{
        System.out.println(pathToAtmo);
        LoaderPatchMaker patchMaker = new LoaderPatchMaker(pathToAtmo, saveTo);
        Thread workThread = new Thread(patchMaker);
        workThread.start();
        workThread.join();
    }
}
