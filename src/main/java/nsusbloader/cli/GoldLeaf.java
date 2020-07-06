package nsusbloader.cli;

import nsusbloader.COM.ICommunications;
import nsusbloader.COM.USB.UsbCommunications;
import nsusbloader.Controllers.SettingsController;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GoldLeaf {

    private final String[] arguments;
    private List<File> filesList;
    private String goldLeafVersion;
    private boolean filterForNsp;

    private int parseFileSince = 1;

    public GoldLeaf(String[] arguments) throws InterruptedException, IncorrectSetupException{
        this.arguments = arguments;

        checkArguments();
        parseGoldLeafVersion();
        parseFilesArguments();
        runGoldLeafBackend();
    }

    public void checkArguments() throws IncorrectSetupException{
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
                + "\n\tver=<goldleaf_version>\tDefine GoldLeaf version (mandatory)\n\n"
                + "\n\tfilter\t\nShow only *.nsp in GoldLeaf (optional)\n\n"
                + getGlSupportedVersions());
    }
    private String getGlSupportedVersions(){
        StringBuilder builder = new StringBuilder("Supported versions: \n");

        for (String a : SettingsController.glSupportedVersions){
            builder.append("\t");
            builder.append(a);
            builder.append("\n");
        }
        return builder.toString();
    }

    public void parseGoldLeafVersion() throws IncorrectSetupException{
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

        for (String version : SettingsController.glSupportedVersions){
            if (version.equals(goldLeafVersion))
                return;
        }

        throw new IncorrectSetupException("GoldLeaf " + goldLeafVersion + " is not supported.\n" +
                getGlSupportedVersions());
    }

    public void parseFilesArguments() throws IncorrectSetupException{
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

    public void runGoldLeafBackend() throws InterruptedException {
        ICommunications task = new UsbCommunications(filesList,
                "GoldLeaf"+goldLeafVersion,
                filterForNsp);
        Thread thread = new Thread(task);
        thread.start();
        thread.join();
    }
}
