package nsusbloader.COM.Helpers;

import java.io.*;

/**
 *  Handle Split files
 * */
public class NSSplitReader implements Closeable  {

    private final String splitFileDir;
    private final long referenceSplitChunkSize;

    private byte subFileNum;
    private long curPosition;
    private BufferedInputStream biStream;

    public NSSplitReader(File file, long seekToPosition) throws IOException, NullPointerException {
        this.splitFileDir = file.getAbsolutePath()+File.separator;
        File subFile = new File(file.getAbsolutePath()+File.separator+"00");
        if (! file.exists())
            throw new FileNotFoundException("File not found on "+file.getAbsolutePath()+File.separator+"00");
        this.referenceSplitChunkSize = subFile.length();
        this.subFileNum = (byte) (seekToPosition / referenceSplitChunkSize);
        this.biStream = new BufferedInputStream(new FileInputStream(splitFileDir + String.format("%02d", subFileNum)));
        this.curPosition = seekToPosition;

        seekToPosition -= referenceSplitChunkSize * subFileNum;

        if (seekToPosition != biStream.skip(seekToPosition))
            throw new IOException("Unable to seek to requested position of "+seekToPosition+" for file "+splitFileDir+String.format("%02d", subFileNum));
    }

    public long seek(long position) throws IOException{

        byte subFileRequested = (byte) (position / referenceSplitChunkSize);

        if ((subFileRequested != this.subFileNum) || (curPosition > position)) {
            biStream.close();
            biStream = new BufferedInputStream(new FileInputStream(splitFileDir + String.format("%02d", subFileRequested)));
            this.subFileNum = subFileRequested;
            this.curPosition = referenceSplitChunkSize * subFileRequested;
        }

        long retVal = biStream.skip(position - curPosition);

        retVal += curPosition;
        this.curPosition = position;
        return retVal;
    }

    public int read(byte[] readBuffer) throws IOException, NullPointerException {
        final int requested = readBuffer.length;
        int readPrtOne;

        if ( (curPosition + requested) <= (referenceSplitChunkSize * (subFileNum+1))) {
            if ((readPrtOne = biStream.read(readBuffer)) < 0 )
                return readPrtOne;
            curPosition += readPrtOne;
            return readPrtOne;
        }

        int partOne = (int) (referenceSplitChunkSize * (subFileNum+1) - curPosition);
        int partTwo = requested - partOne;
        int readPrtTwo;

        if ( (readPrtOne = biStream.read(readBuffer, 0, partOne)) < 0)
            return readPrtOne;

        curPosition += readPrtOne;

        if (readPrtOne != partOne)
            return readPrtOne;

        biStream.close();
        subFileNum += 1;
        biStream = new BufferedInputStream(new FileInputStream(splitFileDir + String.format("%02d", subFileNum)));

        if ( (readPrtTwo = biStream.read(readBuffer, partOne, partTwo) ) < 0)
            return readPrtTwo;

        curPosition += readPrtTwo;

        return readPrtOne + readPrtTwo;
    }

    @Override
    public void close() throws IOException {
        if (biStream != null)
            biStream.close();
    }
}
