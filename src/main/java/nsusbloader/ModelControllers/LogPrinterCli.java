package nsusbloader.ModelControllers;

import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.File;
import java.util.HashMap;

public class LogPrinterCli implements ILogPrinter{
    @Override
    public void print(String message, EMsgType type) {
        switch (type){
            case PASS:
                System.out.println("P: "+message);
                break;
            case FAIL:
                System.out.println("F: "+message);
                break;
            case INFO:
                System.out.println("I: "+message);
                break;
            case WARNING:
                System.out.println("W: "+message);
                break;
            default:
                System.out.println(message);
        }
    }

    @Override
    public void updateProgress(Double value) { }
    @Override
    public void update(HashMap<String, File> nspMap, EFileStatus status) { }
    @Override
    public void update(File file, EFileStatus status) { }
    @Override
    public void updateOneLinerStatus(boolean status){  }
    @Override
    public void close() {
        System.out.println("\n-\n");
    }
}
