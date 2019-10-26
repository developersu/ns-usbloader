package nsusbloader.COM.USB.PFS;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Data class to hold NCA, tik, xml etc. meta-information
 * */
public class NCAFile {
    //private int ncaNumber;
    private byte[] ncaFileName;
    private long ncaOffset;
    private long ncaSize;

    //public void setNcaNumber(int ncaNumber){ this.ncaNumber = ncaNumber; }
    void setNcaFileName(byte[] ncaFileName) { this.ncaFileName = ncaFileName; }
    void setNcaOffset(long ncaOffset) { this.ncaOffset = ncaOffset; }
    void setNcaSize(long ncaSize) { this.ncaSize = ncaSize; }

    //public int getNcaNumber() {return this.ncaNumber; }
    public byte[] getNcaFileName() { return ncaFileName; }
    public byte[] getNcaFileNameLength() { return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ncaFileName.length).array(); }
    public long getNcaOffset() { return ncaOffset; }
    public long getNcaSize() { return ncaSize; }
}
