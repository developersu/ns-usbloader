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
import libKonogonka.Tools.NCA.NCAProvider;

import java.util.List;
import java.util.concurrent.Callable;

class EsNcaSearchTask implements Callable<NCAProvider> {
    private final List<NCAProvider> ncaProviders;

    EsNcaSearchTask(List<NCAProvider> ncaProviders){
        this.ncaProviders = ncaProviders;
    }

    @Override
    public NCAProvider call() {
        try {
            for (NCAProvider ncaProvider : ncaProviders) {
                String titleId = Converter.byteArrToHexStringAsLE(ncaProvider.getTitleId());
                if (titleId.startsWith("0100000000000033") && ncaProvider.getContentType() == 0) {
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
