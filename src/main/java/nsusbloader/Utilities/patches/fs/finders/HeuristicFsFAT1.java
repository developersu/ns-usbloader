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

import java.util.ArrayList;
import java.util.List;

class HeuristicFsFAT1 extends AHeuristic {
    private static final String PATTERN0 = ".1e42b91fc14271";
    private static final String PATTERN1 = "...9408...1F05.....54"; // ...94 081C0012 1F050071 4101
/*
    710006eba0  c0  02  40  f9     ldr       x0 , [ x22 ]
    710006eba4  5c  c9  02  94     bl        FUN_7100121114                             undefined FUN_7100121114()
    710006eba8  08  1c  00  12     and       w8 , w0 , # 0xff
 */
    private final List<Integer> findings;
    private final byte[] where;

    HeuristicFsFAT1(long fwVersion, byte[] where){
        this.where = where;
        String pattern = getPattern(fwVersion);
        SimplyFind simplyfind = new SimplyFind(pattern, where);
        List<Integer> temporary = simplyfind.getResults();
        if (fwVersion >= 15300){
            this.findings = new ArrayList<>();
            temporary.forEach(var -> findings.add(var + 4));
        }
        else
            findings = temporary;

        System.out.println("\t\tFAT32 # 1 +++++++++++++++++++++++++++++++");
        for (Integer find : findings) {
            System.out.println(getDetails(find));
            System.out.println("------------------------------------------------------------------");
        }
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
        int firstOffsetInternal = value - 4;
        int firstExpression = Converter.getLEint(where, firstOffsetInternal);
        int conditionalJumpLocation = ((firstExpression >> 5 & 0x7FFFF) * 4 + firstOffsetInternal) & 0xfffff;

        int secondExpressionsPairElement1 = Converter.getLEint(where, conditionalJumpLocation);
        int secondExpressionsPairElement2 = Converter.getLEint(where, conditionalJumpLocation+4);

        StringBuilder builder = new StringBuilder();
        //builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal-4*11), firstOffsetInternal-4*11));
        //builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal-4*10), firstOffsetInternal-4*10));
        //builder.append("^ ^ ^ ...\n");
        builder.append(BinToAsmPrinter.printSimplified(firstExpression, firstOffsetInternal));
        builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal+4), firstOffsetInternal+4));
        builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal+8), firstOffsetInternal+8));
        builder.append(BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal+12), firstOffsetInternal+12));
        builder.append("...\n");
        builder.append(BinToAsmPrinter.printSimplified(secondExpressionsPairElement1, conditionalJumpLocation));
        builder.append(BinToAsmPrinter.printSimplified(secondExpressionsPairElement2, conditionalJumpLocation+4));

        return builder.toString();
    }
    @Override
    public String getDetails(){
        int firstOffsetInternal = findings.get(0) - 4;
        int firstExpression = Converter.getLEint(where, firstOffsetInternal);
        int conditionalJumpLocation = ((firstExpression >> 5 & 0x7FFFF) * 4 + firstOffsetInternal) & 0xfffff;

        int secondExpressionsPairElement1 = Converter.getLEint(where, conditionalJumpLocation);
        int secondExpressionsPairElement2 = Converter.getLEint(where, conditionalJumpLocation+4);

        return BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal - 4 * 11), firstOffsetInternal - 4 * 11) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal - 4 * 10), firstOffsetInternal - 4 * 10) +
                "^ ^ ^ ...\n" +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal - 4 * 2), firstOffsetInternal - 4 * 2) +
                "^ ^ ^ ...\n" +
                BinToAsmPrinter.printSimplified(firstExpression, firstOffsetInternal) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal + 4), firstOffsetInternal + 4) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, firstOffsetInternal + 8), firstOffsetInternal + 8) +
                "...\n" +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement1, conditionalJumpLocation) +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement2, conditionalJumpLocation + 4);
    }
}
