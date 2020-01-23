package nsusbloader.Utilities;

import javafx.concurrent.Task;
import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.*;
import java.util.Arrays;

public class SplitMergeTool {
    // TODO:  ADD ABILITY TO INTERRUPT PROCESS

    public static Task<Void> splitFile(String filePath, String saveToPath){
        LogPrinter logPrinter = new LogPrinter();
        return new Task<Void>() {
            @Override
            protected Void call() {
                File file = new File(filePath);
                File folder = new File(saveToPath+File.separator+"!_"+file.getName());

                logPrinter.print("Split file:      "+filePath, EMsgType.INFO);

                for (int i = 0; i < 50; i++){
                    if (! folder.mkdir()){
                        if (folder.exists()){
                            logPrinter.print("Trying to create a good new folder...", EMsgType.WARNING);
                            folder = new File(saveToPath+File.separator+"!_"+i+"_"+file.getName());
                            continue;
                        }
                        else {  // folder not created and not exists - return
                            logPrinter.print("Folder "+folder.getAbsolutePath()+" could not be created. Not enough rights or something like that?", EMsgType.FAIL);
                            logPrinter.close();
                            return null;
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
                        return null;
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

                return null;
            }
        };
    };

    // TODO: CHECK IF FILE WE'RE ABOUT TO CREATE IS EXISTS !!!
    // TODO: not here: Add RECENT on current session level selection of the 'Select file/select folder' ? Already done ?

    public static Task<Void> mergeFile(String filePath, String saveToPath){
        LogPrinter logPrinter = new LogPrinter();

        return new Task<Void>() {
            @Override
            protected Void call() {
                logPrinter.print("Merge file:      "+filePath, EMsgType.INFO);

                File folder = new File(filePath);

                long cnkTotalSize = 0;

                File[] chunkFiles = folder.listFiles((file, s) -> s.matches("^[0-9][0-9]$"));

                if (chunkFiles == null){
                    logPrinter.print("Selected folder doesn't have any chunks. Nothing to do here.", EMsgType.FAIL);
                    logPrinter.close();
                    return null;
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

                logPrinter.print("Save results to: "+resultFile.getAbsolutePath(), EMsgType.INFO);
                try {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(resultFile));

                    BufferedInputStream bis;
                    byte[] chunk;
                    int readBytesCnt;

                    for (File cnk : chunkFiles){
                        bis = new BufferedInputStream(new FileInputStream(cnk));
                        while (true){
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

                    if (cnkTotalSize != resultFileSize)
                        logPrinter.print("Sizes are different! Do NOT use this file for installations!", EMsgType.FAIL);
                    else
                        logPrinter.print("Sizes are the same! Split file should be good!", EMsgType.PASS);
                }
                catch (Exception e){
                    e.printStackTrace();
                    logPrinter.print("Error: "+e.getMessage(), EMsgType.FAIL);
                }

                logPrinter.print("Merge task complete!", EMsgType.INFO);
                logPrinter.close();
                return null;
            }
        };
    }
}
