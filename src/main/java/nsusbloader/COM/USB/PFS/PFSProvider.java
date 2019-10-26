package nsusbloader.COM.USB.PFS;

import nsusbloader.ModelControllers.LogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Used in GoldLeaf USB protocol
 * */
public class PFSProvider {
    private static final byte[] PFS0 = new byte[]{0x50, 0x46, 0x53, 0x30};  // PFS0

    private String nspFileName;
    private NCAFile[] ncaFiles;
    private long bodySize;
    private int ticketID = -1;

    public PFSProvider(File nspFile, LogPrinter logPrinter) throws Exception{
        if (nspFile.isDirectory()) {
            nspFileName = nspFile.getName();
            nspFile = new File(nspFile.getAbsolutePath() + File.separator + "00");
        }
        else
            nspFileName = nspFile.getName();

        RandomAccessFile randAccessFile = new RandomAccessFile(nspFile, "r");

        int filesCount;
        int header;

        logPrinter.print("PFS Start NSP file analyze for ["+nspFileName+"]", EMsgType.INFO);

        byte[] fileStartingBytes = new byte[12];
        // Read PFS0, files count, header, padding (4 zero bytes)
        if (randAccessFile.read(fileStartingBytes) == 12)
            logPrinter.print("PFS Read file starting bytes.", EMsgType.PASS);
        else {
            logPrinter.print("PFS Read file starting bytes.", EMsgType.FAIL);
            randAccessFile.close();
            throw new Exception("Unable to read file starting bytes");
        }
        // Check PFS0
        if (Arrays.equals(PFS0, Arrays.copyOfRange(fileStartingBytes, 0, 4)))
            logPrinter.print("PFS Read 'PFS0'.", EMsgType.PASS);
        else
            logPrinter.print("PFS Read 'PFS0': this file looks wired.", EMsgType.WARNING);
        // Get files count
        filesCount = ByteBuffer.wrap(Arrays.copyOfRange(fileStartingBytes, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (filesCount > 0 ) {
            logPrinter.print("PFS Read files count [" + filesCount + "]", EMsgType.PASS);
        }
        else {
            logPrinter.print("PFS Read files count", EMsgType.FAIL);
            randAccessFile.close();
            throw new Exception("Unable to read file count");
        }
        // Get header
        header = ByteBuffer.wrap(Arrays.copyOfRange(fileStartingBytes, 8, 12)).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (header > 0 )
            logPrinter.print("PFS Read header ["+header+"]", EMsgType.PASS);
        else {
            logPrinter.print("PFS Read header ", EMsgType.FAIL);
            randAccessFile.close();
            throw new Exception("Unable to read header");
        }
        //*********************************************************************************************
        // Create NCA set
        this.ncaFiles = new NCAFile[filesCount];
        // Collect files from NSP
        byte[] ncaInfoArr = new byte[24];   // should be unsigned long, but.. java.. u know my pain man

        HashMap<Integer, Long> ncaNameOffsets = new LinkedHashMap<>();

        int offset;
        long nca_offset;
        long nca_size;
        long nca_name_offset;

        for (int i=0; i<filesCount; i++){
            if (randAccessFile.read(ncaInfoArr) == 24) {
                logPrinter.print("PFS Read NCA inside NSP: " + i, EMsgType.PASS);
            }
            else {
                logPrinter.print("PFS Read NCA inside NSP: "+i, EMsgType.FAIL);
                randAccessFile.close();
                throw new Exception("Unable to read NCA inside NSP");
            }
            offset =  ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            nca_offset = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 4, 12)).order(ByteOrder.LITTLE_ENDIAN).getLong();
            nca_size = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 12, 20)).order(ByteOrder.LITTLE_ENDIAN).getLong();
            nca_name_offset = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 20, 24)).order(ByteOrder.LITTLE_ENDIAN).getInt(); // yes, cast from int to long.

            logPrinter.print("  Padding check", offset == 0?EMsgType.PASS:EMsgType.WARNING);
            logPrinter.print("  NCA offset check: "+nca_offset, nca_offset >= 0?EMsgType.PASS:EMsgType.WARNING);
            logPrinter.print("  NCA size check: "+nca_size, nca_size >= 0?EMsgType.PASS: EMsgType.WARNING);
            logPrinter.print("  NCA name offset check: "+nca_name_offset, nca_name_offset >= 0?EMsgType.PASS:EMsgType.WARNING);

            NCAFile ncaFile = new NCAFile();
            ncaFile.setNcaOffset(nca_offset);
            ncaFile.setNcaSize(nca_size);
            this.ncaFiles[i] = ncaFile;

            ncaNameOffsets.put(i, nca_name_offset);
        }
        // Final offset
        byte[] bufForInt = new byte[4];
        if ((randAccessFile.read(bufForInt) == 4) && (Arrays.equals(bufForInt, new byte[4])))
            logPrinter.print("PFS Final padding check", EMsgType.PASS);
        else
            logPrinter.print("PFS Final padding check", EMsgType.WARNING);

        // Calculate position including header for body size offset
        bodySize = randAccessFile.getFilePointer()+header;
        //*********************************************************************************************
        // Collect file names from NCAs
        logPrinter.print("PFS Collecting file names", EMsgType.INFO);
        List<Byte> ncaFN;                 // Temporary
        byte[] b = new byte[1];                 // Temporary
        for (int i=0; i<filesCount; i++){
            ncaFN = new ArrayList<>();
            randAccessFile.seek(filesCount*24+16+ncaNameOffsets.get(i));          // Files cont * 24(bit for each meta-data) + 4 bytes goes after all of them  + 12 bit what were in the beginning
            while ((randAccessFile.read(b)) != -1){
                if (b[0] == 0x00)
                    break;
                else
                    ncaFN.add(b[0]);
            }
            byte[] exchangeTempArray = new byte[ncaFN.size()];
            for (int j=0; j < ncaFN.size(); j++)
                exchangeTempArray[j] = ncaFN.get(j);
            // Find and store ticket (.tik)
            if (new String(exchangeTempArray, StandardCharsets.UTF_8).toLowerCase().endsWith(".tik"))
                this.ticketID = i;
            this.ncaFiles[i].setNcaFileName(Arrays.copyOf(exchangeTempArray, exchangeTempArray.length));
        }
        randAccessFile.close();
        logPrinter.print("PFS Finished NSP file analyze for ["+nspFileName+"]", EMsgType.PASS);
    }
    /**
     * Return file name as byte array
     * */
    public byte[] getBytesNspFileName(){
        return nspFileName.getBytes(StandardCharsets.UTF_8);
    }
    /**
     * Return file name length as byte array
     * */
    public byte[] getBytesNspFileNameLength(){
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(getBytesNspFileName().length).array();
    }
    /**
     * Return NCA count inside of file as byte array
     * */
    public byte[] getBytesCountOfNca(){
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ncaFiles.length).array();
    }
    /**
     * Return NCA count inside of file as int
     * */
    public int getIntCountOfNca(){
        return ncaFiles.length;
    }
    /**
     * Return requested-by-number NCA file inside of file
     * */
    public NCAFile getNca(int ncaNumber){
        return ncaFiles[ncaNumber];
    }
    /**
     * Return bodySize
     * */
    public long getBodySize(){
        return bodySize;
    }
    /**
     * Return special NCA file: ticket
     * (sugar)
     * */
    public int getNcaTicketID(){
        return ticketID;
    }
}
