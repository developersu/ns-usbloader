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
package nsusbloader.Utilities.patches.loader;

import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.Utilities.patches.MalformedIniFileException;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class LoaderIniMaker {
    private static final String FILE_HEADER_TEXT = "# UTF-8\n" +
            "# A KIP section is [kip1_name:sha256_hex_8bytes]\n" +
            "# A patchset is .patch_name=kip_section_dec:offset_hex_0x:length_hex_0x:src_data_hex,dst_data_hex\n" +
            "# _dec: 1 char decimal | _hex_0x: max u32 prefixed with 0x | _hex: hex array.\n" +
            "# Kip1 section decimals: TEXT: 0, RODATA: 1, DATA: 2.\n"; // Sending good vibes to Mr. ITotalJustice

    private final ILogPrinter logPrinter;
    private final String saveToLocation;
    private final int offset;

    private String sectionDeclaration;
    private String patchSet;

    LoaderIniMaker(ILogPrinter logPrinter,
                          String saveToLocation,
                          int foundOffset,
                          String patchName) throws Exception{
        this.logPrinter = logPrinter;
        this.saveToLocation = saveToLocation;
        this.offset = foundOffset + 6;

        mkDirs();
        makeSectionDeclaration(patchName);
        makePatchSet1();
        writeFile();
    }

    private void mkDirs(){
        File parentFolder = new File(saveToLocation + File.separator + "bootloader");
        parentFolder.mkdirs();
    }

    private void makeSectionDeclaration(String patchName){
        sectionDeclaration = "[Loader:"+patchName.substring(0, 16)+"]";
    }

    private void makePatchSet1(){
        patchSet = String.format(".nosigchk=0:0x%02X:0x1:01,00", offset);
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

                    String expression = ini.readLine();

                    if (expression == null || ! expression.startsWith(patchSet))
                        throw new MalformedIniFileException("Somewhere near "+ini.getFilePointer());

                    return; // Ini file already contains correct information regarding patch file we made.
                }
            }

            ini.writeBytes("\n#Loader (Atmosphere)\n");
            ini.writeBytes(sectionDeclaration);
            ini.writeBytes("\n");

            ini.writeBytes(patchSet);
            ini.writeBytes("\n");
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
