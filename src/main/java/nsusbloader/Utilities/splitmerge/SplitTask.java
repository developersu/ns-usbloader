/*
    Copyright 2019-2020 Dmitry Isaenko

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
package nsusbloader.Utilities.splitmerge;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.*;
import java.util.Arrays;

public class SplitTask extends Task<Boolean> {

    private final ILogPrinter logPrinter;
    private final String saveToPath;
    private final String filePath;

    public SplitTask(String filePath, String saveToPath){
        this.filePath = filePath;
        this.saveToPath = saveToPath;
        logPrinter = Log.getPrinter(EModule.SPLIT_MERGE_TOOL);
    }

    @Override
    protected Boolean call() {
        File file = new File(filePath);
        File folder = new File(saveToPath+File.separator+"!_"+file.getName());

        logPrinter.print("Split file:      "+filePath, EMsgType.INFO);

        for (int i = 0; ; i++){
            if (this.isCancelled()){
                logPrinter.print("Split task interrupted!", EMsgType.PASS);
                logPrinter.close();
                return false;
            }
            if (! folder.mkdir()){
                if (folder.exists()){
                    if (i >= 50){
                        logPrinter.print("Can't create new file.", EMsgType.FAIL);
                        logPrinter.close();
                        return false;
                    }
                    logPrinter.print("Trying to create a good new folder...", EMsgType.WARNING);
                    folder = new File(saveToPath+File.separator+"!_"+i+"_"+file.getName());
                    continue;
                }
                else {  // folder not created and not exists - return
                    logPrinter.print("Folder "+folder.getAbsolutePath()+" could not be created. Not enough rights or something like that?", EMsgType.FAIL);
                    logPrinter.close();
                    return false;
                }
            }
            logPrinter.print("Save results to: "+folder.getAbsolutePath(), EMsgType.INFO);
            break;
        }

        try{
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

            BufferedOutputStream fragmentBos;

            long counter;

            long originalFileLen = file.length();

            double chunkPercent = (4194240.0 / (originalFileLen / 100.0) / 100.0);
            long totalSizeCnt = 0;

            byte[] chunk;
            int readBytesCnt;

            main_loop:
            for (int i = 0; ; i++){
                fragmentBos = new BufferedOutputStream(
                        new FileOutputStream(new File(folder.getAbsolutePath()+File.separator+String.format("%02d", i)))
                );

                counter = 0;

                while (counter < 1024){      // 0xffff0000 total

                    if (this.isCancelled()){
                        fragmentBos.close();
                        bis.close();
                        boolean isDeleted = folder.delete();
                        File[] chArrToDel = folder.listFiles();
                        if (! isDeleted && chArrToDel != null){
                            isDeleted = true;
                            for (File chunkFile : chArrToDel)
                                isDeleted &= chunkFile.delete();
                            isDeleted &= folder.delete();
                        }
                        logPrinter.print("Split task interrupted and folder "+(isDeleted?"deleted.":"is not deleted."), EMsgType.PASS);
                        logPrinter.close();
                        return false;
                    }

                    chunk = new byte[4194240];

                    if ((readBytesCnt = bis.read(chunk)) < 4194240){
                        if (readBytesCnt > 0)
                            fragmentBos.write(chunk, 0, readBytesCnt);
                        fragmentBos.close();
                        logPrinter.updateProgress(1.0);
                        break main_loop;
                    }

                    fragmentBos.write(chunk);

                    logPrinter.updateProgress(chunkPercent * totalSizeCnt);
                    counter++;          // NOTE: here we have some redundancy of variables. It has to be fixed one day.
                    totalSizeCnt++;
                }
                fragmentBos.close();
            }

            bis.close();

            //=============== let's check what we have ==============
            logPrinter.print("Original file size: "+originalFileLen, EMsgType.INFO);
            long totalChunksSize = 0;
            File[] chunkFileArr = folder.listFiles();

            if (chunkFileArr == null) {
                logPrinter.print("Unable to check results. It means that something went wrong.", EMsgType.FAIL);
                return false;
            }
            else {
                Arrays.sort(chunkFileArr);
                for (File chunkFile : chunkFileArr) {
                    logPrinter.print("Chunk " + chunkFile.getName() + " size: " + chunkFile.length(), EMsgType.INFO);
                    totalChunksSize += chunkFile.length();
                }

                logPrinter.print("Total chunks size: " + totalChunksSize, EMsgType.INFO);

                if (originalFileLen != totalChunksSize)
                    logPrinter.print("Sizes are different! Do NOT use this file for installations!", EMsgType.FAIL);
                else
                    logPrinter.print("Sizes are the same! Split file should be good!", EMsgType.PASS);
            }
        }
        catch (Exception e){
            e.printStackTrace();
            logPrinter.print("Error: "+e.getMessage(), EMsgType.FAIL);
        }
        logPrinter.print("Split task complete!", EMsgType.INFO);
        logPrinter.close();

        return true;
    }
}