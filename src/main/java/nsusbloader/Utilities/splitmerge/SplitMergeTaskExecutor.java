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

import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.ModelControllers.Log;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
/*
* TODO: Kill this on application exit (?)
*/
public class SplitMergeTaskExecutor implements Runnable {
    private final boolean isSplit;
    private final List<File> files;
    private final String saveToPath;
    private final ILogPrinter logPrinter;
    private final ExecutorService executorService;

    private final MultithreadingPrintAdapter printAdapter;

    public SplitMergeTaskExecutor(boolean isSplit, List<File> files, String saveToPath){
        this.isSplit = isSplit;
        this.files = files;
        this.saveToPath = saveToPath;
        this.logPrinter = Log.getPrinter(EModule.SPLIT_MERGE_TOOL);
        this.executorService = Executors.newFixedThreadPool(
                files.size(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setDaemon(true);
                    return thread;
                });
        this.printAdapter = new MultithreadingPrintAdapter(logPrinter);
    }

    public void run(){
        try {
            List<Future<Boolean>> futuresResults = executorService.invokeAll(getSubTasksCollection());
            boolean onelinerResult = true;
            for (Future<Boolean> future : futuresResults){
                onelinerResult &= future.get();
            }
            executorService.shutdown();
            logPrinter.updateOneLinerStatus(onelinerResult);
        }
        catch (InterruptedException ie){
            //ie.printStackTrace();
            executorService.shutdownNow();
            boolean interruptedSuccessfully = false;
            try {
                interruptedSuccessfully = executorService.awaitTermination(20, TimeUnit.SECONDS);
            }
            catch (InterruptedException awaitInterrupt){
                print("Force interrupting task...", EMsgType.WARNING);
            }
            logPrinter.updateOneLinerStatus(false);
            print((
                    isSplit?
                        "Split tasks interrupted ":
                        "Merge tasks interrupted ")+
                    (interruptedSuccessfully?
                        "successfully":
                        "with some issues"), EMsgType.WARNING);
        }
        catch (Exception e){
            logPrinter.updateOneLinerStatus(false);
            print(
                    isSplit?
                    "Split task failed: ":
                    "Merge task failed: "+e.getMessage(), EMsgType.FAIL);
            e.printStackTrace();
        }

        print(
                isSplit?
                ".:: Split complete ::.":
                ".:: Merge complete ::.", EMsgType.INFO);
        logPrinter.close();
    }
    private List<Callable<Boolean>> getSubTasksCollection() throws InterruptedException{
        List<Callable<Boolean>> subTasks = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();

        // TODO: Optimize?

        if (isSplit){
            stringBuilder.append("Split files:\n");
            for (int i = 0; i < files.size(); i++){
                File file = files.get(i);
                stringBuilder.append("[");
                stringBuilder.append(i);
                stringBuilder.append("] ");
                stringBuilder.append(file.getName());
                stringBuilder.append("\n");
                Callable<Boolean> task = new SplitSubTask(i, file, saveToPath, printAdapter);
                subTasks.add(task);
            }
        }
        else {
            stringBuilder.append("Merge files:\n");
            for (int i = 0; i < files.size(); i++){
                File file = files.get(i);
                stringBuilder.append("[");
                stringBuilder.append(i);
                stringBuilder.append("] ");
                stringBuilder.append(file.getName());
                stringBuilder.append("\n");
                Callable<Boolean> task = new MergeSubTask(i, file, saveToPath, printAdapter);
                subTasks.add(task);
            }
        }

        logPrinter.print(stringBuilder.toString(), EMsgType.INFO);

        return subTasks;
    }

    private void print(String message, EMsgType type){
        try {
            logPrinter.print(message, type);
        }
        catch (InterruptedException ie){
            ie.printStackTrace();
        }
    }
}