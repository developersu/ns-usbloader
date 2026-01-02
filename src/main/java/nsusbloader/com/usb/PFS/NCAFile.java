/*
    Copyright 2019-2026 Dmitry Isaenko

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
package nsusbloader.com.usb.PFS;

import static nsusbloader.com.DataConvertUtils.intToArrLE;

/**
 * Data class to hold NCA, tik, XML etc. meta-information
 * */
public class NCAFile {
    //private int ncaNumber;
    private byte[] ncaFileName;
    private byte[] ncaFileNameLength;
    private long ncaOffset;
    private long ncaSize;

    //public void setNcaNumber(int ncaNumber){ this.ncaNumber = ncaNumber; }
    void setNcaFileName(byte[] ncaFileName) {
        this.ncaFileName = ncaFileName;
        this.ncaFileNameLength = intToArrLE(ncaFileName.length);
    }
    void setNcaOffset(long ncaOffset) {
        this.ncaOffset = ncaOffset;
    }
    void setNcaSize(long ncaSize) {
        this.ncaSize = ncaSize;
    }

    //public int getNcaNumber() {return this.ncaNumber; }
    public byte[] getNcaFileName() { return ncaFileName; }
    public byte[] getNcaFileNameLength() { return ncaFileNameLength; }
    public long getNcaOffset() { return ncaOffset; }
    public long getNcaSize() { return ncaSize; }
}
