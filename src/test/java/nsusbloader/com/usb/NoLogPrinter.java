package nsusbloader.com.usb;

import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.File;
import java.util.HashMap;

public class NoLogPrinter implements ILogPrinter {
    @Override
    public void print(String message, EMsgType type) { }

    @Override
    public void updateProgress(Double value) { }

    @Override
    public void update(HashMap<String, File> nspMap, EFileStatus status) { }

    @Override
    public void update(File file, EFileStatus status) { }

    @Override
    public void updateOneLinerStatus(boolean status) { }

    @Override
    public void close() { }
}
