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

import nsusbloader.ModelControllers.ILogPrinter;

import java.io.*;
import java.util.*;

import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static nsusbloader.NSLDataTypes.EMsgType.*;
import static nsusbloader.com.DataConvertUtils.*;

/**
 * Used in GoldLeaf USB protocol
 * */
public class PFSProvider {
    private final String nspFileName;
    private final NCAFile[] ncaFiles;
    private final long bodySize;
    private int ticketID = -1;

    public PFSProvider(File nspFile, ILogPrinter log) throws Exception {
        nspFileName = nspFile.getName();
        if (nspFile.isDirectory()) {
            nspFile = new File(nspFile.getAbsolutePath() + separator + "00");
        }

        var randAccessFile = new RandomAccessFile(nspFile, "r");

        log.print("PFS Start NSP file analyze for ["+nspFileName+"]", INFO);

        var fileStartingBytes = new byte[12];
        // Read PFS0, files count, header, padding (4 zero bytes)
        if (randAccessFile.read(fileStartingBytes) == 12)
            log.print("PFS Read file starting bytes.", PASS);
        else {
            log.print("PFS Read file starting bytes.", FAIL);
            randAccessFile.close();
            throw new Exception("Unable to read file starting bytes");
        }
        // Check PFS0
        if ("PFS0".equals(new String(fileStartingBytes, 0, 4, US_ASCII)))
            log.print("PFS Read 'PFS0'.", PASS);
        else
            log.print("PFS Read 'PFS0': this file looks wired.", WARNING);
        // Get files count
        int filesCount = arrToIntLE(fileStartingBytes, 4);
        if (filesCount > 0 )
            log.print("PFS Read files count [" + filesCount + "]", PASS);
        else {
            log.print("PFS Read files count", FAIL);
            randAccessFile.close();
            throw new Exception("Unable to read file count");
        }
        // Get header
        int header = arrToIntLE(fileStartingBytes, 8);
        if (header > 0 )
            log.print("PFS Read header ["+header+"]", PASS);
        else {
            log.print("PFS Read header ", FAIL);
            randAccessFile.close();
            throw new Exception("Unable to read header");
        }
        //*********************************************************************************************
        // Create NCA set
        ncaFiles = new NCAFile[filesCount];
        // Collect files from NSP
        var ncaInfoArr = new byte[24];   // should be unsigned long
        var ncaNameOffsets = new LinkedHashMap<Integer, Long>();

        for (int i = 0; i < filesCount; i++){
            if (randAccessFile.read(ncaInfoArr) == 24) {
                log.print("PFS Read NCA inside NSP: "+i, PASS);
            }
            else {
                log.print("PFS Read NCA inside NSP: "+i, FAIL);
                randAccessFile.close();
                throw new Exception("Unable to read NCA inside NSP");
            }
            int offset = arrToIntLE(ncaInfoArr, 0);
            long nca_offset = arrToLongLE(ncaInfoArr, 4);
            long nca_size = arrToLongLE(ncaInfoArr, 12);
            long nca_name_offset = arrToIntLE(ncaInfoArr, 20); // cast from int → long.

            log.print("  Padding check", offset == 0? PASS: WARNING);
            log.print("  NCA offset check: "+nca_offset, nca_offset >= 0? PASS: WARNING);
            log.print("  NCA size check: "+nca_size, nca_size >= 0? PASS: WARNING);
            log.print("  NCA name offset check: "+nca_name_offset, nca_name_offset >= 0? PASS: WARNING);

            var ncaFile = new NCAFile();
            ncaFile.setNcaOffset(nca_offset);
            ncaFile.setNcaSize(nca_size);
            ncaFiles[i] = ncaFile;

            ncaNameOffsets.put(i, nca_name_offset);
        }
        // Final offset
        byte[] bufForInt = new byte[4];
        log.print("PFS Final padding check",
                ((randAccessFile.read(bufForInt) == 4) && Arrays.equals(bufForInt, new byte[4]))? PASS: WARNING);

        // Calculate position including header for body size offset
        bodySize = randAccessFile.getFilePointer()+header;
        //*********************************************************************************************
        // Collect file names from NCAs
        log.print("PFS Collecting file names", INFO);

        // Files cont * 24 (bit per each meta-data) + 4 bytes (goes after all of them)  + 12 bit were at the beginning
        long seekIncrement = filesCount*24L+16L;
        byte[] b = new byte[1];                 // Temporary
        for (int i = 0; i < filesCount; i++){
            var ncaFN = new ArrayList<Byte>();
            randAccessFile.seek(seekIncrement+ncaNameOffsets.get(i));
            while ((randAccessFile.read(b)) != -1) {
                if (b[0] == 0x00)
                    break;
                else
                    ncaFN.add(b[0]);
            }
            byte[] exchangeTempArray = new byte[ncaFN.size()];
            for (int j = 0; j < ncaFN.size(); j++)
                exchangeTempArray[j] = ncaFN.get(j);
            // Find and store ticket (.tik)
            if (new String(exchangeTempArray, UTF_8).toLowerCase().endsWith(".tik"))
                ticketID = i;
            ncaFiles[i].setNcaFileName(Arrays.copyOf(exchangeTempArray, exchangeTempArray.length));
        }
        randAccessFile.close();
        log.print("PFS Finished NSP file analyze for ["+nspFileName+"]", PASS);
    }
    /**
     * Return file name as byte array
     * */
    public byte[] getBytesNspFileName(){
        return nspFileName.getBytes(UTF_8);
    }
    /**
     * Return file name length as byte array
     * */
    public byte[] getBytesNspFileNameLength(){
        return intToArrLE(getBytesNspFileName().length);
    }
    /**
     * Return NCA count inside of file as byte array
     * */
    public byte[] getBytesCountOfNca(){
        return intToArrLE(ncaFiles.length);
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
