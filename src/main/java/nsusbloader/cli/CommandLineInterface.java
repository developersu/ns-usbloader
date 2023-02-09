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
import org.apache.commons.cli.*;

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
                new RcmCli(payloadArgument);
                return;
            }
            if (cli.hasOption("c") || cli.hasOption("clean")){
                handleSettingClean();
                return;
            }
            if (cli.hasOption("n") || cli.hasOption("tfn")){
                final String[] arguments = cli.getOptionValues("tfn");
                new TinfoilNetCli(arguments);
                return;
            }
            if (cli.hasOption("t") || cli.hasOption("tinfoil")){
                final String[] arguments = cli.getOptionValues("tinfoil");
                new TinfoilUsbCli(arguments);
                return;
            }
            if (cli.hasOption("g") || cli.hasOption("goldleaf")){
                final String[] arguments = cli.getOptionValues("goldleaf");
                new GoldLeafCli(arguments);
                return;
            }
            if (cli.hasOption("experimental")){
                final String[] arguments = cli.getOptionValues("experimental");
                new ExperimentalCli(arguments);
                return;
            }
            /*
            if (cli.hasOption("x") || cli.hasOption("nxdt")){
                final String[] arguments = cli.getOptionValues("nxdt");
                new NxdtCli(arguments);
                return;
            }
            */
            if (cli.hasOption("s") || cli.hasOption("split")){
                final String[] arguments = cli.getOptionValues("split");
                new SplitCli(arguments);
                return;
            }
            if (cli.hasOption("m") || cli.hasOption("merge")){
                final String[] arguments = cli.getOptionValues("merge");
                new MergeCli(arguments);
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
                .desc("Install via Awoo Network mode. Check '-n help' for information.")
                .hasArgs()
                .argName("...")
                .build();
        /* Tinfoil/Awoo USB */
        final Option tinfoilOption = Option.builder("t")
                .longOpt("tinfoil")
                .desc("Install via Awoo USB mode.")
                .hasArgs()
                .argName("FILE...")
                .build();
        /* GoldLeaf USB */
        final Option glOption = Option.builder("g")
                .longOpt("goldleaf")
                .desc("Install via GoldLeaf mode. Check '-g help' for information.")
                .hasArgs()
                .argName("...")
                .build();
        final Option experimentalOption = Option.builder()
                .longOpt("experimental")
                .desc("Enable testing and experimental functions")
                .hasArgs()
                .argName("y|n")
                .build();
        /* nxdumptool */
        /*
        final Option nxdtOption = Option.builder("x")
                .longOpt("nxdt")
                .desc("Handle nxdumptool connections.")
                .hasArg()
                .argName("DIRECTORY")
                .build();
         */
        final Option splitOption = Option.builder("s")
                .longOpt("split")
                .desc("Split files. Check '-s help' for information.")
                .hasArgs()
                .argName("...")
                .build();
        final Option mergeOption = Option.builder("m")
                .longOpt("merge")
                .desc("Merge files. Check '-m help' for information.")
                .hasArgs()
                .argName("...")
                .build();

        final OptionGroup group = new OptionGroup();
        group.addOption(rcmOption);
        group.addOption(tinfoilNetOption);
        group.addOption(cleanSettingsOption);
        group.addOption(versionOption);
        group.addOption(helpOption);
        group.addOption(tinfoilOption);
        group.addOption(glOption);
        group.addOption(experimentalOption);
        //group.addOption(nxdtOption);
        group.addOption(splitOption);
        group.addOption(mergeOption);

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
    private void handleHelp(Options cliOptions){
        new HelpFormatter().printHelp(
                120,
                "NS-USBloader.jar [OPTION]... [FILE]...",
                "options:",
                cliOptions,
                "\n");
    }

}
