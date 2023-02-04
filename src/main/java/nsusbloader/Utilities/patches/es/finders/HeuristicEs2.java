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
import nsusbloader.Utilities.patches.AHeuristic;
import nsusbloader.Utilities.patches.BinToAsmPrinter;
import nsusbloader.Utilities.patches.SimplyFind;

import java.util.ArrayList;
import java.util.List;

class HeuristicEs2 extends AHeuristic {
    private static final String PATTERN = ".D2.52";

    private List<Integer> findings;
    private final byte[] where;

    HeuristicEs2(byte[] where){
        this.where = where;
        find();

        this.findings.removeIf(this::dropStep1);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep2);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep3);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep4);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep5);
        if(findings.size() < 2)
            return;

        this.findings.removeIf(this::dropStep6);
    }
    private void find(){
        SimplyFind simplyfind = new SimplyFind(PATTERN, where);
        findings = new ArrayList<>();
        // This approach a bit different. We're looking for pattern we want to patch, not the next one.
        // So easier way is just to shift every value and pretend that nothing happened.
        for (int offset : simplyfind.getResults())
            findings.add(offset+4);
    }

    // Limit range
    private boolean dropStep1(int offsetOfPatternFound){
        return ((offsetOfPatternFound < 0x10000 || offsetOfPatternFound > 0xffffc));
    }
    // Is CBNZ next?
    private boolean dropStep2(int offsetOfPatternFound){
        return ! isCBNZ(Converter.getLEint(where, offsetOfPatternFound));
    }
    // Check what's above
    private boolean dropStep3(int offsetOfPatternFound){
        return ! isMOV(Converter.getLEint(where, offsetOfPatternFound-4));
    }
    // Check what's beyond or after jump
    private boolean dropStep4(int offsetOfPatternFound) {
        int nextExpression = Converter.getLEint(where, offsetOfPatternFound + 4);
        return ! isLDRB_LDURB(nextExpression); // Drop if not LDRB OR LDURB
    }

    // Check second after jump if LDR-TBZ
    private boolean dropStep5(int offsetOfPatternFound) {
        int expression = Converter.getLEint(where, offsetOfPatternFound);
        int afterJumpPosition = ((expression >> 5 & 0x7FFFF) * 4 + offsetOfPatternFound) & 0xfffff;
        int secondAfterJumpExpression = Converter.getLEint(where, afterJumpPosition+4);
        return ! isBL(secondAfterJumpExpression); //Second after jump = BL? No -> Drop
    }

    // Check second after jump if LDR-TBZ
    private boolean dropStep6(int offsetOfPatternFound) {
        int expression = Converter.getLEint(where, offsetOfPatternFound);
        int afterJumpPosition = ((expression >> 5 & 0x7FFFF) * 4 + offsetOfPatternFound) & 0xfffff;
        int forthAfterJumpExpression = Converter.getLEint(where, afterJumpPosition+12);
        return ! isBL(forthAfterJumpExpression); //Forth after jump = BL? No -> Drop
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
        int secondExpressionOffset = findings.get(0);

        int firstExpression = Converter.getLEint(where, secondExpressionOffset-4);
        int secondExpression = Converter.getLEint(where, secondExpressionOffset);
        int conditionalJumpLocation = 0;
        if ((secondExpression >> 24 & 0x7f) ==  0x35) {
            conditionalJumpLocation = ((secondExpression >> 5 & 0x7FFFF) * 4 + secondExpressionOffset) & 0xfffff;
        }
        else if ((firstExpression >> 24 & 0x7f) == 0x36) {
            conditionalJumpLocation = (secondExpressionOffset-4 + (firstExpression >> 5 & 0x3fff) * 4) & 0xfffff;
        }
        int secondExpressionsPairElement1 = Converter.getLEint(where, conditionalJumpLocation);
        int secondExpressionsPairElement2 = Converter.getLEint(where, conditionalJumpLocation + 4);
        int secondExpressionsPairElement3 = Converter.getLEint(where, conditionalJumpLocation + 8);
        int secondExpressionsPairElement4 = Converter.getLEint(where, conditionalJumpLocation + 12);

        return BinToAsmPrinter.printSimplified(Converter.getLEint(where, secondExpressionOffset - 4), secondExpressionOffset - 4) +
                BinToAsmPrinter.printSimplified(secondExpression, secondExpressionOffset) +
                BinToAsmPrinter.printSimplified(Converter.getLEint(where, secondExpressionOffset + 4), secondExpressionOffset + 4) +
                "...\n" +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement1, conditionalJumpLocation) +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement2, conditionalJumpLocation + 4) +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement3, conditionalJumpLocation + 8) +
                BinToAsmPrinter.printSimplified(secondExpressionsPairElement4, conditionalJumpLocation + 12);
    }
}
