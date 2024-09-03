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

import nsusbloader.com.usb.UsbCommunications;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TinfoilUsbCli {

    private final String[] arguments;
    private List<File> filesList;

    TinfoilUsbCli(String[] arguments) throws InterruptedException, IncorrectSetupException{
        this.arguments = arguments;
        checkArguments();
        parseFilesArguments();
        runTinfoilBackend();
    }

    private void checkArguments() throws IncorrectSetupException{
        if (arguments == null || arguments.length == 0) {
            throw new IncorrectSetupException("No files?\n" +
                    "Try 'ns-usbloader -h' for more information.");
        }
    }

    private void parseFilesArguments() throws IncorrectSetupException{
        filesList = new ArrayList<>();
        File file;

        for (String arg : arguments) {
            file = new File(arg);
            if (!file.exists()) {
                continue;
            }

            if (file.isDirectory()) {
                filesList.addAll(fillListOfFiles(file));
            } else {
                filesList.add(file);
            }
        }

        if (filesList.size() == 0) {
            throw new IncorrectSetupException("File(s) doesn't exist.\n" +
                    "Try 'ns-usbloader -n help' for more information.");
        }
    }

    public List<File> fillListOfFiles(File rootDir) {
        try (Stream<Path> stream = Files.walk(rootDir.toPath())) {
            return stream
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

    private void runTinfoilBackend() throws InterruptedException{
        Runnable task = new UsbCommunications(filesList, "TinFoil", false);
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
        thread.join();
    }
}
