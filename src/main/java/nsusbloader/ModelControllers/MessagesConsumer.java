/*
    Copyright 2019-2024 Dmitry Isaenko

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
import nsusbloader.Controllers.Payload;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EFileStatus;
import nsusbloader.NSLDataTypes.EModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessagesConsumer extends AnimationTimer {
    private static final MediatorControl mediator = MediatorControl.INSTANCE;
    private static final TextArea logsArea = mediator.getLogArea();
    private static final ProgressBar progressBar = mediator.getProgressBar();;
    private static final ResourceBundle resourceBundle = mediator.getResourceBundle();

    private final BlockingQueue<String> msgQueue;
    private final BlockingQueue<Double> progressQueue;
    private final HashMap<String, EFileStatus> statusMap;
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
        this.progressQueue = progressQueue;
        this.statusMap = statusMap;
        this.oneLinerStatus = oneLinerStatus;

        progressBar.setProgress(0.0);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        logsArea.clear();
        mediator.setTransferActive(appModuleType, true, new Payload());
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

        if (isInterrupted)                 // safe, could not be interrupted while HashMap populating
            updateElementsAndStop();
    }

    private void updateElementsAndStop(){
        Payload payload = new Payload(
                resourceBundle.getString(oneLinerStatus.get() ? "done_txt" : "failure_txt"),
                statusMap);

        mediator.setTransferActive(appModuleType, false, payload);
        progressBar.setProgress(0.0);

        this.stop();
    }

    public void interrupt(){
        this.isInterrupted = true;
    }
}