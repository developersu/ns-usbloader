/*
    Copyright 2019-2020 Dmitry Isaenko, DarkMatterCore

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
package nsusbloader.Utilities.nxdumptool;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class NxdtNspFile {
    private final String name;
    private final int headerSize;
    private final long fullSize;
    private long nspRemainingSize;
    private final File file;

    NxdtNspFile(String name, int headerSize, long fullSize, File file) throws Exception{
        this.name = name;
        this.headerSize = headerSize;
        this.fullSize = fullSize;
        this.file = file;
        this.nspRemainingSize = fullSize - headerSize;

        removeIfExists();
        createHeaderFiller();
    }
    private void removeIfExists() throws Exception{
        if (! file.exists())
            return;

        if (file.delete())
            return;

        throw new Exception("Unable to delete leftovers of the NSP file: "+name);
    }
    private void createHeaderFiller() throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")){
            raf.setLength(headerSize);
        }
        catch (IOException e){
            throw new Exception("Unable to reserve space for NSP file's header: "+e.getMessage());
        }
    }

    public String getName() { return name; }
    public int getHeaderSize() { return headerSize; }
    public long getFullSize() { return fullSize; }
    public File getFile() { return file; }
    public long getNspRemainingSize() { return nspRemainingSize; }

    public void setNspRemainingSize(long nspRemainingSize) { this.nspRemainingSize = nspRemainingSize; }
}
