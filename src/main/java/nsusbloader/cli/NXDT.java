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

import nsusbloader.Utilities.nxdumptool.NxdtTask;

import java.io.File;

public class NXDT {

    private final String[] arguments;
    private String saveTo;

    public NXDT(String[] arguments) throws InterruptedException, IncorrectSetupException{
        this.arguments = arguments;
        parseArgument();
        runBackend();
    }

    private void parseArgument() throws IncorrectSetupException{
        final File file = new File(arguments[0]);

        if (! file.exists()){
            throw new IncorrectSetupException("Directory does not exist.\n" +
                    "Try 'ns-usbloader -h' for more information.");
        }

        if (file.isFile()){
            throw new IncorrectSetupException("Argument is file while directory expected.\n" +
                    "Try 'ns-usbloader -h' for more information.");
        }

        saveTo = arguments[0];
    }

    private void runBackend() throws InterruptedException{
        NxdtTask nxdtTask = new NxdtTask(saveTo);
        Thread thread = new Thread(nxdtTask);
        thread.setDaemon(true);
        thread.start();
        thread.join();
    }
}
