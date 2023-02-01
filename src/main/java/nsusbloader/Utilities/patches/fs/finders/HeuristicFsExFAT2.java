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
 */
package nsusbloader.Utilities.patches.fs.finders;

import libKonogonka.Converter;
import nsusbloader.Utilities.patches.AHeuristic;
import nsusbloader.Utilities.patches.BinToAsmPrinter;
import nsusbloader.Utilities.patches.SimplyFind;

import java.util.List;

class HeuristicFsExFAT2 extends AHeuristic {
    private static final String PATTERN0 = ".94081C00121F05007181000054";
    private static final String PATTERN1 = "003688...1F";

    private final List<Integer> findings;
    private final byte[] where;

    HeuristicFsExFAT2(long fwVersion, byte[] where){
        this.where = where;
        String pattern = getPattern(fwVersion);
        SimplyFind simplyfind = new SimplyFind(pattern, where);
        this.findings = simplyfind.getResults();

        for (Integer find : findings)
            System.out.println(getDetails(find));
        /*                                                                                                  FIXME
        this.findings.removeIf(this::dropStep1);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep2);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep3);

 */
    }
    private String getPattern(long fwVersion){
        if (fwVersion < 15300) // & fwVersion >= 9300
            return PATTERN0;
        return PATTERN1;
    }
    // Let's focus on CBZ-ONLY statements
    private boolean dropStep1(int offsetOfPatternFound){
        return ((where[offsetOfPatternFound - 1] & (byte) 0b01111111) != 0x34);
    }

    private boolean dropStep2(int offsetOfPatternFound){
        int conditionalJumpLocation = getCBZConditionalJumpLocation(offsetOfPatternFound - 4);

        int afterJumpSecondExpressions = Converter.getLEint(where, conditionalJumpLocation);
        int afterJumpThirdExpressions = Converter.getLEint(where, conditionalJumpLocation+4);
        // Check first is 'MOV'; second is 'B'
        return (! isMOV_REG(afterJumpSecondExpressions)) || ! isB(afterJumpThirdExpressions);
    }

    private boolean dropStep3(int offsetOfPatternFound){
        int conditionalJumpLocation = getCBZConditionalJumpLocation(offsetOfPatternFound-4);
        int afterJumpSecondExpressions = Converter.getLEint(where, conditionalJumpLocation+4);
        int secondPairConditionalJumpLocation = ((afterJumpSecondExpressions & 0x3ffffff) * 4 + (conditionalJumpLocation+4)) & 0xfffff;

        int thirdExpressionsPairElement1 = Converter.getLEint(where, secondPairConditionalJumpLocation);
        int thirdExpressionsPairElement2 = Converter.getLEint(where, secondPairConditionalJumpLocation+4);
        // Check first is 'ADD'; second is 'BL'
        return (! isADD(thirdExpressionsPairElement1)) || (! isBL(thirdExpressionsPairElement2));
    }

    private int getCBZConditionalJumpLocation(int cbzOffsetInternal){
        int cbzExpression = Converter.getLEint(where, cbzOffsetInternal);
        return ((cbzExpression >> 5 & 0x7FFFF) * 4 + cbzOffsetInternal) & 0xfffff;
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

    public String getDetails(Integer value){
        StringBuilder builder = new StringBuilder();
        int cbzOffsetInternal = value - 4;
        int cbzExpression = Converter.getLEint(where, cbzOffsetInternal);
        int conditionalJumpLocation = ((cbzExpression >> 5 & 0x7FFFF) * 4 + cbzOffsetInternal) & 0xfffff;

        int secondExpressionsPairElement1 = Converter.getLEint(where, conditionalJumpLocation);
        int secondExpressionsPairElement2 = Converter.getLEint(where, conditionalJumpLocation+4);

        builder.append(BinToAsmPrinter.printSimplified(cbzExpression, cbzOffsetInternal));
        builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, cbzOffsetInternal+4), cbzOffsetInternal+4));
        builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, cbzOffsetInternal+8), cbzOffsetInternal+8));
        builder.append("...\n");

        builder.append(BinToAsmPrinter.printSimplified(secondExpressionsPairElement1, conditionalJumpLocation));
        builder.append(BinToAsmPrinter.printSimplified(secondExpressionsPairElement2, conditionalJumpLocation+4));

        if (((secondExpressionsPairElement2 >> 26 & 0b111111) == 0x5)){
            builder.append("...\n");
            int conditionalJumpLocation2 = ((secondExpressionsPairElement2 & 0x3ffffff) * 4 + (conditionalJumpLocation+4)) & 0xfffff;

            builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, conditionalJumpLocation2), conditionalJumpLocation2));
            builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, conditionalJumpLocation2+4), conditionalJumpLocation2+4));

        }
        else {
            builder.append("NO CONDITIONAL JUMP ON 2nd iteration (HeuristicEs3)");
        }
        return builder.toString();
    }

    @Override
    public String getDetails(){
        StringBuilder builder = new StringBuilder();
        int cbzOffsetInternal = findings.get(0) - 4;
        int cbzExpression = Converter.getLEint(where, cbzOffsetInternal);
        int conditionalJumpLocation = ((cbzExpression >> 5 & 0x7FFFF) * 4 + cbzOffsetInternal) & 0xfffff;

        int secondExpressionsPairElement1 = Converter.getLEint(where, conditionalJumpLocation);
        int secondExpressionsPairElement2 = Converter.getLEint(where, conditionalJumpLocation+4);

        builder.append(BinToAsmPrinter.printSimplified(cbzExpression, cbzOffsetInternal));
        builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, cbzOffsetInternal+4), cbzOffsetInternal+4));
        builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, cbzOffsetInternal+8), cbzOffsetInternal+8));
        builder.append("...\n");

        builder.append(BinToAsmPrinter.printSimplified(secondExpressionsPairElement1, conditionalJumpLocation));
        builder.append(BinToAsmPrinter.printSimplified(secondExpressionsPairElement2, conditionalJumpLocation+4));

        if (((secondExpressionsPairElement2 >> 26 & 0b111111) == 0x5)){
            builder.append("...\n");
            int conditionalJumpLocation2 = ((secondExpressionsPairElement2 & 0x3ffffff) * 4 + (conditionalJumpLocation+4)) & 0xfffff;

            builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, conditionalJumpLocation2), conditionalJumpLocation2));
            builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, conditionalJumpLocation2+4), conditionalJumpLocation2+4));

        }
        else {
            builder.append("NO CONDITIONAL JUMP ON 2nd iteration (HeuristicEs3)");
        }
        return builder.toString();
    }
}
