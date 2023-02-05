/*
    Copyright 2019-2023 Dmitry Isaenko
     
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
package nsusbloader.Utilities.patches.fs;

import libKonogonka.Converter;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.Utilities.patches.MalformedIniFileException;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public class FsIniMaker {
    private static final String FILE_HEADER_TEXT = "# UTF-8\n" +
            "# A KIP section is [kip1_name:sha256_hex_8bytes]\n" +
            "# A patchset is .patch_name=kip_section_dec:offset_hex_0x:length_hex_0x:src_data_hex,dst_data_hex\n" +
            "# _dec: 1 char decimal | _hex_0x: max u32 prefixed with 0x | _hex: hex array.\n" +
            "# Kip1 section decimals: TEXT: 0, RODATA: 1, DATA: 2.\n"; // Sending good vibes to Mr. ITotalJustice

    private final ILogPrinter logPrinter;
    private final String saveToLocation;
    private final int offset1;
    private final int offset2;

    private String firmwareVersionInformationNotice;
    private String sectionDeclaration;
    private String patchSet1;
    private String patchSet2;

    public FsIniMaker(ILogPrinter logPrinter,
                      String saveToLocation,
                      byte[] _textSection,
                      int wizardOffset1,
                      int wizardOffset2,
                      byte[] sdkVersion,
                      String patchName,
                      boolean filesystemTypeFat32) throws Exception{
        this.logPrinter = logPrinter;
        this.saveToLocation = saveToLocation;
        this.offset1 = wizardOffset1 - 4;
        this.offset2 = wizardOffset2 - 4;

        mkDirs();
        makeFwVersionInformationNotice(filesystemTypeFat32, sdkVersion);
        makeSectionDeclaration(patchName);
        makePatchSet1(_textSection);
        makePatchSet2(_textSection);
        writeFile();
    }

    private void mkDirs(){
        File parentFolder = new File(saveToLocation + File.separator + "bootloader");
        parentFolder.mkdirs();
    }

    private void makeFwVersionInformationNotice(boolean isFat32, byte[] fwVersion){
        String fwVersionFormatted = fwVersion[3]+"."+fwVersion[2]+"."+fwVersion[1]+"."+fwVersion[0];
        if (isFat32)
            firmwareVersionInformationNotice = "\n#FS "+fwVersionFormatted+"\n";
        else
            firmwareVersionInformationNotice = "\n#FS "+fwVersionFormatted+"-ExFAT\n";
    }

    private void makeSectionDeclaration(String patchName){
        sectionDeclaration = "[FS:"+patchName.substring(0, 16)+"]";
    }

    private void makePatchSet1(byte[] _textSection){
        if (offset1 > 0) {
            byte[] originalInstruction = Arrays.copyOfRange(_textSection, offset1, offset1 + 4);
            patchSet1 = String.format(".nosigchk=0:0x%02X:0x4:%s,1F2003D5",
                    offset1, Converter.byteArrToHexStringAsLE(originalInstruction, true));
        }
    }

    private void makePatchSet2(byte[] _textSection){
        if (offset2 > 0) {
            byte[] originalInstruction = Arrays.copyOfRange(_textSection, offset2, offset2 + 4);
            patchSet2 = String.format(".nosigchk=0:0x%02X:0x4:%s,E0031F2A",
                    offset2, Converter.byteArrToHexStringAsLE(originalInstruction, true));
        }
    }

    private void writeFile() throws Exception{
        final String iniLocation = saveToLocation + File.separator + "bootloader" + File.separator + "patches.ini";
        final Path iniLocationPath = Paths.get(iniLocation);

        boolean iniNotExists = Files.notExists(iniLocationPath);

        try (RandomAccessFile ini = new RandomAccessFile(iniLocation, "rw")){
            if (iniNotExists)
                ini.writeBytes(FILE_HEADER_TEXT);
            else {
                String line;
                while ((line = ini.readLine()) != null){
                    if (! line.startsWith(sectionDeclaration))
                        continue;

                    if (offset1 > 0) {
                        String expression1 = ini.readLine();
                        if (expression1 == null || ! expression1.startsWith(patchSet1))
                            throw new MalformedIniFileException("Somewhere near "+ini.getFilePointer());
                    }
                    String expression2 = ini.readLine();
                    if (offset2 > 0) {
                        if (expression2 == null || ! expression2.startsWith(patchSet2))
                            throw new MalformedIniFileException("Somewhere near "+ini.getFilePointer());
                    }
                    else {
                        if (expression2 == null || ! expression2.startsWith(".nosigchk"))
                            return;
                        throw new MalformedIniFileException("Somewhere near "+ini.getFilePointer());
                    }
                    return; // Ini file already contains correct information regarding patch file we made.
                }
            }

            ini.writeBytes(firmwareVersionInformationNotice);
            ini.writeBytes(sectionDeclaration);
            ini.writeBytes("\n");

            if (offset1 > 0) {
                ini.writeBytes(patchSet1);
                ini.writeBytes("\n");
            }
            if (offset2 > 0) {
                ini.writeBytes(patchSet2);
                ini.writeBytes("\n");
            }
        }
        catch (MalformedIniFileException e){
            e.printStackTrace();
            logPrinter.print(
                    "Existing patches.ini file is malformed or contains incorrect (outdated) information regarding current patch.\n" +
                            "It's now saved at "+iniLocation+".OLD\n" +
                            "New patches.ini file created instead.", EMsgType.WARNING);
            Files.move(iniLocationPath, Paths.get(iniLocation+".OLD"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            writeFile();
        }
    }
}
