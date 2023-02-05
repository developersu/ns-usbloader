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
package nsusbloader.Utilities.patches.loader;

import libKonogonka.Converter;
import libKonogonka.fs.other.System2.ini1.KIP1Provider;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import nsusbloader.Utilities.patches.BinToAsmPrinter;
import nsusbloader.Utilities.patches.SimplyFind;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

public class LoaderPatch {
    private static final byte[] HEADER = "PATCH".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FOOTER = "EOF".getBytes(StandardCharsets.US_ASCII);

    private static final String ATMOSPHERE_NEW_PATTERN = "01C0BE121F00016B";
    //private static final String ATMOSPHERE_OLD_PATTERN = "003C00121F280071"; Must be patched using different (to current implementation) code

    private final String saveToLocation;
    private final ILogPrinter logPrinter;

    private String patchName;
    private byte[] _textSection;

    private int offset;

    LoaderPatch(KIP1Provider loaderProvider,
                String saveToLocation,
                ILogPrinter logPrinter) throws Exception{
        this.saveToLocation = saveToLocation;
        this.logPrinter = logPrinter;

        getPatchName(loaderProvider);
        getTextSection(loaderProvider);
        findOffset();
        mkDirs();
        writeFile();
        new LoaderIniMaker(logPrinter, saveToLocation, offset, patchName);
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
    private void findOffset() throws Exception{
        SimplyFind simplyFind = new SimplyFind(ATMOSPHERE_NEW_PATTERN, _textSection); // Atm 13+
        if (simplyFind.getResults().size() == 0)
            throw new Exception("Offset not found");

        offset = simplyFind.getResults().get(0);

        if (offset <= 0)
            throw new Exception("Found offset is incorrect");

        for (int i = 0; i < simplyFind.getResults().size(); i++) {
            int offsetInternal = simplyFind.getResults().get(i) + 4;
            logPrinter.print("Only first (#1) found record will be patched!", EMsgType.INFO);
            logPrinter.print("Found #" + (i+1) +"\n"+
                            BinToAsmPrinter.printSimplified(Converter.getLEint(_textSection, offsetInternal), offsetInternal) +
                            BinToAsmPrinter.printSimplified(Converter.getLEint(_textSection, offsetInternal + 4), offsetInternal + 4) +
                            BinToAsmPrinter.printSimplified(Converter.getLEint(_textSection, offsetInternal + 8), offsetInternal + 8) +
                            BinToAsmPrinter.printSimplified(Converter.getLEint(_textSection, offsetInternal + 12), offsetInternal + 12),
                    EMsgType.NULL);
        }
    }
    private void mkDirs(){
        File parentFolder = new File(saveToLocation + File.separator +
                "atmosphere" + File.separator + "kip_patches" + File.separator + "loader_patches");
        parentFolder.mkdirs();
    }

    private void writeFile() throws Exception{
        String patchFileLocation = saveToLocation + File.separator +
                "atmosphere" + File.separator + "kip_patches" + File.separator + "fs_patches" + File.separator + patchName;

        ByteBuffer handyFsPatch = ByteBuffer.allocate(0x100).order(ByteOrder.LITTLE_ENDIAN);
        handyFsPatch.put(HEADER);
        handyFsPatch.put(getPatch1(offset));
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
        int requiredInstructionOffsetInternal = offset + 6;
        int requiredInstructionOffsetReal = requiredInstructionOffsetInternal + 0x100;
        final byte[] patch = new byte[]{0x00, 0x01, 0x00};

        int instructionPatched = Converter.getLEint(_textSection, offset + 4) & 0xff00ffff;

        logPrinter.print("Patch will be applied", EMsgType.PASS);
        logPrinter.print(BinToAsmPrinter.printSimplified(instructionPatched, offset+4), EMsgType.NULL);

        ByteBuffer prePatch = ByteBuffer.allocate(7).order(ByteOrder.BIG_ENDIAN)
                .putInt(requiredInstructionOffsetReal)
                .put(patch);

        return Arrays.copyOfRange(prePatch.array(), 1, 7);
    }
}
