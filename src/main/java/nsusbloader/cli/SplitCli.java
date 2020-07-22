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
package nsusbloader.cli;

import nsusbloader.Utilities.splitmerge.SplitTask;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SplitCli {

    private String[] arguments;
    private String saveTo;
    private String[] files;

    SplitCli(String[] arguments) throws InterruptedException, IncorrectSetupException{
        this.arguments = arguments;
        checkArguments();
        parseArguments();
        printFilesForSplit();
        runBackend();
    }

    private void checkArguments() throws IncorrectSetupException{
        if (arguments == null || arguments.length == 0) {
            throw new IncorrectSetupException("No arguments.\n" +
                    "Try 'ns-usbloader -s help' for more information.");
        }

        if (arguments.length == 1){
            if (isHelpDirective(arguments[0])){
                showHelp();
                return;
            }

            throw new IncorrectSetupException("Not enough arguments.\n" +
                    "Try 'ns-usbloader -s help' for more information.");
        }

        this.saveTo = arguments[0];
        File saveToFile = new File(saveTo);
        if (! saveToFile.exists() || saveToFile.isFile()){
            throw new IncorrectSetupException("First argument must be existing directory.");
        }
    }
    private boolean isHelpDirective(String argument){
        return argument.equals("help");
    }
    private void showHelp() throws IncorrectSetupException{
        throw new IncorrectSetupException("Usage:\n"
                + "\tns-usbloader -s <SAVE_TO_DIR> <FILE>...\n"
                + "\n\nOptions:"
                + "\n\tSAVE_TO_DIR\tWhere results should be saved"
                + "\n\tFILE\t\tOne or more files to split");
    }

    private void parseArguments() throws IncorrectSetupException{
        List<String> files = new ArrayList<>();
        for (int i = 1; i < arguments.length; i++){
            File file = new File(arguments[i]);
            if (file.isFile())
                files.add(file.getAbsolutePath());
        }

        if (files.isEmpty()){
            throw new IncorrectSetupException("No files specified.\n" +
                    "Try 'ns-usbloader -s help' for more information.");
        }

        this.files = files.toArray(new String[0]);
    }

    private void printFilesForSplit(){
        System.out.println("Next files will be splitted:");
        for (String f : this.files)
            System.out.println("  "+f);
    }

    private void runBackend() throws InterruptedException{
        for (String filePath : files){
            Runnable splitTaks = new SplitTask(filePath, saveTo);
            Thread thread = new Thread(splitTaks);
            thread.setDaemon(true);
            thread.start();
            thread.join();
        }
    }
}
