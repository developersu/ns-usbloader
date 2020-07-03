/*
    Copyright 2019-2020 Dmitry Isaenko

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
package nsusbloader.ModelControllers;

import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLMain;

public class Log {

    private Log(){}

    public static ILogPrinter getPrinter(EModule whoIsAsking){
        if (NSLMain.isCli)
            return new LogPrinterCli();
        else
            return new LogPrinterGui(whoIsAsking);
    }
}
