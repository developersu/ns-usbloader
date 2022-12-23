/*
    Copyright 2018-2022 Dmitry Isaenko
     
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
    Based on ES-AutoIPS.py patch script made by GBATemp member MrDude.
    Taken from: https://gbatemp.net/threads/info-on-sha-256-hashes-on-fs-patches.581550/
 */
package nsusbloader.Utilities.patches.es;

import libKonogonka.Converter;
import libKonogonka.Tools.NCA.NCAProvider;
import libKonogonka.Tools.NSO.NSO0Header;
import libKonogonka.Tools.NSO.NSO0Provider;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.Utilities.patches.es.finders.HeuristicEsWizard;

import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class EsPatch {
    private final NCAProvider ncaProvider;
    private final String saveToLocation;
    private final ILogPrinter logPrinter;

    private Long fwVersion;
    private String buildId;
    private byte[] _textSection;

    private HeuristicEsWizard wizard;

    EsPatch(NCAProvider ncaProvider, String saveToLocation, ILogPrinter logPrinter) throws Exception{
        this.ncaProvider = ncaProvider;
        this.saveToLocation = saveToLocation + File.separator +
                "atmosphere" + File.separator + "exefs_patches" + File.separator + "es_patches";
        this.logPrinter = logPrinter;

        getPlainFirmwareVersion();
        NSO0Provider nso0Provider = new NSO0Provider(ncaProvider.getNCAContentProvider(0).getPfs0().getStreamProducer(0));
        getBuildId(nso0Provider);
        getTextSection(nso0Provider);
        findAllOffsets();
        mkDirs();
        writeFile();
        logPrinter.print("                  == Debug information ==\n"+wizard.getDebug(), EMsgType.NULL);
    }
    private void getPlainFirmwareVersion() throws Exception{
        fwVersion = Long.parseLong(""+ncaProvider.getSdkVersion()[3]+ncaProvider.getSdkVersion()[2]
                +ncaProvider.getSdkVersion()[1] +ncaProvider.getSdkVersion()[0]);
        logPrinter.print("Internal firmware version: "+ncaProvider.getSdkVersion()[3] +"."+ncaProvider.getSdkVersion()[2] +"."+ncaProvider.getSdkVersion()[1] +"."+ncaProvider.getSdkVersion()[0], EMsgType.INFO);
        if (fwVersion < 9300)
            logPrinter.print("WARNING! FIRMWARES VERSIONS BEFORE 9.0.0 ARE NOT SUPPORTED! USING PRODUCED ES PATCHES (IF ANY) COULD BREAK SOMETHING! IT'S NEVER BEEN TESTED!", EMsgType.WARNING);
    }
    private void getBuildId(NSO0Provider nso0Provider) throws Exception{
        NSO0Header nso0DecompressedHeader = nso0Provider.getAsDecompressedNSO0().getHeader();
        byte[] buildIdBytes = nso0DecompressedHeader.getModuleId();
        buildId = Converter.byteArrToHexStringAsLE(buildIdBytes).substring(0, 40).toUpperCase();
        logPrinter.print("Build ID: "+buildId, EMsgType.INFO);
    }
    private void getTextSection(NSO0Provider nso0Provider) throws Exception{
        _textSection = nso0Provider.getAsDecompressedNSO0().getTextRaw();
    }
    private void findAllOffsets() throws Exception{
        this.wizard = new HeuristicEsWizard(fwVersion, _textSection);
        String errorsAndNotes = wizard.getErrorsAndNotes();
        if (errorsAndNotes.length() > 0)
            logPrinter.print(errorsAndNotes, EMsgType.WARNING);
    }
    private void mkDirs(){
        File parentFolder = new File(saveToLocation);
        parentFolder.mkdirs();
    }

    private void writeFile() throws Exception{
        String patchFileLocation = saveToLocation + File.separator + buildId + ".ips";
        int offset1 = wizard.getOffset1();
        int offset2 = wizard.getOffset2();
        int offset3 = wizard.getOffset3();

        ByteBuffer handyEsPatch = ByteBuffer.allocate(0x23).order(ByteOrder.LITTLE_ENDIAN);
        handyEsPatch.put(getHeader());
        if (offset1 > 0) {
            logPrinter.print("Patch component 1 will be used", EMsgType.PASS);
            handyEsPatch.put(getPatch1(offset1));
        }
        if (offset2 > 0) {
            logPrinter.print("Patch component 2 will be used", EMsgType.PASS);
            handyEsPatch.put(getPatch2(offset2));
        }
        if (offset3 > 0) {
            logPrinter.print("Patch component 3 will be used", EMsgType.PASS);
            handyEsPatch.put(getPatch3(offset3));
        }
        handyEsPatch.put(getFooter());

        try (BufferedOutputStream stream = new BufferedOutputStream(
                Files.newOutputStream(new File(patchFileLocation).toPath()))){
            stream.write(handyEsPatch.array());
        }
        logPrinter.print("Patch created at "+patchFileLocation, EMsgType.PASS);
    }
    private byte[] getHeader(){
        return "PATCH".getBytes(StandardCharsets.US_ASCII);
    }
    private byte[] getFooter(){
        return "EOF".getBytes(StandardCharsets.US_ASCII);
    }

    // WE EXPECT TO SEE CBZ (for patch 1) INSTRUCTION RIGHT BEFORE FOUND SEQUENCE (requiredInstructionOffsetInternal)
    // IN RESULTING FILE InstructionOffset SHOULD BE INCREMENTED by 0x100 to get real offset
    // (because header for decompressed NSO0 size = 0x100; it's fixed alignment produced by libKonogonka)
    private byte[] getPatch1(int offset) throws Exception{
        int requiredInstructionOffsetInternal = offset - 4;
        int requiredInstructionOffsetReal = requiredInstructionOffsetInternal + 0x100;
        int instructionExpression = Converter.getLEint(_textSection, requiredInstructionOffsetInternal);
        int patch = ((0x14 << 24) | (instructionExpression >> 5) & 0x7FFFF);

        logPrinter.print(BinToAsmPrinter.printSimplified(patch, requiredInstructionOffsetInternal), EMsgType.NULL);

        // Somehow IPS patches uses offsets written as big_endian (0.o) and bytes dat should be patched as LE.
        ByteBuffer prePatch = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                .putInt(requiredInstructionOffsetReal)
                .putShort((short) 4)
                .putInt(Integer.reverseBytes(patch));

        return Arrays.copyOfRange(prePatch.array(), 1, 10);
    }
    private byte[] getPatch2(int offset) throws Exception{
        final int NopExpression = 0x1F2003D5; // reversed
        int offsetReal = offset - 4 + 0x100;

        logPrinter.print(BinToAsmPrinter.printSimplified(Integer.reverseBytes(NopExpression),  offset - 4), EMsgType.NULL);

        ByteBuffer prePatch = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                .putInt(offsetReal)
                .putShort((short) 4)
                .putInt(NopExpression);

        return Arrays.copyOfRange(prePatch.array(), 1, 10);
    }
    private byte[] getPatch3(int offset) throws Exception{
        int requiredInstructionOffsetInternal = offset - 4;
        int requiredInstructionOffsetReal = requiredInstructionOffsetInternal + 0x100;

        int instructionExpression = Converter.getLEint(_textSection, requiredInstructionOffsetInternal);
        int patch = ((0x14 << 24) | (instructionExpression >> 5) & 0x7FFFF);

        logPrinter.print(BinToAsmPrinter.printSimplified(patch, requiredInstructionOffsetInternal), EMsgType.NULL);

        ByteBuffer prePatch = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN)
                .putInt(requiredInstructionOffsetReal)
                .putShort((short) 4)
                .putInt(Integer.reverseBytes(patch));

        return Arrays.copyOfRange(prePatch.array(), 1, 10);
    }
}
