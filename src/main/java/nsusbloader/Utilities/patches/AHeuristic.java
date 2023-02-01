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
/**
 * Searches instructions (via known patterns) that follows 'specific instruction' we want to patch.
 * Returns offset of the pattern. Not offset of the 'specific instruction'.
 * */
public abstract class AHeuristic {
    protected boolean isLDR(int expression){ return (expression >> 22 & 0x2FF) == 0x2e5; }// LDR ! Sounds like LDP, don't mess up
    protected boolean isLDP(int expression){ return (expression >> 22 & 0x1F9) == 0xA1; }// LDP !
    protected boolean isCBNZ(int expression){ return (expression >> 24 & 0x7f) == 0x35; }
    protected boolean isMOV(int expression){ return (expression >> 23 & 0xff) == 0xA5; }
    protected boolean isTBZ(int expression){ return (expression >> 24 & 0x7f) == 0x36; }
    protected boolean isLDRB_LDURB(int expression){ return (expression >> 21 & 0x7f7) == 0x1c2; }
    protected boolean isMOV_REG(int expression){ return (expression & 0x7FE0FFE0) == 0x2A0003E0; }
    protected boolean isB(int expression) { return (expression >> 26 & 0x3f) == 0x5; }
    protected boolean isBL(int expression){ return (expression >> 26 & 0x3f) == 0x25; }
    protected boolean isADD(int expression){ return (expression >> 23 & 0xff) == 0x22; }
    public abstract boolean isFound();
    public abstract boolean wantLessEntropy();
    public abstract int getOffset() throws Exception;
    public abstract String getDetails();

    /**
     * Should be used if wantLessEntropy() == true
     * @return isFound();
     * */
    public abstract boolean setOffsetsNearby(int offsetNearby);
}
