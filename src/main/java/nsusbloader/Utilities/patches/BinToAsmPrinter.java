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
package nsusbloader.Utilities.patches;

import libKonogonka.Converter;
import nsusbloader.NSLMain;

public class BinToAsmPrinter {
    static {
        boolean notWindows = ! System.getProperty("os.name").toLowerCase().contains("windows");

        if(notWindows && NSLMain.isCli){
            ANSI_RESET = "\u001B[0m";
            ANSI_GREEN = "\u001B[32m";
            ANSI_BLUE = "\u001B[34m";
            ANSI_YELLOW = "\u001B[33m";
            ANSI_PURPLE = "\u001B[35m";
            ANSI_CYAN = "\u001B[36m";
            ANSI_RED = "\u001B[31m";
        }
        else {
            ANSI_RESET = ANSI_RED = ANSI_GREEN = ANSI_BLUE = ANSI_YELLOW = ANSI_PURPLE = ANSI_CYAN = "";
        }
    }
    private static final String ANSI_RESET;
    private static final String ANSI_RED;
    private static final String ANSI_GREEN;
    private static final String ANSI_BLUE;
    private static final String ANSI_YELLOW;
    private static final String ANSI_PURPLE;
    private static final String ANSI_CYAN;

    public static String print(int instructionExpression, int offset){
        if (instructionExpression == 0xd503201f)
            return printNOP(instructionExpression);

        if ((instructionExpression & 0x7FE0FFE0) == 0x2A0003E0) {
            return printMOVRegister(instructionExpression);
        }

        switch ((instructionExpression >> 23 & 0b011111111)){
            case 0xA5:
                return printMOV(instructionExpression);
            case 0x62:
                if (((instructionExpression & 0x1f) == 0x1f)){
                    return printCMN(instructionExpression);
                }
        }

        switch (instructionExpression >> 24 & 0xff) {
            case 0x34:
            case 0xb4:
                return printCBZ(instructionExpression, offset);
            case 0xb5:
            case 0x35:
                return printCBNZ(instructionExpression, offset);
            case 0x36:
            case 0xb6:
                return printTBZ(instructionExpression, offset);
            case 0x54:
                return printBConditional(instructionExpression, offset);
        }
        switch ((instructionExpression >> 26 & 0b111111)) {
            case 0x5:
                return printB(instructionExpression, offset);
            case 0x25:
                return printBL(instructionExpression, offset);
        }

        return printUnknown(instructionExpression);
    }
    public static String printSimplified(int instructionExpression, int offset){
        if (instructionExpression == 0xd503201f)
            return printNOPSimplified(instructionExpression, offset);

        if ((instructionExpression & 0x7FE0FFE0) == 0x2A0003E0) {
            return printMOVRegisterSimplified(instructionExpression, offset);
        }

        switch (instructionExpression >> 22 & 0b1011111111) {
            case 0x2e5:
                return printLRDImmUnsignSimplified(instructionExpression, offset);
            case 0xe5:
                return printLRDBImmUnsignSimplified(instructionExpression, offset);
        }

        if ((instructionExpression >> 21 & 0x7FF) == 0x1C2)
            return printImTooLazy("LDURB", instructionExpression, offset);

        // same to (afterJumpExpression >> 23 & 0x1F9) != 0xA1
        switch (instructionExpression >> 22 & 0x1FF){
            case 0xA3: // 0b10100011
            case 0xA7: // 0b10100111
            case 0xA5: // 0b10100101
                return printImTooLazy("LDP", instructionExpression, offset);
        }

        switch ((instructionExpression >> 23 & 0x1ff)){
            case 0xA5:
                return printMOVSimplified(instructionExpression, offset);
            case 0x22:
                return printImTooLazy("ADD", instructionExpression, offset);
            case 0x62:
                if (((instructionExpression & 0x1f) == 0x1f)){
                    return printCMNSimplified(instructionExpression, offset);
                }
            case 0xA2:
                return printSUBSimplified(instructionExpression, offset);
            case 0xE2:
            case 0x1e2:
                return printCMPSimplified(instructionExpression, offset);
            case 0x24:
            case 0x124:
                return printANDSimplified(instructionExpression, offset);
        }

        switch (instructionExpression >> 24 & 0xff) {
            case 0x34:
            case 0xb4:
                return printCBZSimplified(instructionExpression, offset);
            case 0xb5:
            case 0x35:
                return printCBNZSimplified(instructionExpression, offset);
            case 0x36:
            case 0xb6:
                return printTBZSimplified(instructionExpression, offset);
            case 0x54:
                return printBConditionalSimplified(instructionExpression, offset);
            case 0xeb:
            case 0x6b:
                if ((instructionExpression & 0x1f) == 0b11111)
                    return printCMPShiftedRegisterSimplified(instructionExpression, offset);
        }

        switch (instructionExpression >> 26 & 0b111111) {
            case 0x5:
                return printBSimplified(instructionExpression, offset);
            case 0x25:
                return printBLSimplified(instructionExpression, offset);
        }

        if ((instructionExpression >> 10 & 0x3FFFFF) == 0x3597c0 && ((instructionExpression & 0x1F) == 0))
            return printRetSimplified(instructionExpression, offset);
        return  printUnknownSimplified(instructionExpression, offset);
    }

