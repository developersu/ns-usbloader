package nsusbloader.cli;

import nsusbloader.COM.ICommunications;
import nsusbloader.COM.USB.UsbCommunications;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TinfoilUsb {

    private final String[] arguments;
    private List<File> filesList;

    public TinfoilUsb(String[] arguments) throws InterruptedException, IncorrectSetupException{
        this.arguments = arguments;
        checkArguments();
        parseFilesArguments();
        runTinfoilBackend();
    }

    private void checkArguments() throws IncorrectSetupException{
        if (arguments == null || arguments.length == 0) {
            throw new IncorrectSetupException("No files?\n" +
                    "Try 'ns-usbloader -h' for more information.");
        }
    }

    private void parseFilesArguments() throws IncorrectSetupException{
        filesList = new ArrayList<>();
        File file;

        for (String arg : arguments) {
            file = new File(arg);
            if (file.exists())
                filesList.add(file);
        }

        if (filesList.size() == 0) {
            throw new IncorrectSetupException("File(s) doesn't exist.\n" +
                    "Try 'ns-usbloader -n help' for more information.");
        }
    }

    private void runTinfoilBackend() throws InterruptedException{
        ICommunications task = new UsbCommunications(filesList, "TinFoil", false);
        Thread thread = new Thread(task);
        thread.start();
        thread.join();
    }
}
