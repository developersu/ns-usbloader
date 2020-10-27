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

import nsusbloader.com.net.NETCommunications;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TinfoilNetCli {

    private final String[] arguments;

    private String nsIp;

    private String hostIp = "";
    private String hostPortNum = "";
    //private String hostExtras = ""; // TODO: Add 'don't serve requests' option or remove this

    private int parseFileSince = 1;

    private List<File> filesList;

    TinfoilNetCli(String[] arguments) throws InterruptedException, IncorrectSetupException{
        this.arguments = arguments;
        checkArguments();
        parseNsIP();
        parseHostSettings();
        parseFilesArguments();
        runTinfoilNetBackend();
    }

    private void checkArguments() throws IncorrectSetupException{
        if (arguments == null || arguments.length == 0) {
            throw new IncorrectSetupException("No arguments.\n" +
                    "Try 'ns-usbloader -n help' for more information.");
        }

        if (arguments.length == 1){
            if (isHelpDirective(arguments[0])){
                showHelp();
                return;
            }
            else
                throw new IncorrectSetupException("Not enough arguments.\n" +
                        "Try 'ns-usbloader -n help' for more information.");
        }

        if (arguments.length == 2 && arguments[1].startsWith("hostip=")) {
            throw new IncorrectSetupException("Not enough arguments.\n" +
                    "Try 'ns-usbloader -n help' for more information.");
        }
    }

    private boolean isHelpDirective(String argument){
        return argument.equals("help");
    }
    private void showHelp() throws IncorrectSetupException{
        throw new IncorrectSetupException("Usage:\n"
                + "\tns-usbloader -n nsip=<arg1> [hostip=<arg2>] FILE1 ...\n"
                + "\tns-usbloader --tfn nsip=<arg1> [hostip=<arg2>] FILE1 ..."
                + "\n\nOptions:"
                + "\n\tnsip=<ip>\t\tDefine NS IP address (mandatory)"
                + "\n\thostip=<ip[:port]>\tDefine this host IP address. Will be obtained automatically if not set.");
    }

    private void parseNsIP() throws IncorrectSetupException{
        String argument1 = arguments[0];

        if (! argument1.startsWith("nsip=")) {
            throw new IncorrectSetupException("First argument must be 'nsip=<ip_address>'\n" +
                    "Try 'ns-usbloader -n help' for more information.");
        }

        nsIp = argument1.replaceAll("^nsip=", "");

        if (nsIp.isEmpty()) {
            throw new IncorrectSetupException("No spaces allowed before or after 'nsip=<ip_address>' argument.\n" +
                    "Try 'ns-usbloader -n help' for more information.");
        }
    }

    private void parseHostSettings(){
        String argument2 = arguments[1];

        if (! argument2.startsWith("hostip="))
            return;

        parseFileSince = 2;
        hostIp = argument2.replaceAll("(^hostip=)|(:.+?$)|(:$)", "");

        if (argument2.contains(":"))
            hostPortNum = argument2.replaceAll("(^.+:)", "");
        //    hostPortNum = argument2.replaceAll("(^.+:)|(/.+?$)|(/$)", "");
        //if (argument2.contains("/"))
        //    hostExtras = argument2.replaceAll("^[^/]*/", "");
    }

    private void parseFilesArguments() throws IncorrectSetupException{
        filesList = new ArrayList<>();
        File file;

        for (; parseFileSince < arguments.length; parseFileSince++) {
            file = new File(arguments[parseFileSince]);
            if (file.exists())
                filesList.add(file);
        }

        if (filesList.size() == 0) {
            throw new IncorrectSetupException("File(s) doesn't exist.\n" +
                    "Try 'ns-usbloader -n help' for more information.");
        }
    }

    private void runTinfoilNetBackend() throws InterruptedException{
        NETCommunications netCommunications = new NETCommunications(
                filesList,
                nsIp,
                false,
                hostIp,
                hostPortNum,
                "");
        Thread netCommThread = new Thread(netCommunications);
        netCommThread.setDaemon(true);
        netCommThread.start();
        netCommThread.join();
    }
}
