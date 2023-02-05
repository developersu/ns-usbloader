/*
    Copyright 2018-2022 Dmitry Isaenko
     
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
package nsusbloader.Utilities.patches.loader;

import libKonogonka.Converter;
import libKonogonka.fs.other.System2.ini1.KIP1Provider;
import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.Utilities.patches.SimplyFind;

import java.io.BufferedInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class LoaderPatchMaker extends CancellableRunnable {

    private final ILogPrinter logPrinter;
    private final String atmosphereLocation;
    private final String saveTo;

    private String package3Location;
    private KIP1Provider loaderProvider;

    private boolean oneLinerStatus = false;

    public LoaderPatchMaker(String atmosphereLocation, String saveTo){
        this.logPrinter = Log.getPrinter(EModule.PATCHES);
        /*
        this.logPrinter = new ILogPrinter() {
            public void print(String message, EMsgType type) throws InterruptedException {}
            public void updateProgress(Double value) throws InterruptedException {}
            public void update(HashMap<String, File> nspMap, EFileStatus status) {}
            public void update(File file, EFileStatus status) {}
            public void updateOneLinerStatus(boolean status) {}
            public void close() {}
        };
        //*/
        this.atmosphereLocation = atmosphereLocation;
        this.saveTo = saveTo;
    }

    @Override
    public void run() {
        try {
            logPrinter.print("..:: Make Loader Patches ::..", EMsgType.INFO);
            checkPackage3();
            createLoaderKip1Provider();
            makePatches();
        }
        catch (Exception e){
            e.printStackTrace();
            try{
                logPrinter.print(e.getMessage(), EMsgType.FAIL);
            } catch (Exception ignore){}
        }
        finally {
            logPrinter.updateOneLinerStatus(oneLinerStatus);
            logPrinter.close();
        }
    }
    private void checkPackage3() throws Exception{
        logPrinter.print("Looking at Atmosphere", EMsgType.INFO);
        if (Files.notExists(Paths.get(atmosphereLocation)))
            throw new Exception("Atmosphere directory does not exist at " + atmosphereLocation);

        package3Location = atmosphereLocation +File.separator+"package3";
        if (Files.exists(Paths.get(package3Location)))
            return;

        package3Location = atmosphereLocation +File.separator+"fusee-secondary.bin";
        if (Files.notExists(Paths.get(package3Location)))
            throw new Exception("package3 / fusee-secondary.bin file not found at " + atmosphereLocation);
    }

    private void createLoaderKip1Provider() throws Exception{
        Path package3Path = Paths.get(package3Location);

        try (BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(package3Path))) {
            byte[] data = new byte[0x400];
            if (0x400 != stream.read(data))
                throw new Exception("Failed to read first 0x400 bytes of package3 / fusee-secondary file.");

            SimplyFind simplyFind = new SimplyFind(".6f61646572", data); // eq. '.oader'
            List<Integer> results = simplyFind.getResults();
            if (results.size() == 0)
                throw new Exception("Failed to find 'Loader' offset at package3 / fusee-secondary file.");

            int offset = results.get(0);
            int kip1Offset = Converter.getLEint(data, offset - 0x10);
            int kip1Size = Converter.getLEint(data, offset - 0xC);

            loaderProvider = new KIP1Provider(package3Location, kip1Offset);

            if (kip1Size != loaderProvider.getSize())
                throw new Exception("Incorrect calculations for KIP1. PK31 value: "+kip1Size+"KIP1Provider value: "+loaderProvider.getSize());
            logPrinter.print("Loader KIP1 found", EMsgType.PASS);
        }
    }
    private void makePatches() throws Exception{
        new LoaderPatch(loaderProvider, saveTo, logPrinter);
        oneLinerStatus = true;
    }
}