    private static String printCBZ(int instructionExpression, int offset){
        int conditionalJumpLocation = ((instructionExpression >> 5 & 0x7FFFF) * 4 + offset) & 0xfffff;

        return String.format(ANSI_YELLOW + "sf == 0 ? <Wt> else <Xt>\n" +
                                     "CBZ <?t>, <label>          |.....CBZ signature......|\n" +
                         ANSI_CYAN + "                            sf 0  1  1    0  1  0  0   |imm19..........................................................||Rd.............|" + ANSI_RESET + "\n" +
                         ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n\n"+
                        ANSI_YELLOW + "CBZ " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                (instructionExpression >> 31 == 0) ? "w" : "x", (instructionExpression & 0b11111),
                conditionalJumpLocation, (conditionalJumpLocation + 0x100));
    }


    private static String printCBNZ(int instructionExpression, int offset){
        int conditionalJumpLocation = ((instructionExpression >> 5 & 0x7FFFF) * 4 + offset) & 0xfffff;

        return String.format(ANSI_YELLOW + "sf == 0 ? <Wt> else <Xt>\n" +
                                    "CBNZ <?t>, <label>         |.....CBZ signature......|\n" +
                        ANSI_CYAN + "                            sf 0  1  1    0  1  0     |imm19..........................................................||Rd.............|" + ANSI_RESET + "\n" +
                        ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n\n"+
                        ANSI_YELLOW + "CBNZ " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                (instructionExpression >> 31 == 0) ? "w" : "x", (instructionExpression & 0b11111),
                conditionalJumpLocation, (conditionalJumpLocation + 0x100));
    }

