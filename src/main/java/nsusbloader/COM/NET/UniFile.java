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
package nsusbloader.COM.NET;

import java.io.File;

class UniFile {
    private final long size;
    private final File file;

    UniFile(File file) {
        this.file = file;

        if (file.isFile()) {
            size = file.length();
        }
        else {
            long fSize = 0;
            File[] subFiles = file.listFiles((myFile, name) -> name.matches("[0-9]{2}"));
            for (File subFile : subFiles)
                fSize += subFile.length();
            size = fSize;
        }
    }

    public long getSize() { return size; }
    public File getFile() { return file; }
}
