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

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.*;
import java.util.Arrays;

public class MergeTask extends CancellableRunnable {

    private final ILogPrinter logPrinter;
    private final String saveToPath;
    private final String filePath;

    private File splitFile;

    private File[] chunkFiles;
    private long chunksTotalSize;
    private File resultFile;

    public MergeTask(String filePath, String saveToPath) {
        this.filePath = filePath;
        this.saveToPath = saveToPath;
        logPrinter = Log.getPrinter(EModule.SPLIT_MERGE_TOOL);
    }

    @Override
    public void run() {
        try {
            logPrinter.print("Merge file: " + filePath, EMsgType.INFO);
            splitFile = new File(filePath);

            collectChunks();
            validateChunks();
            sortChunks();
            calculateChunksSizeSum();

            createFile();
            mergeChunksToFile();
            validateFile();

            logPrinter.print(".:: Merge complete ::.", EMsgType.INFO);
            logPrinter.updateOneLinerStatus(true);
            logPrinter.close();
        }
        catch (Exception e){
            logPrinter.print(e.getMessage(), EMsgType.FAIL);
            logPrinter.updateOneLinerStatus(false);
            logPrinter.close();
        }
    }

    private void collectChunks(){
        chunkFiles = splitFile.listFiles((file, s) -> s.matches("^[0-9][0-9]$"));
    }

    private void validateChunks() throws Exception{
        if (chunkFiles == null || chunkFiles.length == 0){
            throw new Exception("Selected folder doesn't have any chunks. Nothing to do here.");
        }
    }

    private void sortChunks(){
        Arrays.sort(chunkFiles);
    }

    private void calculateChunksSizeSum(){
        logPrinter.print("Next files will be merged in following order: ", EMsgType.INFO);
        for (File cnk : chunkFiles){
            logPrinter.print("    "+cnk.getName(), EMsgType.INFO);
            chunksTotalSize += cnk.length();
        }
    }

    private void createFile() throws Exception{
        final String splitFileName = splitFile.getName();

        resultFile = new File(saveToPath+File.separator+"!_"+splitFileName);

        for (int i = 0; i < 50 ; i++){
            if (isCancelled()){
                throw new InterruptedException("Split task interrupted!");
            }

            if (resultFile.exists()){
                logPrinter.print("Trying to create a good new file...", EMsgType.WARNING);
                resultFile = new File(saveToPath+File.separator+"!_"+i+"_"+splitFileName);
                continue;
            }

            logPrinter.print("Save results to: "+resultFile.getAbsolutePath(), EMsgType.INFO);
            return;
        }
        throw new Exception("Can't create new file.");
    }

    private void mergeChunksToFile() throws Exception{
        double chunkPercent = (4194240.0 / (chunksTotalSize / 100.0) / 100.0);
        long totalSizeCnt = 0;

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(resultFile));

        BufferedInputStream bis;
        byte[] chunk;
        int readBytesCnt;

        for (File chunkFile : chunkFiles){
            bis = new BufferedInputStream(new FileInputStream(chunkFile));
            while (true){

                if (isCancelled()){
                    bos.close();
                    bis.close();
                    boolean isDeleted = resultFile.delete();
                    throw new InterruptedException("Merge task interrupted and file "
                            + (isDeleted ? "deleted." : "is not deleted."));
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
    }

    private void validateFile() throws Exception{
        long resultFileSize = resultFile.length();
        logPrinter.print("Total chunks size: " + chunksTotalSize, EMsgType.INFO);
        logPrinter.print("Merged file size:  " + resultFileSize, EMsgType.INFO);

        if (chunksTotalSize != resultFileSize)
            throw new Exception("Sizes are different! Do NOT use this file for installations!");

        logPrinter.print("Sizes are the same! Resulting file should be good!", EMsgType.PASS);
    }
}
