/*
    Copyright 2018-2023 Dmitry Isaenko
     
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
    ---
    Based on https://github.com/mrdude2478/IPS_Patch_Creator patch script made by GBATemp member MrDude.
 */
package nsusbloader.Utilities.patches.fs;

import libKonogonka.Converter;
import libKonogonka.KeyChainHolder;
import libKonogonka.fs.NCA.NCAProvider;
import libKonogonka.fs.RomFs.FileSystemEntry;
import libKonogonka.fs.RomFs.RomFsProvider;
import libKonogonka.fs.other.System2.System2Provider;
import libKonogonka.fs.other.System2.ini1.Ini1Provider;
import libKonogonka.fs.other.System2.ini1.KIP1Provider;
import libKonogonka.aesctr.InFileStreamProducer;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.Utilities.patches.BinToAsmPrinter;
import nsusbloader.Utilities.patches.fs.finders.HeuristicFsWizard;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FsPatch {
    private static final byte[] HEADER = "PATCH".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FOOTER = "EOF".getBytes(StandardCharsets.US_ASCII);

    private final NCAProvider ncaProvider;
    private final String saveToLocation;
    private final KeyChainHolder keyChainHolder;
    private final ILogPrinter logPrinter;

    private String patchName;
    private byte[] _textSection;
    private boolean filesystemTypeFat32;

    private HeuristicFsWizard wizard;
    // Called twice: once for FAT32, once for ExFAT
    FsPatch(NCAProvider ncaProvider,
            String saveToLocation,
            KeyChainHolder keyChainHolder,
            ILogPrinter logPrinter) throws Exception{
        this.ncaProvider = ncaProvider;
        this.saveToLocation = saveToLocation;
        this.keyChainHolder = keyChainHolder;
        this.logPrinter = logPrinter;

        KIP1Provider kip1Provider = getKIP1Provider();
        getPatchName(kip1Provider);
        getTextSection(kip1Provider);
        checkFirmwareVersion();
        getFilesystemType();
        findAllOffsets();
        mkDirs();
        writeFile();
        new IniMaker(logPrinter,
                saveToLocation,
                _textSection,
                wizard.getOffset1(),
                wizard.getOffset2(),
                ncaProvider.getSdkVersion(),
                patchName,
                filesystemTypeFat32);
        logPrinter.print("                  == Debug information ==\n"+wizard.getDebug(), EMsgType.NULL);
    }
    private KIP1Provider getKIP1Provider() throws Exception{
        RomFsProvider romFsProvider = ncaProvider.getNCAContentProvider(0).getRomfs();

        FileSystemEntry package2FsEntry = romFsProvider.getRootEntry().getContent()
                .stream()
                .filter(e -> e.getName().equals("nx"))
                .collect(Collectors.toList())
                .get(0)
                .getContent()
                .stream()
                .filter(e -> e.getName().equals("package2"))
                .collect(Collectors.toList())
                .get(0);
        InFileStreamProducer producer = romFsProvider.getStreamProducer(package2FsEntry);
        System2Provider system2Provider = new System2Provider(producer, keyChainHolder);
        Ini1Provider ini1Provider = system2Provider.getIni1Provider();

        KIP1Provider kip1Provider = ini1Provider.getKip1List().stream()
                .filter(provider -> provider.getHeader().getName().startsWith("FS"))
                .collect(Collectors.toList())
                .get(0);

        if (kip1Provider == null)
            throw new Exception("No FS KIP1");

        return kip1Provider;
    }
    private void getPatchName(KIP1Provider kip1Provider) throws Exception{
        int kip1EncryptedSize = (int) kip1Provider.getSize();
        byte[] kip1EncryptedRaw = new byte[kip1EncryptedSize];

        try (BufferedInputStream kip1ProviderStream = kip1Provider.getStreamProducer().produce()) {
            if (kip1EncryptedSize != kip1ProviderStream.read(kip1EncryptedRaw))
                throw new Exception("Unencrypted FS KIP1 read failure");
        }

        byte[] sha256ofKip1 = MessageDigest.getInstance("SHA-256").digest(kip1EncryptedRaw);
        patchName = Converter.byteArrToHexStringAsLE(sha256ofKip1, true) + ".ips";
    }
    private void getTextSection(KIP1Provider kip1Provider) throws Exception{
        _textSection = kip1Provider.getAsDecompressed().getTextRaw();
    }
    private void checkFirmwareVersion() throws Exception{
        final byte[] byteSdkVersion = ncaProvider.getSdkVersion();
        long fwVersion = Long.parseLong(""+byteSdkVersion[3]+byteSdkVersion[2]+byteSdkVersion[1]+byteSdkVersion[0]);
        logPrinter.print("Internal firmware version: " + byteSdkVersion[3] +"."+ byteSdkVersion[2] +"."+ byteSdkVersion[1] +"."+ byteSdkVersion[0], EMsgType.INFO);
        if (byteSdkVersion[3] < 9 || fwVersion < 9300)
            logPrinter.print("WARNING! FIRMWARES VERSIONS BEFORE 9.0.0 ARE NOT SUPPORTED! " +
                    "USING PRODUCED ES PATCHES (IF ANY) COULD BREAK SOMETHING! IT'S NEVER BEEN TESTED!", EMsgType.WARNING);
    }
    private void getFilesystemType() throws Exception{
        String titleId = Converter.byteArrToHexStringAsLE(ncaProvider.getTitleId());
        filesystemTypeFat32 = titleId.equals("0100000000000819");
        if (filesystemTypeFat32)
            logPrinter.print("\n\t\t-- [  FAT32  ] --\n", EMsgType.INFO);
        else
            logPrinter.print("\n\t\t-- [  ExFAT  ] --\n", EMsgType.INFO);
    }
    private void findAllOffsets() throws Exception{
        this.wizard = new HeuristicFsWizard(_textSection);
        String errorsAndNotes = wizard.getErrorsAndNotes();
        if (errorsAndNotes.length() > 0)
            logPrinter.print(errorsAndNotes, EMsgType.WARNING);
    }
    private void mkDirs(){
        File parentFolder = new File(saveToLocation + File.separator +
                "atmosphere" + File.separator + "kip_patches" + File.separator + "fs_patches");
        parentFolder.mkdirs();
    }

    private void writeFile() throws Exception{
        String patchFileLocation = saveToLocation + File.separator +
                "atmosphere" + File.separator + "kip_patches" + File.separator + "fs_patches" + File.separator + patchName;
        int offset1 = wizard.getOffset1();
        int offset2 = wizard.getOffset2();

        ByteBuffer handyFsPatch = ByteBuffer.allocate(0x100).order(ByteOrder.LITTLE_ENDIAN);
        handyFsPatch.put(HEADER);
        if (offset1 > 0) {
            logPrinter.print("Patch component 1 will be used", EMsgType.PASS);
            handyFsPatch.put(getPatch1(offset1));
        }
        if (offset2 > 0) {
            logPrinter.print("Patch component 2 will be used", EMsgType.PASS);
            handyFsPatch.put(getPatch2(offset2));
        }
        handyFsPatch.put(FOOTER);

        byte[] fsPatch = new byte[handyFsPatch.position()];
        handyFsPatch.rewind();
        handyFsPatch.get(fsPatch);

        try (BufferedOutputStream stream = new BufferedOutputStream(
                Files.newOutputStream(Paths.get(patchFileLocation)))){
            stream.write(fsPatch);
        }
        logPrinter.print("Patch created at "+patchFileLocation, EMsgType.PASS);
    }

    private byte[] getPatch1(int offset) throws Exception{
        int requiredInstructionOffsetInternal = offset - 4;
        int requiredInstructionOffsetReal = requiredInstructionOffsetInternal + 0x100;
        final int patch = 0x1F2003D5; // NOP

        logPrinter.print(BinToAsmPrinter.printSimplified(Integer.reverseBytes(patch), requiredInstructionOffsetInternal), EMsgType.NULL);

        // Somehow IPS patches uses offsets written as big_endian (0.o) and bytes dat should be patched as LE.
        ByteBuffer prePatch = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                .putInt(requiredInstructionOffsetReal)
                .putShort((short) 4)
                .putInt(patch);

        return Arrays.copyOfRange(prePatch.array(), 1, 10);
    }
    private byte[] getPatch2(int offset) throws Exception{
        int requiredInstructionOffsetInternal = offset - 4;
        int requiredInstructionOffsetReal = requiredInstructionOffsetInternal + 0x100;
        final int patch = 0xE0031F2A; // mov w0, wzr
        logPrinter.print(BinToAsmPrinter.printSimplified(Integer.reverseBytes(patch), requiredInstructionOffsetInternal), EMsgType.NULL);

        // Somehow IPS patches uses offsets written as big_endian (0.o) and bytes dat should be patched as LE.
        ByteBuffer prePatch = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                .putInt(requiredInstructionOffsetReal)
                .putShort((short) 4)
                .putInt(patch);

        return Arrays.copyOfRange(prePatch.array(), 1, 10);
    }
}
