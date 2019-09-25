package nsusbloader.ModelControllers;

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

    public LogPrinter(){
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
    public void updateProgress(Double value) {
        try {
            progressQueue.put(value);
        }
        catch (InterruptedException ignored){}               // TODO: Do something with this
    }
    /**
     * When we're done - update status
     * */
    public void update(HashMap<String, File> nspMap, EFileStatus status){
        for (File file: nspMap.values())
            statusMap.putIfAbsent(file.getName(), status);
    }
    /**
     * When we're done - update status
     * */
    public void update(File file, EFileStatus status){
        statusMap.putIfAbsent(file.getName(), status);
    }
    /**
     * When we're done - close it
     * */
    public void close(){
        msgConsumer.interrupt();
    }
}
