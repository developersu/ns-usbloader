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
package nsusbloader.Utilities.patches.fs.finders;

import nsusbloader.Utilities.patches.AHeuristic;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HeuristicFsWizard {
    /*
    private final List<AHeuristic> all;
    private final List<AHeuristic> found;
    private final List<AHeuristic> wantLessEntropy;
     */
    private final boolean isFat;
    private List<AHeuristic> all;
    private List<AHeuristic> found;
    private List<AHeuristic> wantLessEntropy;

    private final StringBuilder errorsAndNotes;

    private int offset1 = -1;
    private int offset2 = -1;

    public HeuristicFsWizard(long fwVersion, byte[] where, boolean isFat) throws Exception{
        this.isFat = isFat;
        this.errorsAndNotes = new StringBuilder();

        if (isFat){
            this.all = Arrays.asList(
                    new HeuristicFsFAT1(fwVersion, where),
                    new HeuristicFsFAT2(fwVersion, where)
            );
        }
        else {
            System.out.println("TODO: IMPLEMENT FOR EXFAT");
            return;
            /*
            this.all = Arrays.asList(
                    new HeuristicFsExFAT1(fwVersion, where),
                    new HeuristicFsExFAT2(fwVersion, where)
            );
             */
        }
/*
        this.found = all.stream()
                .filter(AHeuristic::isFound)
                .collect(Collectors.toList());

        if (found.isEmpty())
            throw new Exception("Nothing found!");

        this.wantLessEntropy = all.stream()
                .filter(AHeuristic::wantLessEntropy)
                .collect(Collectors.toList());
/*                                                                                                                      FIXME
        shareOffsetsWithEachOther();

        assignOffset1();
        assignOffset2();
         */
    }

    private void shareOffsetsWithEachOther(){
        for (AHeuristic es : wantLessEntropy) {
            if (shareWithNext(es))
                return;
        }
    }
    private boolean shareWithNext(AHeuristic es){
        try {
            for (AHeuristic foundEs : found) {
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

    public String getErrorsAndNotes(){
        return errorsAndNotes.toString();
    }

    public String getDebug(){
        StringBuilder builder = new StringBuilder();
        if (isFat)
            builder.append("\t\t--[  FAT32  ]--\n");
        else
            builder.append("\t\t--[  ExFAT  ]--\n");
        if (all.get(0).isFound()){
            builder.append("\t\t-=== 1 ===-\n");
            builder.append(all.get(0).getDetails());
        }
        if (all.get(1).isFound()){
            builder.append("\t\t-=== 2 ===-\n");
            builder.append(all.get(1).getDetails());
        }
        return builder.toString();
    }

    public int getOffset1() { return offset1; }
    public int getOffset2() { return offset2; }
}
