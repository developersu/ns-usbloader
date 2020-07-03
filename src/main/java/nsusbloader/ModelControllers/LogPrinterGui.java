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
package nsusbloader.ModelControllers;

import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.NSLDataTypes.EMsgType;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogPrinterGui implements ILogPrinter {
    private final MessagesConsumer msgConsumer;
    private final BlockingQueue<String> msgQueue;
    private final BlockingQueue<Double> progressQueue;
    private final HashMap<String, EFileStatus> statusMap;      // BlockingQueue for literally one object. TODO: read more books ; replace to hashMap
    private AtomicBoolean oneLinerStatus;

    public LogPrinterGui(EModule whoIsAsking){
        this.msgQueue = new LinkedBlockingQueue<>();
        this.progressQueue = new LinkedBlockingQueue<>();
        this.statusMap =  new HashMap<>();
        this.oneLinerStatus = new AtomicBoolean();
        this.msgConsumer = new MessagesConsumer(whoIsAsking, this.msgQueue, this.progressQueue, this.statusMap, this.oneLinerStatus);
        this.msgConsumer.start();
    }
    /**
     * This is what will print to textArea of the application.
     * */
    @Override
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
        }
        catch (InterruptedException ie){
            ie.printStackTrace();
        }
    }
    /**
     * Update progress for progress bar
     * */
    @Override
    public void updateProgress(Double value) {
        try {
            progressQueue.put(value);
        }
        catch (InterruptedException ignored){}               // TODO: Do something with this
    }
    /**
     * When we're done - update status
     * */
    @Override
    public void update(HashMap<String, File> nspMap, EFileStatus status){
        for (File file: nspMap.values())
            statusMap.putIfAbsent(file.getName(), status);
    }
    /**
     * When we're done - update status
     * */
    @Override
    public void update(File file, EFileStatus status){
        statusMap.putIfAbsent(file.getName(), status);
    }

    @Override
    public void updateOneLinerStatus(boolean status){
        oneLinerStatus.set(status);
    }
    /**
     * When we're done - close it
     * */
    @Override
    public void close(){
        msgConsumer.interrupt();
    }
}
