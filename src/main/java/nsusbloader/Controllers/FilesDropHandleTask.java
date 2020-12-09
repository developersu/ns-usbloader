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
package nsusbloader.Controllers;

import javafx.concurrent.Task;
import nsusbloader.MediatorControl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilesDropHandleTask extends Task<List<File>> {
    private final String filesRegex;
    private final String foldersRegex;

    private final List<File> filesDropped;
    private final List<File> allFiles;

    private String messageTemplate;
    private long filesScanned = 0;
    private long filesAdded = 0;

    FilesDropHandleTask(List<File> files,
                        String filesRegex,
                        String foldersRegex) {
        this.filesDropped = files;
        this.filesRegex = filesRegex;
        this.foldersRegex = foldersRegex;
        this.allFiles = new ArrayList<>();
        this.messageTemplate = MediatorControl.getInstance().getResourceBundle().getString("windowBodyFilesScanned");
    }

    @Override
    protected List<File> call() {
        if (filesDropped == null || filesDropped.size() == 0)
            return allFiles;

        for (File file : filesDropped){
            if (isCancelled())
                return new ArrayList<>();
            collectFiles(file);
            updateMessage(String.format(messageTemplate, filesScanned++, filesAdded));
        }

        return allFiles;
    }

    private void collectFiles(File startFolder) {
        if (startFolder == null)
            return;

        final String startFolderNameInLowercase = startFolder.getName().toLowerCase();

        if (startFolder.isFile()) {
            if (startFolderNameInLowercase.matches(filesRegex)) {
                allFiles.add(startFolder);
                filesAdded++;
            }
            return;
        }

        if (startFolderNameInLowercase.matches(foldersRegex)) {
            allFiles.add(startFolder);
            filesAdded++;
            return;
        }

        File[] files = startFolder.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (isCancelled())
                return;
            collectFiles(file);
            updateMessage(String.format(messageTemplate, filesScanned++, filesAdded));
        }
    }

}
