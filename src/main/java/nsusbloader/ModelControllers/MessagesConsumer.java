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

import javafx.animation.AnimationTimer;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import nsusbloader.Controllers.NSTableViewController;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessagesConsumer extends AnimationTimer {
    private final BlockingQueue<String> msgQueue;
    private final TextArea logsArea;

    private final BlockingQueue<Double> progressQueue;
    private final ProgressBar progressBar;
    private final HashMap<String, EFileStatus> statusMap;
    private final NSTableViewController tableViewController;
    private final EModule appModuleType;

    private final AtomicBoolean oneLinerStatus;

    private boolean isInterrupted;

    MessagesConsumer(EModule appModuleType,
                     BlockingQueue<String> msgQueue,
                     BlockingQueue<Double> progressQueue,
                     HashMap<String, EFileStatus> statusMap,
                     AtomicBoolean oneLinerStatus){
        this.appModuleType = appModuleType;
        this.isInterrupted = false;

        this.msgQueue = msgQueue;
        this.logsArea = MediatorControl.getInstance().getContoller().logArea;

        this.progressQueue = progressQueue;
        this.progressBar = MediatorControl.getInstance().getContoller().progressBar;

        this.statusMap = statusMap;
        this.tableViewController = MediatorControl.getInstance().getGamesController().tableFilesListController;

        this.oneLinerStatus = oneLinerStatus;

        progressBar.setProgress(0.0);

        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        MediatorControl.getInstance().setBgThreadActive(true, appModuleType);
    }

    @Override
    public void handle(long l){
        ArrayList<String> messages = new ArrayList<>();
        int msgReceived = msgQueue.drainTo(messages);
        if (msgReceived > 0)
            messages.forEach(logsArea::appendText);

        ArrayList<Double> progress = new ArrayList<>();
        int progressReceived = progressQueue.drainTo(progress);
        if (progressReceived > 0) {
            progress.forEach(prg -> {
                if (prg != 1.0)
                    progressBar.setProgress(prg);
                else
                    progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            });
        }

        if (isInterrupted)                 // It's safe 'cuz it's could't be interrupted while HashMap populating
            updateElementsAndStop();
    }

    private void updateElementsAndStop(){
        MediatorControl.getInstance().setBgThreadActive(false, appModuleType);
        progressBar.setProgress(0.0);

        if (statusMap.size() > 0){
            for (String key : statusMap.keySet())
                tableViewController.setFileStatus(key, statusMap.get(key));
        }

        switch (appModuleType){
            case RCM:
                MediatorControl.getInstance().getRcmController().setOneLineStatus(oneLinerStatus.get());
                break;
            case NXDT:
                MediatorControl.getInstance().getNxdtController().setOneLineStatus(oneLinerStatus.get());
                break;
            case SPLIT_MERGE_TOOL:
                MediatorControl.getInstance().getSplitMergeController().setOneLineStatus(oneLinerStatus.get());
                break;
        }
        this.stop();
    }

    public void interrupt(){
        this.isInterrupted = true;
    }
}