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
package nsusbloader.Utilities.patches.fs;

import libKonogonka.Converter;
import libKonogonka.fs.NCA.NCAProvider;

import java.util.List;
import java.util.concurrent.Callable;

class FsNcaSearchTask implements Callable<NCAProvider> {
    private final List<NCAProvider> ncaProviders;

    FsNcaSearchTask(List<NCAProvider> ncaProviders){
        this.ncaProviders = ncaProviders;
    }

    @Override
    public NCAProvider call() {
        try {
            for (NCAProvider ncaProvider : ncaProviders) {
                String titleId = Converter.byteArrToHexStringAsLE(ncaProvider.getTitleId());
                if (titleId.equals("0100000000000819") || titleId.equals("010000000000081b")) { // eq. FAT || exFAT
                    return ncaProvider;
                }
            }
            return null;
        }
        catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
