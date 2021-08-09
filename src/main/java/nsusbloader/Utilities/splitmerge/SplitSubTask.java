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
import java.util.concurrent.ExecutionException;

public class SplitSubTask implements Callable<Boolean> {
    private final int id;
    private final String saveToPath;

    private final File file;
    private File splitFile;
    private long originalFileLen;
    private final MultithreadingPrintAdapter printAdapter;

    SplitSubTask(int id, File file, String saveToPath, MultithreadingPrintAdapter printAdapter){
        this.id = id;
        this.file = file;
        this.saveToPath = saveToPath;
        this.printAdapter = printAdapter;
    }

    @Override
    public Boolean call(){
        try {
            createSplitFile();
            splitFileToChunks();
            validateSplitFile();
            return true;
        }
        catch (InterruptedException | ExecutionException ie){
            ie.printStackTrace();
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

    private void createSplitFile() throws Exception{
        if ( interrupted())
            throw new Exception("Split task interrupted!");

        splitFile = new File(saveToPath+File.separator+"!_"+file.getName());

        for (int i = 0; i < 50; i++){
            if (splitFile.mkdirs()){
                printAdapter.print("["+id+"] Save results to: "+splitFile.getAbsolutePath(), EMsgType.INFO);
                return;
            }

            if (splitFile.exists()){
                printAdapter.print("["+id+"] Trying to create a good new folder...", EMsgType.WARNING);
                splitFile = new File(saveToPath+File.separator+"!_"+i+"_"+file.getName());
                continue;
            }

            throw new Exception("Folder " + splitFile.getAbsolutePath()
                    + " could not be created. Not enough rights or something like that?");
        }
        throw new Exception("Can't create new file.");
    }

    private void splitFileToChunks() throws Exception{
        try(BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))){
            long counter;

            originalFileLen = file.length();
            printAdapter.reportFileSize(originalFileLen);

            byte[] chunk;
            int readBytesCnt;

            main_loop:
            for (int i = 0; ; i++){
                String pathname = splitFile.getAbsolutePath()+File.separator+String.format("%02d", i);
                BufferedOutputStream fragmentBos = new BufferedOutputStream(new FileOutputStream(pathname));

                counter = 0;

                while (counter < 1024){      // 0xffff0000 total
                    chunk = new byte[4194240];

                    if ((readBytesCnt = bis.read(chunk)) < 4194240){
                        if (readBytesCnt > 0)
                            fragmentBos.write(chunk, 0, readBytesCnt);
                        fragmentBos.close();
                        printAdapter.updateProgressBySize(readBytesCnt);
                        break main_loop;
                    }
                    if (interrupted())
                        throw new InterruptedException();

                    fragmentBos.write(chunk);
                    counter++;
                    printAdapter.updateProgressBySize(readBytesCnt);
                }
                fragmentBos.close();
            }
        }
    }

    private void validateSplitFile() throws Exception{
        if (interrupted())
            throw new Exception("Split task interrupted!");

        printAdapter.print("["+id+"] Original file: "+splitFile.getAbsolutePath()+" (size: "+originalFileLen+")", EMsgType.INFO);

        long totalChunksSize = 0;
        File[] chunkFileArr = splitFile.listFiles();

        if (chunkFileArr == null)
            throw new Exception("Unable to check results. It means that something went wrong.");

        Arrays.sort(chunkFileArr);

        StringBuilder stringBuilder = new StringBuilder("["+id+"] Chunks");

        for (File chunkFile : chunkFileArr) {
            stringBuilder.append("\n");
            stringBuilder.append("         ");
            stringBuilder.append(chunkFile.getName());
            stringBuilder.append(" size: ");
            stringBuilder.append(chunkFile.length());
            totalChunksSize += chunkFile.length();
        }
        stringBuilder.append("\n");
        stringBuilder.append("Total chunks size: ");
        stringBuilder.append(totalChunksSize);

        printAdapter.print(stringBuilder.toString(), EMsgType.INFO);

        if (originalFileLen != totalChunksSize)
            throw new Exception("Sizes are different! Do NOT use this file for installations!");

        printAdapter.print("["+id+"] Sizes are the same! Split file should be good!", EMsgType.PASS);
    }

    private void cleanup(){
        boolean isDeleted = splitFile.delete();
        File[] chunksToDelete = splitFile.listFiles();
        if (! isDeleted && chunksToDelete != null){
            isDeleted = true;
            for (File chunkFile : chunksToDelete)
                isDeleted &= chunkFile.delete();
            isDeleted &= splitFile.delete();
        }
        try {
        printAdapter.print(
                "["+id+"] Split task interrupted and folder "
                        + (isDeleted?"deleted.":"is NOT deleted.")
                , EMsgType.FAIL);
        }
        catch (InterruptedException ignore) {}
    }
    private boolean interrupted(){
        return Thread.interrupted();  // NOTE: it's not isInterrupted(); And it's handled properly for now.
    }
}