    private static String printCMN(int instructionExpression){
        int Rn = instructionExpression >> 5 & 0x1F;
        int imm = instructionExpression >> 10 & 0xFFF;

        return String.format(ANSI_YELLOW + "sf == 0 ? <Wt> else <Xt>\n" +
                                    "CMN <?n>, <label>           |.....CMN signature...........|                                                            |..CMN signature.|\n" +
                          ANSI_CYAN+"                            sf 0  1  1    0  0  0  1    0 |imm12......................................||Rn.............| 1    1  1  1  1" + ANSI_RESET + "\n" +
                        ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n\n" +
                        ANSI_YELLOW + "CMN " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + "\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                (instructionExpression >> 31 == 0) ? "w" : "x", Rn, imm);
    }

    private static String printB(int instructionExpression, int offset){
        int conditionalJumpLocationPatch = ((instructionExpression & 0x3ffffff) * 4 + offset) & 0xfffff;

        return String.format(ANSI_YELLOW+"B <label>                  |....B signature...|\n" +
                            "                           "+ANSI_CYAN+" 0  0  0  1    0  1 |imm26...................................................................................|" + ANSI_RESET + "\n" +
                             ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n" +
                        ANSI_YELLOW + "%s " +  ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                ((instructionExpression >> 26 & 0b111111) == 5)?"B":"Some weird stuff",
                conditionalJumpLocationPatch, (conditionalJumpLocationPatch + 0x100));
    }


    private static String printBL(int instructionExpression, int offset){
        int conditionalJumpLocationPatch = ((instructionExpression & 0x3ffffff) * 4 + offset) & 0xfffff;

        return String.format(ANSI_YELLOW+"BL <label>                 |...BL signature...|\n" +
                            "                           "+ANSI_CYAN+" 1  0  0  1    0  1 |imm26...................................................................................|" + ANSI_RESET + "\n" +
                        ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n" +
                        ANSI_YELLOW + "%s " +  ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                ((instructionExpression >> 26 & 0b111111) == 25)?"BL":"Some weird stuff",
                conditionalJumpLocationPatch, (conditionalJumpLocationPatch + 0x100));
    }


    private static String printMOV(int instructionExpression){
        int imm16 = instructionExpression >> 5 & 0xFFFF;
        int sfHw = (instructionExpression >> 22 & 1);

        return String.format(ANSI_YELLOW + "sf == 0 && hw == 0x ? <Wt> else <Xt>\n" +
                                        "MOV <?t>, <label>          |.....MOV signature...........|\n" +
                             ANSI_CYAN +"                            sf 1  0  1    0  0  1  0    1 |hw...|imm16.................................................||Rd.............|" + ANSI_RESET + "\n" +
                            ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n" +
                        ANSI_YELLOW + "MOV " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + "\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                (sfHw == 0) ? "w" : "x", (instructionExpression & 0b11111), imm16);
    }

    private static String printMOVRegister(int instructionExpression){
        String sfHw = (instructionExpression >> 31 & 1) == 0 ? "W" : "X";
        int Rm = instructionExpression >> 16 & 0xF;
        int Rd = instructionExpression & 0xF;

        return String.format(ANSI_YELLOW + "sf == 0 && hw == 0x ? <Wt> else <Xt>\n" +
                                    "MOV (register) <?d>, <?m>  |.....MOV (register) signature.......|\n" +
                         ANSI_CYAN +"                            sf 0  1  0    1  0  1  0    0  0  0 |Rm..............|  0  0  0  0    0  0  1  1    1  1  1 |Rd.............|" + ANSI_RESET + "\n" +
                        ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n" +
                        ANSI_YELLOW + "MOV(reg) " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "%s%d" + ANSI_RESET + "\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                sfHw, Rm, sfHw, Rd);
    }

    private static String printNOP(int instructionExpression){
        return String.format(
                ANSI_YELLOW+"NOP                        |.....NOP signature..........................................................................................|\n" +
                        ANSI_CYAN +"                            1  1  0  1    0  1  0  1    0  0  0  0    0  0  1  1    0  0  1  0    0  0  0  0    0  0  0  1    1  1  1  1 " + ANSI_RESET + "\n" +
                        ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n"+
                        "Instruction         (BE) :  %s | %s\n" +
                        ANSI_YELLOW + "%s" + ANSI_RESET + "\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                (instructionExpression == 0xd503201f)?"NOP":"Some weird stuff");
    }

    private static String printTBZ(int instructionExpression, int offset){
        int xwSelector = (instructionExpression >> 31 & 1);
        int imm = instructionExpression >> 18 & 0b11111;
        int Rt = instructionExpression & 0b11111;
        int label = (offset + (instructionExpression >> 5 & 0x3fff) * 4) & 0xfffff;

        return String.format(ANSI_YELLOW + "sf == 0 && hw == 0x ? <Wt> else <Xt>\n" +
                        "TBZ <?t>,#<imm>, <label>   |.....TBZ signature.......|\n" +
                        ANSI_CYAN+"                            b5 0  1  1    0  1  1  0   |b40.............|imm14.........................................||Rt.............|" + ANSI_RESET + "\n" +
                        ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n" +
                        ANSI_YELLOW + "TBZ " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ", " + ANSI_PURPLE + "%x" + ANSI_RESET + "\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                (xwSelector == 0) ? "w" : "x", Rt, imm, label);
    }

    private static String printBConditional(int instructionExpression, int offset){
        int conditionalJumpLocation = ((instructionExpression >> 4 & 0b1111111111111111111) * 4 + offset) & 0xfffff;

        return String.format(
                        ANSI_YELLOW+"B.%s <label>               |...B.cond signature.......|\n" +
                        ANSI_CYAN+"                            0  1  0  1    0  1  0  0   |imm19..........................................................| 0   |.condit...|" + ANSI_RESET + "\n" +
                        ANSI_GREEN +"                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n" +
                        ANSI_YELLOW + "B.%s " + ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                getBConditionalMarker(instructionExpression & 0xf),
                intAsBinString(instructionExpression), intAsHexString(instructionExpression),
                getBConditionalMarker(instructionExpression & 0xf),
                conditionalJumpLocation, (conditionalJumpLocation + 0x100));
    }

    private static String printUnknown(int instructionExpression){
        return String.format(ANSI_RED + "                            31 30 29 28   27 26 25 24   23 22 21 20   19 18 17 16   15 14 13 12   11 10 9  8    7  6  5  4    3  2  1  0 " + ANSI_RESET + "\n" +
                        "Instruction         (BE) :  %s | %s\n",
                intAsBinString(instructionExpression), intAsHexString(instructionExpression));
    }

    private static String getBConditionalMarker(int cond){
        switch (cond){
            case 0b0000: return "EQ";
            case 0b0001: return "NE";
            case 0b0010: return "CS";
            case 0b0011: return "CC";
            case 0b0100: return "MI";
            case 0b0101: return "PL";
            case 0b0110: return "VS";
            case 0b0111: return "VC";
            case 0b1000: return "HI";
            case 0b1001: return "LS";
            case 0b1010: return "GE";
            case 0b1011: return "LT";
            case 0b1100: return "GT";
            case 0b1101: return "LE";
            case 0b1110: return "AL";
            default: return "??";
        }
        /*
            "__________________CheatSheet_____________________________________\n"+
                "0000 | EQ | Z set                     | equal\n"+
                "0001 | NE | Z clear                   | not equal\n"+
                "0010 | CS | C set                     | unsigned higher or same\n"+
                "0011 | CC | C clear                   | unsigned lower\n"+
                "0100 | MI | N set                     | negative\n"+
                "0101 | PL | N clear                   | positive or zero\n"+
                "0110 | VS | V set                     | overflow\n"+
                "0111 | VC | V clear                   | no overflow\n"+
                "1000 | HI | C set & V clear           | unsigned higher\n"+
                "1001 | LS | C clear or Z set          | unsigned lower or same\n"+
                "1010 | GE | N equals V                | greater or equal\n"+
                "1011 | LT | N not equals V            | less than\n"+
                "1100 | GT | Z clear AND (N equals V)  | greater that\n"+
                "1101 | LE | Z set OR (N not equals V) | less than or equal\n"+
                "1110 | AL | (ignored)                 | always\n";
         */
    }


    private static String printCBZSimplified(int instructionExpression, int offset){
        int conditionalJumpLocation = ((instructionExpression >> 5 & 0x7FFFF) * 4 + offset) & 0xfffff;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   CBZ         " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + " (" + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                (instructionExpression >> 31 == 0) ? "w" : "x", (instructionExpression & 0b11111),
                conditionalJumpLocation, (conditionalJumpLocation + 0x100));
    }

    private static String printCBNZSimplified(int instructionExpression, int offset){
        int conditionalJumpLocation = ((instructionExpression >> 5 & 0x7FFFF) * 4 + offset) & 0xfffff;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   CBNZ        " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + " (" + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                (instructionExpression >> 31 == 0) ? "w" : "x", (instructionExpression & 0b11111),
                conditionalJumpLocation, (conditionalJumpLocation + 0x100));
    }

    private static String printBSimplified(int instructionExpression, int offset){
        int conditionalJumpLocationPatch = ((instructionExpression & 0x3ffffff) * 4 + offset) & 0xfffff;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   B           " +  ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                conditionalJumpLocationPatch, (conditionalJumpLocationPatch + 0x100));
    }


    private static String printBLSimplified(int instructionExpression, int offset){
        int conditionalJumpLocationPatch = ((instructionExpression & 0x3ffffff) * 4 + offset) & 0xfffff;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   BL          " +  ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                conditionalJumpLocationPatch, (conditionalJumpLocationPatch + 0x100));
    }

    private static String printMOVSimplified(int instructionExpression, int offset){
        int imm16 = instructionExpression >> 5 & 0xFFFF;
        int sfHw = (instructionExpression >> 22 & 1);

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   MOV         " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                (sfHw == 0) ? "w" : "x", (instructionExpression & 0b11111), imm16);
    }

    private static String printNOPSimplified(int instructionExpression, int offset){
        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   NOP           " + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression);
    }

    private static String printTBZSimplified(int instructionExpression, int offset){
        int xwSelector = (instructionExpression >> 31 & 1);
        int imm = instructionExpression >> 18 & 0b11111;
        int Rt = instructionExpression & 0b11111;
        int label = offset + (instructionExpression >> 5 & 0x3fff) * 4;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   TBZ         " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ", " + ANSI_PURPLE + "%x" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                (xwSelector == 0) ? "w" : "x", Rt, imm, label);
    }

    private static String printBConditionalSimplified(int instructionExpression, int offset){
        int conditionalJumpLocation = ((instructionExpression >> 4 & 0b1111111111111111111) * 4 + offset) & 0xfffff;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   B.%s        " + ANSI_BLUE + "#0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ")\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                getBConditionalMarker(instructionExpression & 0xf),
                conditionalJumpLocation, (conditionalJumpLocation + 0x100));
    }
    private static String printImTooLazy(String name, int instructionExpression, int offset){
        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   "+name+"           . . . \n"+ ANSI_RESET,
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression);
    }

    private static String printSUBSimplified(int instructionExpression, int offset){
        String wx = (instructionExpression >> 31 == 0) ? "W" : "X";
        int Rt = instructionExpression & 0x1f;
        int Rn = instructionExpression >> 5 & 0x1F;
        int imm12 = instructionExpression >> 10 & 0xFFF; // unsigned only

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   SUB (imm)   " + ANSI_GREEN + "%s%d, " + ANSI_BLUE + "%s%d, #0x%x" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                wx, Rt, wx, Rn, imm12);
    }

    private static String printMOVRegisterSimplified(int instructionExpression, int offset){    //ADD (immediate)
        String sfHw = (instructionExpression >> 31 & 1) == 0 ? "W" : "X";
        int Rm = instructionExpression >> 16 & 0x1F;
        int Rd = instructionExpression & 0x1F;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   MOV (reg)   " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "%s%d" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                sfHw, Rm, sfHw, Rd);
    }

    private static String printCMNSimplified(int instructionExpression, int offset){
        int Rn = instructionExpression >> 5 & 0x1F;
        int imm = instructionExpression >> 10 & 0xFFF;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   CMN         " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "#0x%x" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                (instructionExpression >> 31 == 0) ? "w" : "x", Rn, imm);
    }

    private static String printLRDImmUnsignSimplified(int instructionExpression, int offset){
        String wx = (instructionExpression >> 31 == 0) ? "W" : "X";
        int Rt = instructionExpression & 0x1f;
        int Rn = instructionExpression >> 5 & 0xF;
        int imm12 = (instructionExpression >> 10 & 0xFFF) * 8; // unsigned only


        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   LDR(imm)    " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "[%s%d, #0x%x]" + ANSI_RESET + "     (note: unsigned offset)\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                wx, Rt, wx, Rn, imm12);
    }
    private static String printLRDBImmUnsignSimplified(int instructionExpression, int offset){
        String wx = (instructionExpression >> 31 == 0) ? "W" : "X";
        int Rt = instructionExpression & 0x1f;
        int Rn = instructionExpression >> 5 & 0xF;
        int imm12 = (instructionExpression >> 10 & 0xFFF) * 8; // unsigned only

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   LDRB(imm)   " + ANSI_GREEN + "%s%d " + ANSI_BLUE + "[%s%d, #0x%x]" + ANSI_RESET + "     (note: unsigned offset)\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                wx, Rt, wx, Rn, imm12);
    }

    private static String printCMPSimplified(int instructionExpression, int offset){
        String sf = (instructionExpression >> 31 == 0) ? "W" : "X";
        int Rn = instructionExpression >> 5 & 0x1F;
        int conditionalJumpLocation = (instructionExpression >> 10) & 0xfff;
        int LSL = (instructionExpression >> 22 & 0b1) == 1 ? 12 : 0;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   CMP         " + ANSI_GREEN + sf + "%d," +
                        ANSI_BLUE + "0x%x" + ANSI_RESET + " (Real: " + ANSI_BLUE + "#0x%x" + ANSI_RESET + ") " + ANSI_PURPLE + "LSL #%d" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                Rn,
                conditionalJumpLocation, (conditionalJumpLocation + 0x100),
                LSL);
    }

    private static String printCMPShiftedRegisterSimplified(int instructionExpression, int offset){
        String sf = (instructionExpression >> 31 == 0) ? "W" : "X";
        int Rn = instructionExpression >> 5 & 0x1F;
        int Rm = instructionExpression >> 16 & 0x1F;
        int imm6 = instructionExpression >> 10 & 0x3f;
        int LSL = (instructionExpression >> 22 & 0b11);
        String LSLStr;
        switch (LSL){
            case 0b00:
                LSLStr = "LSL";
                break;
            case 0b01:
                LSLStr = "LSR";
                break;
            case 0b10:
                LSLStr = "ASR";
                break;
            case 0b11:
                LSLStr = "RESERVED";
                break;
            default:
                LSLStr = "?";
        }

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   CMP (sr)    " + ANSI_GREEN + sf + "%d," +
                        ANSI_BLUE + sf + "%d " + ANSI_BLUE + LSLStr + ANSI_PURPLE + " %d" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression,
                Rn, Rm, imm6);
    }

    private static String printANDSimplified(int instructionExpression, int offset){
        String sf = (instructionExpression >> 31 == 0) ? "W" : "X";
        int Rn = instructionExpression & 0x1F;
        int Rd = instructionExpression >> 5 & 0x1F;
        int imm;
        if (sf.equals("W"))
            imm = instructionExpression >> 10 & 0xfff;
        else
            imm = instructionExpression >> 10 & 0x1fff;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   AND         " + ANSI_GREEN + sf + "%d, " + ANSI_BLUE +
                        sf + "%d" + ANSI_PURPLE + " # ??? 0b%s " + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression, Rn, Rd, Converter.intToBinaryString(imm));
    }

    private static String printRetSimplified(int instructionExpression, int offset){
        int Xn = (instructionExpression >> 5) & 0x1F;

        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   RET        " + ANSI_GREEN + " X%d" + ANSI_RESET + "\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression, Xn == 0 ? 30 : Xn);
    }

    private static String printUnknownSimplified(int instructionExpression, int offset){
        return String.format(
                "%06x 7100%06x "+ANSI_CYAN+"%08x (%08x)"+ANSI_YELLOW + "   ???          0b"+ANSI_RESET+ Converter.intToBinaryString(instructionExpression) +"\n",
                offset+0x100, offset,Integer.reverseBytes(instructionExpression), instructionExpression);
    }

    private static String intAsBinString(int number) {
        StringBuilder result = new StringBuilder();
        for(int i = 31; i >= 0 ; i--) {
            int mask = 1 << i;
            result.append((number & mask) != 0 ? "1" : "0");
            result.append("  ");
            if (i % 4 == 0)
                result.append("  ");
        }
        result.replace(result.length() - 1, result.length(), "");

        return result.toString();
    }
    private static String intAsHexString(int number) {
        number = Integer.reverseBytes(number);
        StringBuilder result = new StringBuilder();
        for(int i = 0; i <= 3 ; i++) {
            int mask = 0xff << i*8;
            result.append(String.format("%02x", (byte)((number & mask) >> i*8)));
            result.append(" ");
        }
        result.replace(result.length() - 1, result.length(), "");

        return result.toString();
    }
}