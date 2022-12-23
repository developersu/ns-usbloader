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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HeuristicEsWizard {
    private final List<IHeuristicEs> all;
    private final List<IHeuristicEs> found;
    private final List<IHeuristicEs> wantLessEntropy;

    private final StringBuilder errorsAndNotes;

    private int offset1 = -1;
    private int offset2 = -1;
    private int offset3 = -1;

    public HeuristicEsWizard(long fwVersion, byte[] where) throws Exception{
        this.errorsAndNotes = new StringBuilder();

        this.all = Arrays.asList(
                new HeuristicEs1(where),
                new HeuristicEs2(where),
                new HeuristicEs3(fwVersion, where)
        );

        this.found = all.stream()
                .filter(IHeuristicEs::isFound)
                .collect(Collectors.toList());

        if (found.isEmpty())
            throw new Exception("Nothing found!");

        this.wantLessEntropy = all.stream()
                .filter(IHeuristicEs::wantLessEntropy)
                .collect(Collectors.toList());

        shareOffsetsWithEachOther();

        assignOffset1();
        assignOffset2();
        assignOffset3();
    }

    private void shareOffsetsWithEachOther(){
        for (IHeuristicEs es : wantLessEntropy) {
            if (shareWithNext(es))
                return;
        }
    }
    private boolean shareWithNext(IHeuristicEs es){
        try {
            for (IHeuristicEs foundEs : found) {
                if (es.setOffsetsNearby(foundEs.getOffset())) {
                    found.add(es);
                    wantLessEntropy.remove(es);
                    shareOffsetsWithEachOther();
                    return true;
                }
            }
        }
        catch (Exception e){ e.printStackTrace(); }
        return false;
    }

    private void assignOffset1(){
        try {
            offset1 = all.get(0).getOffset();
        }
        catch (Exception e){ errorsAndNotes.append(e.getLocalizedMessage()).append("\n"); }
    }
    private void assignOffset2(){
        try {
            offset2 = all.get(1).getOffset();
        }
        catch (Exception e){ errorsAndNotes.append(e.getLocalizedMessage()).append("\n"); }
    }
    private void assignOffset3(){
        try {
            offset3 = all.get(2).getOffset();
        }
        catch (Exception e){ errorsAndNotes.append(e.getLocalizedMessage()).append("\n"); }
    }

    public String getErrorsAndNotes(){
        return errorsAndNotes.toString();
    }

    public String getDebug(){
        StringBuilder builder = new StringBuilder();
        if (all.get(0).isFound()){
            builder.append("\t\t-=== 1 ===-\n");
            builder.append(all.get(0).getDetails());
        }
        if (all.get(1).isFound()){
            builder.append("\t\t-=== 2 ===-\n");
            builder.append(all.get(1).getDetails());
        }
        if (all.get(2).isFound()){
            builder.append("\t\t-=== 3 ===-\n");
            builder.append(all.get(2).getDetails());
        }
        return builder.toString();
    }

    public int getOffset1() { return offset1; }
    public int getOffset2() { return offset2; }
    public int getOffset3() { return offset3; }
}
