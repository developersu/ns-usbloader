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

public class MergeTask extends Task<Boolean> {

    private final ILogPrinter logPrinter;
    private final String saveToPath;
    private final String filePath;

    public MergeTask(String filePath, String saveToPath) {
        this.filePath = filePath;
        this.saveToPath = saveToPath;
        logPrinter = Log.getPrinter(EModule.SPLIT_MERGE_TOOL);
    }
    @Override
    protected Boolean call() {
        logPrinter.print("Merge file: "+filePath, EMsgType.INFO);

        File folder = new File(filePath);

        long cnkTotalSize = 0;

        File[] chunkFiles = folder.listFiles((file, s) -> s.matches("^[0-9][0-9]$"));

        if (chunkFiles == null || chunkFiles.length == 0){
            logPrinter.print("Selected folder doesn't have any chunks. Nothing to do here.", EMsgType.FAIL);
            logPrinter.close();
            return false;
        }

        Arrays.sort(chunkFiles);

        logPrinter.print("Next files will be merged in following order: ", EMsgType.INFO);
        for (File cnk : chunkFiles){
            logPrinter.print("    "+cnk.getName(), EMsgType.INFO);
            cnkTotalSize += cnk.length();
        }

        double chunkPercent = (4194240.0 / (cnkTotalSize / 100.0) / 100.0);
        long totalSizeCnt = 0;

        File resultFile = new File(saveToPath+File.separator+"!_"+folder.getName());
        //*******
        for (int i = 0;  ; i++){
            if (this.isCancelled()){
                logPrinter.print("Split task interrupted!", EMsgType.PASS);
                logPrinter.close();
                return false;
            }

            if (resultFile.exists()){
                if (i >= 50){
                    logPrinter.print("Can't create new file.", EMsgType.FAIL);
                    logPrinter.close();
                    return false;
                }

                logPrinter.print("Trying to create a good new file...", EMsgType.WARNING);
                resultFile = new File(saveToPath+File.separator+"!_"+i+"_"+folder.getName());
                continue;
            }
            logPrinter.print("Save results to: "+resultFile.getAbsolutePath(), EMsgType.INFO);
            break;
        }
        //*******

        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(resultFile));

            BufferedInputStream bis;
            byte[] chunk;
            int readBytesCnt;

            for (File cnk : chunkFiles){
                bis = new BufferedInputStream(new FileInputStream(cnk));
                while (true){

                    if (this.isCancelled()){
                        bos.close();
                        bis.close();
                        boolean isDeleted = resultFile.delete();
                        logPrinter.print("Split task interrupted and file "+(isDeleted?"deleted.":"is not deleted."), EMsgType.PASS);
                        logPrinter.close();
                        return false;
                    }

                    chunk = new byte[4194240];
                    readBytesCnt = bis.read(chunk);

                    logPrinter.updateProgress(chunkPercent * totalSizeCnt);
                    totalSizeCnt++;

                    if (readBytesCnt < 4194240){
                        if (readBytesCnt > 0)
                            bos.write(chunk, 0, readBytesCnt);
                        break;
                    }

                    bos.write(chunk);
                }
                bis.close();
            }
            bos.close();
            //=============== let's check what we have ==============
            long resultFileSize = resultFile.length();
            logPrinter.print("Total chunks size: " + cnkTotalSize, EMsgType.INFO);
            logPrinter.print("Merged file size:  " + resultFileSize, EMsgType.INFO);

            if (cnkTotalSize != resultFileSize){
                logPrinter.print("Sizes are different! Do NOT use this file for installations!", EMsgType.FAIL);
                return false;
            }
            logPrinter.print("Sizes are the same! Split file should be good!", EMsgType.PASS);
        }
        catch (Exception e){
            e.printStackTrace();
            logPrinter.print("Error: "+e.getMessage(), EMsgType.FAIL);
        }

        logPrinter.print("Merge task complete!", EMsgType.INFO);
        logPrinter.close();
        return true;
    }
}
