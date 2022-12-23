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
/**
 * Searches instructions (via known patterns) that follows 'specific instruction' we want to patch.
 * Returns offset of the pattern. Not offset of the 'specific instruction'.
 * */
interface IHeuristicEs {
    default boolean isLDR(int expression){ return (expression >> 22 & 0x2FF) == 0x2e5; }// LDR ! Sounds like LDP, don't mess up
    default boolean isLDP(int expression){ return (expression >> 22 & 0x1F9) == 0xA1; }// LDP !
    default boolean isCBNZ(int expression){ return (expression >> 24 & 0x7f) == 0x35; }
    default boolean isMOV(int expression){ return (expression >> 23 & 0xff) == 0xA5; }
    default boolean isTBZ(int expression){ return (expression >> 24 & 0x7f) == 0x36; }
    default boolean isLDRB_LDURB(int expression){ return (expression >> 21 & 0x7f7) == 0x1c2; }
    default boolean isMOV_REG(int expression){ return (expression & 0x7FE0FFE0) == 0x2A0003E0; }
    default boolean isB(int expression) { return (expression >> 26 & 0x3f) == 0x5; }
    default boolean isBL(int expression){ return (expression >> 26 & 0x3f) == 0x25; }
    default boolean isADD(int expression){ return (expression >> 23 & 0xff) == 0x22; }
    boolean isFound();
    boolean wantLessEntropy();
    int getOffset() throws Exception;
    String getDetails();

    /**
     * Should be used if wantLessEntropy() == true
     * @return isFound();
     * */
    boolean setOffsetsNearby(int offsetNearby);
}
