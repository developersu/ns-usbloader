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
package nsusbloader.Utilities.patches.es;

import libKonogonka.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimplyFind {
    private String what;
    private final byte[] where;

    private Matcher matcherHex;
    private Matcher matcherDot;

    private final List<Integer> findings = new ArrayList<>();
    private final int statementLength;
    private final List<SearchBlock> searchBlocks = new ArrayList<>();
    /**
     * Abstraction layer for searching patterns like "..CAFE..BE" in bytes array.
     * It's 'String' combination of hex values and '.' which stands for unknown value.
     * Returns offset of the first symbol.
     * */
    public SimplyFind(String what, byte[] where){
        this.where = where;
        if (! what.contains(".")){
            doKMPSearch(Converter.hexStringToByteArray(what), 0);
            this.statementLength = what.length()/2;
            return;
        }
        this.what = what.replaceAll("\\.", "\\.\\.");
        this.statementLength = this.what.length()/2;

        buildSearchingSequence();
        complexSearch();
    }
    private void buildSearchingSequence(){
        Pattern patternHex = Pattern.compile("[0-9]|[A-F]|[a-f]");
        Pattern patternDot = Pattern.compile("\\.");
        this.matcherHex = patternHex.matcher(what);
        this.matcherDot = patternDot.matcher(what);

        int nextDotPos = 0;
        int nextHexPos;

        while(true){
            nextHexPos = getNextNumberPosition(nextDotPos);
            if (nextHexPos == -1)
                break;

            nextDotPos = getNextDotPosition(nextHexPos);
            if (nextDotPos == -1) {
                searchBlocks.add(new SearchBlock(what.substring(nextHexPos), nextHexPos));
                break;
            }
            String searchStatement = what.substring(nextHexPos, nextDotPos);
            searchBlocks.add(new SearchBlock(searchStatement, nextHexPos));
        }
    }
    private int getNextNumberPosition(int since){
        if (matcherHex.find(since))
            return matcherHex.start();
        return -1;
    }

    private int getNextDotPosition(int since){
        if (matcherDot.find(since))
            return matcherDot.start();
        return -1;
    }

    private void complexSearch(){
        SearchBlock block = searchBlocks.get(0);
        doKMPSearch(block.statement, block.offsetInStatement);
        findings.removeIf(this::searchForward);
    }
    private boolean searchForward(int offset){
        for (int i = 1; i < searchBlocks.size(); i++) {
            SearchBlock block = searchBlocks.get(i);
            if (! doDumbSearch(block.statement, offset+block.offsetInStatement)){
                return true;
            }
        }
        return false;
    }

    private void doKMPSearch(byte[] subject, int skip){
        int whereSize = where.length;
        int subjectSize = subject.length;

        int[] pf = new int[subjectSize];

        int j = 0;
        for (int i = 1; i < subjectSize; i++ ) {
            while ((j > 0) && (subject[j] != subject[i]))
                j = pf[j-1];
            if (subject[j] == subject[i])
                j++;
            pf[i] = j;
        }

        j = 0;
        for (int i = 0; i < whereSize; i++){
            while ((j > 0) && (subject[j] != where[i]))
                j = pf[j - 1];
            if (subject[j] == where[i])
                j++;
            if (j == subjectSize) {
                findings.add(i-j+1-skip);
                j = 0;
            }
        }
    }

    private boolean doDumbSearch(byte[] subject, int since){
        for (int i = 0; i < subject.length; i++) {
            if (where[since + i] != subject[i])
                return false;
        }
        return true;
    }

    public int getStatementLength() {
        return statementLength;
    }

    public List<Integer> getResults(){
        return findings;
    }

    private static class SearchBlock{
        byte[] statement;
        int offsetInStatement;

        SearchBlock(String statement, int offset){
            if (statement != null) {
                this.statement = Converter.hexStringToByteArray(statement);
            }
            this.offsetInStatement = offset/2;
        }
    }
}
