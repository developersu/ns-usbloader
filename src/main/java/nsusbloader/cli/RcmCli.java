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
package nsusbloader.cli;

import nsusbloader.Utilities.Rcm;

import java.io.File;

public class RcmCli {
    RcmCli(String argument) throws InterruptedException, IncorrectSetupException{
        runBackend(argument);
    }

    private void runBackend(String payload) throws InterruptedException{
        Rcm rcm = new nsusbloader.Utilities.Rcm(payload);
        Thread rcmThread = new Thread(rcm);
        rcmThread.start();
        rcmThread.join();
    }
}
