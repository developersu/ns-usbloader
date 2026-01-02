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
package nsusbloader.com.helpers;

import java.io.*;

import static java.io.File.separator;

/**
 *  Handle Split files
 * */
public class NSSplitReader extends InputStream  {

    private final String splitFileDir;
    private final long referenceSplitChunkSize;

    private byte subFileNum;
    private long curPosition;
    private BufferedInputStream inStream;

    public NSSplitReader(File file, long seekToPosition) throws IOException, NullPointerException {
        this.splitFileDir = file.getAbsolutePath()+ separator;
        var subFile = new File(file.getAbsolutePath()+separator+"00");
        if (! file.exists())
            throw new FileNotFoundException("File not found on "+file.getAbsolutePath()+separator+"00");
        this.referenceSplitChunkSize = subFile.length();
        this.subFileNum = (byte) (seekToPosition / referenceSplitChunkSize);
        this.inStream = new BufferedInputStream(new FileInputStream(splitFileDir + String.format("%02d", subFileNum)));
        this.curPosition = seekToPosition;

        seekToPosition -= referenceSplitChunkSize * subFileNum;

        if (seekToPosition != inStream.skip(seekToPosition))
            throw new IOException("Unable to seek to requested position of "+seekToPosition+" for file "+splitFileDir+String.format("%02d", subFileNum));
    }

    public long seek(long position) throws IOException{
        byte subFileRequested = (byte) (position / referenceSplitChunkSize);

        if ((subFileRequested != this.subFileNum) || (curPosition > position)) {
            inStream.close();
            inStream = new BufferedInputStream(new FileInputStream(splitFileDir + String.format("%02d", subFileRequested)));
            subFileNum = subFileRequested;
            curPosition = referenceSplitChunkSize * subFileRequested;
        }

        long retVal = inStream.skip(position - curPosition);
        curPosition = position;
        return retVal+curPosition;
    }

    @Override
    public int read(byte[] readBuffer) throws IOException, NullPointerException {
        var requested = readBuffer.length;
        int readPrtOne;

        if ( (curPosition + requested) <= (referenceSplitChunkSize * (subFileNum+1))) {
            if ((readPrtOne = inStream.read(readBuffer)) < 0 )
                return readPrtOne;
            curPosition += readPrtOne;
            return readPrtOne;
        }

        int partOne = (int) (referenceSplitChunkSize * (subFileNum+1) - curPosition);
        int partTwo = requested - partOne;
        int readPrtTwo;

        if ( (readPrtOne = inStream.read(readBuffer, 0, partOne)) < 0)
            return readPrtOne;

        curPosition += readPrtOne;

        if (readPrtOne != partOne)
            return readPrtOne;

        inStream.close();
        subFileNum += 1;
        inStream = new BufferedInputStream(new FileInputStream(splitFileDir + String.format("%02d", subFileNum)));

        if ( (readPrtTwo = inStream.read(readBuffer, partOne, partTwo) ) < 0)
            return readPrtTwo;

        curPosition += readPrtTwo;

        return readPrtOne + readPrtTwo;
    }

    @Override
    public void close() throws IOException {
        if (inStream != null)
            inStream.close();
    }

    @Override
    public long skip(long n) throws IOException {
        throw new IOException("Not supported, try using seek(...) instead");
    }

    @Override
    public int read() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public void skipNBytes(long n) throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public int available() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        throw new IOException("Not supported");
    }
}
