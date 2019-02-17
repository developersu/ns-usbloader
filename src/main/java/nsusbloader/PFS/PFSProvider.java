package nsusbloader.PFS;

import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.ServiceWindow;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * Used in GoldLeaf USB protocol
 * */
public class PFSProvider {
    private static final byte[] PFS0 = new byte[]{(byte)0x50, (byte)0x46, (byte)0x53, (byte)0x30};  // PFS0, and what did you think?

    private BlockingQueue<String> msgQueue;
    private ResourceBundle rb;

    private RandomAccessFile randAccessFile;
    private String nspFileName;
    private NCAFile[] ncaFiles;
    private long bodySize;
    private int ticketID = -1;

    public PFSProvider(File nspFile, BlockingQueue msgQueue){
        this.msgQueue = msgQueue;
        try {
            this.randAccessFile = new RandomAccessFile(nspFile, "r");
            nspFileName = nspFile.getName();
        }
        catch (FileNotFoundException fnfe){
            printLog("PFS File not founnd: \n  "+fnfe.getMessage(), EMsgType.FAIL);
            nspFileName = null;
        }
        if (Locale.getDefault().getISO3Language().equals("rus"))
            rb = ResourceBundle.getBundle("locale", new Locale("ru"));
        else
            rb = ResourceBundle.getBundle("locale", new Locale("en"));
    }
    
    public boolean init() {
        if (nspFileName == null)
            return false;

        int filesCount;
        int header;

        printLog("PFS Start NSP file analyze for ["+nspFileName+"]", EMsgType.INFO);
        try {
            byte[] fileStartingBytes = new byte[12];
            // Read PFS0, files count, header, padding (4 zero bytes)
            if (randAccessFile.read(fileStartingBytes) == 12)
                printLog("PFS Read file starting bytes.", EMsgType.PASS);
            else {
                printLog("PFS Read file starting bytes.", EMsgType.FAIL);
                randAccessFile.close();
                return false;
            }
            // Check PFS0
            if (Arrays.equals(PFS0, Arrays.copyOfRange(fileStartingBytes, 0, 4)))
                printLog("PFS Read 'PFS0'.", EMsgType.PASS);
            else {
                printLog("PFS Read 'PFS0'.", EMsgType.WARNING);
                if (!ServiceWindow.getConfirmationWindow(nspFileName+"\n"+rb.getString("windowTitleConfirmWrongPFS0"), rb.getString("windowBodyConfirmWrongPFS0"))) {
                    randAccessFile.close();
                    return false;
                }
            }
            // Get files count
            filesCount = ByteBuffer.wrap(Arrays.copyOfRange(fileStartingBytes, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (filesCount > 0 ) {
                printLog("PFS Read files count [" + filesCount + "]", EMsgType.PASS);
            }
            else {
                printLog("PFS Read files count", EMsgType.FAIL);
                randAccessFile.close();
                return false;
            }
            // Get header
            header = ByteBuffer.wrap(Arrays.copyOfRange(fileStartingBytes, 8, 12)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (header > 0 )
                printLog("PFS Read header ["+header+"]", EMsgType.PASS);
            else {
                printLog("PFS Read header ", EMsgType.FAIL);
                randAccessFile.close();
                return false;
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
                    printLog("PFS Read NCA inside NSP: " + i, EMsgType.PASS);
                }
                else {
                    printLog("PFS Read NCA inside NSP: "+i, EMsgType.FAIL);
                    randAccessFile.close();
                    return false;
                }
                offset =  ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
                nca_offset = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 4, 12)).order(ByteOrder.LITTLE_ENDIAN).getLong();
                nca_size = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 12, 20)).order(ByteOrder.LITTLE_ENDIAN).getLong();
                nca_name_offset = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 20, 24)).order(ByteOrder.LITTLE_ENDIAN).getInt(); // yes, cast from int to long.

                printLog("  Padding check", offset == 0?EMsgType.PASS:EMsgType.WARNING);
                printLog("  NCA offset check "+nca_offset, nca_offset >= 0?EMsgType.PASS:EMsgType.WARNING);
                printLog("  NCA size check: "+nca_size, nca_size >= 0?EMsgType.PASS: EMsgType.WARNING);
                printLog("  NCA name offset check "+nca_name_offset, nca_name_offset >= 0?EMsgType.PASS:EMsgType.WARNING);

                NCAFile ncaFile = new NCAFile();
                ncaFile.setNcaOffset(nca_offset);
                ncaFile.setNcaSize(nca_size);
                this.ncaFiles[i] = ncaFile;

                ncaNameOffsets.put(i, nca_name_offset);
            }
            // Final offset
            byte[] bufForInt = new byte[4];
            if ((randAccessFile.read(bufForInt) == 4) && (Arrays.equals(bufForInt, new byte[4])))
                printLog("PFS Final padding check", EMsgType.PASS);
            else
                printLog("PFS Final padding check", EMsgType.WARNING);

            // Calculate position including header for body size offset
            bodySize = randAccessFile.getFilePointer()+header;
            //*********************************************************************************************
            // Collect file names from NCAs
            printLog("PFS Collecting file names", EMsgType.INFO);
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
        }
        catch (IOException ioe){
            printLog("PFS Failed NSP file analyze for ["+nspFileName+"]\n  "+ioe.getMessage(), EMsgType.FAIL);
            ioe.printStackTrace();
        }
        printLog("PFS Finish NSP file analyze for ["+nspFileName+"]", EMsgType.PASS);

        return true;
    }
    /**
     * Return file name as byte array
     * */
    public byte[] getBytesNspFileName(){
        return nspFileName.getBytes(StandardCharsets.UTF_8);
    }
    /**
     * Return file name as String
     * */
    public String getStringNspFileName(){
        return nspFileName;
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
    /**
     * This is what will print to textArea of the application.
     **/
    private void printLog(String message, EMsgType type){
        try {
            switch (type){
                case PASS:
                    msgQueue.put("[ PASS ] "+message+"\n");
                    break;
                case FAIL:
                    msgQueue.put("[ FAIL ] "+message+"\n");
                    break;
                case INFO:
                    msgQueue.put("[ INFO ] "+message+"\n");
                    break;
                case WARNING:
                    msgQueue.put("[ WARN ] "+message+"\n");
                    break;
            }
        }catch (InterruptedException ie){
            ie.printStackTrace();                                                  //TODO:  ???
        }
    }
}
