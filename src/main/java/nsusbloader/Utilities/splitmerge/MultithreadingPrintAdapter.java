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

import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;

public class MultithreadingPrintAdapter {
    private final ILogPrinter printer;
    private long totalFilesSize;
    private long bytesComplete;

    public MultithreadingPrintAdapter(ILogPrinter printer){
        this.printer = printer;
    }

    public void print(String message, EMsgType type) throws InterruptedException{
        printer.print(message, type);
    }

    public void reportFileSize(long fileSize){
        totalFilesSize += fileSize;
    }
    public void updateProgressBySize(long chunkSize) throws InterruptedException{
        bytesComplete += chunkSize;
        printer.updateProgress((double) bytesComplete / (double) totalFilesSize);
    }
}
