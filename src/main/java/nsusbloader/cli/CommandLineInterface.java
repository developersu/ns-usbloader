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

import nsusbloader.NSLMain;
import nsusbloader.Utilities.Rcm;
import org.apache.commons.cli.*;

import java.io.File;

import java.util.prefs.Preferences;

public class CommandLineInterface {

    public CommandLineInterface(String[] args) {
        if (noRealKeys(args)){
            System.out.println("Try 'ns-usbloader --help' for more information.");
            return;
        }

        final Options cliOptions = createCliOptions();

        CommandLineParser cliParser = new DefaultParser();
        try{
            CommandLine cli = cliParser.parse(cliOptions, args);
            if (cli.hasOption('v') || cli.hasOption("version")){
                handleVersion();
                return;
            }
            if (cli.hasOption('h') || cli.hasOption("help")){
                handleHelp(cliOptions);
                return;
            }
            if (cli.hasOption('r') || cli.hasOption("rcm")){
                final String payloadArgument = cli.getOptionValue("rcm");
                handleRcm(payloadArgument);
                return;
            }
            if (cli.hasOption("c") || cli.hasOption("clean")){
                handleSettingClean();
                return;
            }
            if (cli.hasOption("n") || cli.hasOption("tfn")){
                final String[] tfnArguments = cli.getOptionValues("tfn");
                new TinfoilNet(tfnArguments);
                return;
            }
        }
        catch (ParseException pe){
            System.out.println(pe.getLocalizedMessage() +
                    "\nTry 'ns-usbloader --help' for more information.");
        }
        catch (IncorrectSetupException iee){
            System.out.println(iee.getLocalizedMessage());
        }
        catch (InterruptedException ignore){}
        catch (Exception e){
            System.out.println("CLI error");
            e.printStackTrace();
        }
    }

    private boolean noRealKeys(String[] args){
        return (args.length > 0 && ! args[0].startsWith("-"));
    }

    private Options createCliOptions(){
        final Options options = new Options();

        final Option rcmOption = Option.builder("r")
                .longOpt("rcm")
                .desc("Send payload")
                .hasArg(true)
                .argName("[PATH/]payload.bin")
                .numberOfArgs(1)
                .build();

        final Option cleanSettingsOption = Option.builder("c")
                .longOpt("clean")
                .desc("Remove/reset settings and exit")
                .hasArg(false)
                .build();

        final Option versionOption = Option.builder("v")
                .longOpt("version")
                .desc("Show application version")
                .hasArg(false)
                .build();

        final Option helpOption = Option.builder("h")
                .longOpt("help")
                .desc("Show this help")
                .hasArg(false)
                .build();

        /* Tinfoil network mode options */
        final Option tinfoilNetOption = Option.builder("n")
                .longOpt("tfn")
                .desc("Install via Tinfoil/Awoo Network mode. Check '-n help' for information.")
                .hasArgs()
                .argName("...")
                .build();


        final OptionGroup group = new OptionGroup();
        group.addOption(rcmOption);
        group.addOption(tinfoilNetOption);
        group.addOption(cleanSettingsOption);
        group.addOption(versionOption);
        group.addOption(helpOption);

        options.addOptionGroup(group);

        return options;
    }

    private void handleVersion(){
        System.out.println("NS-USBloader " + NSLMain.appVersion);
    }
    private void handleSettingClean() throws Exception {
        if (Preferences.userRoot().nodeExists("NS-USBloader")) {
            Preferences.userRoot().node("NS-USBloader").removeNode();
            System.out.println("Settings removed");
        }
        else
            System.out.println("There are no settings in system to remove");
    }
    private void handleRcm(String payload) throws InterruptedException{
        boolean isWindows = System.getProperty("os.name").toLowerCase().replace(" ", "").contains("windows");

        if (isWindows) {
            if (! payload.matches("^.:\\\\.*$"))
                payload = System.getProperty("user.dir") + File.separator + payload;
        }
        else {
            if (! payload.startsWith("/"))
                payload = System.getProperty("user.dir") + File.separator + payload;
        }

        Rcm rcm = new Rcm(payload);
        Thread rcmThread = new Thread(rcm);
        rcmThread.start();
        rcmThread.join();
    }
    private void handleHelp(Options cliOptions){
        new HelpFormatter().printHelp(
                120,
                "NS-USBloader.jar [OPTION]... [FILE]...",
                "options:",
                cliOptions,
                "\n");
    }

}
