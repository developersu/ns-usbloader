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
 */
package nsusbloader.Utilities.patches.es.finders;

import libKonogonka.Converter;
import nsusbloader.AppPreferences;
import nsusbloader.Utilities.patches.AHeuristic;
import nsusbloader.Utilities.patches.BinToAsmPrinter;
import nsusbloader.Utilities.patches.SimplyFind;

import java.util.List;

class HeuristicEs1 extends AHeuristic {
    private static final String PATTERN = AppPreferences.getInstance().getPatchPattern("ES", 1, 0);

    private final List<Integer> findings;
    private final byte[] where;

    HeuristicEs1(byte[] where){
        this.where = where;
        SimplyFind simplyfind = new SimplyFind(PATTERN, where);
        this.findings = simplyfind.getResults();

        this.findings.removeIf(this::dropStep1);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep2);
    }

    // Check ranges
    private boolean dropStep1(int offsetOfPatternFound){
        return ((offsetOfPatternFound < 0x10000 || offsetOfPatternFound > 0xffffc));
    }
    // Remove non-CBZ
    private boolean dropStep2(int offsetOfPatternFound){
        return ((where[offsetOfPatternFound - 1] & (byte) 0b01111111) != 0x34);
    }

    @Override
    public boolean isFound(){
        return findings.size() == 1;
    }

    @Override
    public boolean wantLessEntropy(){
        return findings.size() > 1;
    }

    @Override
    public int getOffset() throws Exception{
        if(findings.isEmpty())
            throw new Exception("Nothing found");
        if (findings.size() > 1)
            throw new Exception("Too many offsets");
        return findings.get(0);
    }

    @Override
    public boolean setOffsetsNearby(int offsetNearby) {
        findings.removeIf(offset -> {
            if (offset > offsetNearby)
                return ! (offset < offsetNearby - 0xffff);
            return ! (offset > offsetNearby - 0xffff);
        });
        return isFound();
    }

    @Override
    public String getDetails(){
        int cbzOffsetInternal = findings.get(0) - 4;
        int instructionExpression = Converter.getLEint(where, cbzOffsetInternal);
        int conditionalJumpLocation = ((instructionExpression >> 5 & 0x7FFFF) * 4 + cbzOffsetInternal) & 0xfffff;

        int secondExpressionsPairElement1 = Converter.getLEint(where, conditionalJumpLocation);
        int secondExpressionsPairElement2 = Converter.getLEint(where, conditionalJumpLocation+4);

        return BinToAsmPrinter.printSimplified(instructionExpression, cbzOffsetInternal) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, cbzOffsetInternal + 4),
                        cbzOffsetInternal + 4) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, cbzOffsetInternal + 8),
                        cbzOffsetInternal + 8) +
                "...\n" +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement1, conditionalJumpLocation) +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement2, conditionalJumpLocation + 4);
    }
}
