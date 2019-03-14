package nsusbloader.USB;

import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LogPrinter {
    private MessagesConsumer msgConsumer;
    private BlockingQueue<String> msgQueue;
    private BlockingQueue<Double> progressQueue;
    private HashMap<String, EFileStatus> statusMap;      // BlockingQueue for literally one object. TODO: read more books ; replace to hashMap

    LogPrinter(){
        this.msgQueue = new LinkedBlockingQueue<>();
        this.progressQueue = new LinkedBlockingQueue<>();
        this.statusMap =  new HashMap<>();
        this.msgConsumer = new MessagesConsumer(this.msgQueue, this.progressQueue, this.statusMap);
        this.msgConsumer.start();
    }
    /**
     * This is what will print to textArea of the application.
     * */
    public void print(String message, EMsgType type){
        try {
            switch (type){
                case PASS:
                    msgQueue.put("[ PASS ] "+message+"\n");
                    break;
                case FAIL:
                    msgQueue.put("[ FAIL ] "+message+"\n");
                    break;
                case INFO:
                    msgQueue.put("[ INFO ] "+message+"\n");
                    break;
                case WARNING:
                    msgQueue.put("[ WARN ] "+message+"\n");
                    break;
                default:
                    msgQueue.put(message);
            }
        }catch (InterruptedException ie){
            ie.printStackTrace();
        }
    }
    /**
     * Update progress for progress bar
     * */
    public void updateProgress(Double value) throws InterruptedException{
        progressQueue.put(value);
    }
    /**
     * When we're done
     * */
    public void updateAndClose(HashMap<String, File> nspMap, EFileStatus status){
        for (String fileName: nspMap.keySet())
            statusMap.put(fileName, status);
        msgConsumer.interrupt();
    }
}
