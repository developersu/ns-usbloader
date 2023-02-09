/*
    Copyright 2019-2023 Dmitry Isaenko
     
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

import nsusbloader.AppPreferences;

public class ExperimentalCli {
    ExperimentalCli(String[] arguments) throws IncorrectSetupException{
        if (arguments == null || arguments.length == 0)
            throw new IncorrectSetupException("No arguments.\nShould be 'y' or 'n'");

        if (arguments.length > 1)
            throw new IncorrectSetupException("Too many arguments.\nShould be 'y' or 'n' only");

        String arg = arguments[0].toLowerCase().substring(0, 1);

        if (arg.equals("y")) {
            AppPreferences.getInstance().setPatchesTabInvisible(false);
            System.out.println("Experimental functions enabled");
            return;
        }
        if (arg.equals("n")) {
            AppPreferences.getInstance().setPatchesTabInvisible(true);
            System.out.println("Experimental functions disabled");
            return;
        }

        throw new IncorrectSetupException("Incorrect arguments.\nCould be 'y' or 'n' only");
    }
}
