/*
    Copyright 2019-2021 Dmitry Isaenko

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

import nsusbloader.NSLDataTypes.EMsgType;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class MergeSubTask implements Callable<Boolean> {
    private final int id;
    private final String saveToPath;
    private final MultithreadingPrintAdapter printAdapter;
    private final File splitFile;

    private File[] chunkFiles;
    private long chunksTotalSize;
    private File resultFile;

    public MergeSubTask(int id, File splitFile, String saveToPath, MultithreadingPrintAdapter printAdapter){
        this.id = id;
        this.splitFile = splitFile;
        this.saveToPath = saveToPath;
        this.printAdapter = printAdapter;
    }

    @Override
    public Boolean call(){
        try{
            collectChunks();
            validateChunks();
            sortChunks();
            calculateChunksSizeSum();

            createFile();
            mergeChunksToFile();
            validateFile();
            return true;
        }
        catch (InterruptedException ie){
            cleanup();
            return false;
        }
        catch (Exception e){
            e.printStackTrace();
            try {
                printAdapter.print("["+id+"] "+e.getMessage(), EMsgType.FAIL);
            }
            catch (InterruptedException ignore) {}
            return false;
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

    private void calculateChunksSizeSum() throws InterruptedException{
        StringBuilder builder = new StringBuilder("["+id+"] Next files will be merged in following order: ");

        for (File cnk : chunkFiles){
            builder.append(cnk.getName());
            builder.append(" ");
            chunksTotalSize += cnk.length();
        }
        printAdapter.print(builder.toString(), EMsgType.INFO);
    }

    private void createFile() throws Exception{
        final String splitFileName = splitFile.getName();

        resultFile = new File(saveToPath+File.separator+"!_"+splitFileName);

        for (int i = 0; i < 50 ; i++){
            if (interrupted())
                throw new InterruptedException();

            if (resultFile.exists()){
                printAdapter.print("["+id+"] Trying to create a good new file...", EMsgType.WARNING);
                resultFile = new File(saveToPath+File.separator+"!_"+i+"_"+splitFileName);
                continue;
            }

            printAdapter.print("["+id+"] Save results to: "+resultFile.getAbsolutePath(), EMsgType.INFO);
            return;
        }
        throw new Exception("Can't create new file.");
    }

    private void mergeChunksToFile() throws Exception{
        if ( interrupted())
            throw new InterruptedException();

        try(BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(resultFile))){
            BufferedInputStream bis;
            byte[] chunk;
            int readBytesCnt;

            printAdapter.reportFileSize(chunksTotalSize);

            for (File chunkFile : chunkFiles){
                bis = new BufferedInputStream(new FileInputStream(chunkFile));
                while (true){
                    chunk = new byte[4194240];
                    readBytesCnt = bis.read(chunk);

                    if (readBytesCnt < 4194240){
                        if (readBytesCnt > 0)
                            bos.write(chunk, 0, readBytesCnt);
                        break;
                    }

                    if (interrupted())
                        throw new InterruptedException();

                    bos.write(chunk);

                    printAdapter.updateProgressBySize(readBytesCnt);
                }
                bis.close();
            }
        }
    }

    private void validateFile() throws Exception{
        if ( interrupted())
            throw new Exception("Merge task interrupted!");

        long resultFileSize = resultFile.length();
        printAdapter.print("["+id+"] Total chunks size: " + chunksTotalSize
                                    +"\n         Merged file size:  " + resultFileSize, EMsgType.INFO);

        if (chunksTotalSize != resultFileSize)
            throw new Exception("Sizes are different! Do NOT use this file for installations!");

        printAdapter.print("["+id+"] Sizes are the same! Resulting file should be good!", EMsgType.PASS);
    }

    private void cleanup(){
        boolean isDeleted = resultFile.delete();
        try {
            printAdapter.print(
                "[" + id + "] Merge task interrupted and file "
                        + (isDeleted ? "deleted." : "is NOT deleted."), EMsgType.FAIL);
        }
        catch (InterruptedException ignore) {}
    }
    private boolean interrupted(){
        return Thread.interrupted();  // NOTE: it's not isInterrupted(); And it's handled properly for now.
    }
}
