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

import nsusbloader.AppPreferences;
import nsusbloader.com.usb.UsbCommunications;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GoldLeafCli {

    private final String[] arguments;
    private List<File> filesList;
    private String goldLeafVersion;
    private boolean filterForNsp;

    private int parseFileSince = 1;

    GoldLeafCli(String[] arguments) throws InterruptedException, IncorrectSetupException{
        this.arguments = arguments;

        checkArguments();
        parseGoldLeafVersion();
        parseFilesArguments();
        runGoldLeafBackend();
    }

    private void checkArguments() throws IncorrectSetupException{
        if (arguments == null || arguments.length == 0) {
            throw new IncorrectSetupException("No arguments.\n" +
                    "Try 'ns-usbloader -g help' for more information.");
        }

        if (arguments.length == 1){
            if (isHelpDirective(arguments[0])){
                showHelp();
            }
        }

        if (arguments.length > 1 && arguments[1].equals("filter")){
            filterForNsp = true;
            parseFileSince = 2;
        }
    }
    private boolean isHelpDirective(String argument){
        return argument.equals("help");
    }
    private void showHelp() throws IncorrectSetupException{
        throw new IncorrectSetupException("Usage:\n"
                + "\tns-usbloader -g ver=<arg1> [filter] FILE1 ...\n"
                + "\tns-usbloader --goldleaf ver=<arg1> [filter] FILE1 ..."
                + "\n\nOption:"
                + "\n\tver=<goldleaf_version>\tDefine GoldLeaf version (mandatory)"
                + "\n\tfilter\t\t\tShow only *.nsp in GoldLeaf (optional)\n\n"
                + getGlSupportedVersions());
    }
    private String getGlSupportedVersions(){
        StringBuilder builder = new StringBuilder("Supported versions: \n");

        for (String a : AppPreferences.GOLDLEAF_SUPPORTED_VERSIONS){
            builder.append("\t");
            builder.append(a);
            builder.append("\n");
        }
        return builder.toString();
    }

    private void parseGoldLeafVersion() throws IncorrectSetupException{
        String argument1 = arguments[0];

        if (! argument1.startsWith("ver=")) {
            throw new IncorrectSetupException("First argument must be 'ver=<goldleaf_version>'\n" +
                    "Try 'ns-usbloader -g help' for more information.");
        }

        goldLeafVersion = argument1.replaceAll("^ver=", "");

        if (goldLeafVersion.isEmpty()) {
            throw new IncorrectSetupException("No spaces allowed before or after 'ver=<goldleaf_version>' argument.\n" +
                    "Try 'ns-usbloader -g help' for more information.");
        }

        for (String version : AppPreferences.GOLDLEAF_SUPPORTED_VERSIONS){
            if (version.equals(goldLeafVersion))
                return;
        }

        throw new IncorrectSetupException("GoldLeaf " + goldLeafVersion + " is not supported.\n" +
                getGlSupportedVersions());
    }

    private void parseFilesArguments() throws IncorrectSetupException{
        filesList = new ArrayList<>();
        File file;

        for (; parseFileSince < arguments.length; parseFileSince++) {
            file = new File(arguments[parseFileSince]);
            if (file.exists())
                filesList.add(file);
        }

        if (filesList.size() == 0 && goldLeafVersion.equals("v0.5")) {
            throw new IncorrectSetupException("File(s) doesn't exist but should be set for GoldLeaf v0.5.\n" +
                    "Try 'ns-usbloader -g help' for more information.");
        }
    }

    private void runGoldLeafBackend() throws InterruptedException {
        Runnable task = new UsbCommunications(filesList,
                "GoldLeaf "+goldLeafVersion,
                filterForNsp);
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
        thread.join();
    }
}
