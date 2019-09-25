package nsusbloader.ModelControllers;

import javafx.animation.AnimationTimer;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import nsusbloader.Controllers.NSTableViewController;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EFileStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

public class MessagesConsumer extends AnimationTimer {
    private final BlockingQueue<String> msgQueue;
    private final TextArea logsArea;

    private final BlockingQueue<Double> progressQueue;
    private final ProgressBar progressBar;
    private final HashMap<String, EFileStatus> statusMap;
    private final NSTableViewController tableViewController;

    private boolean isInterrupted;

    MessagesConsumer(BlockingQueue<String> msgQueue, BlockingQueue<Double> progressQueue, HashMap<String, EFileStatus> statusMap){
        this.isInterrupted = false;

        this.msgQueue = msgQueue;
        this.logsArea = MediatorControl.getInstance().getContoller().logArea;

        this.progressQueue = progressQueue;
        this.progressBar = MediatorControl.getInstance().getContoller().progressBar;

        this.statusMap = statusMap;
        this.tableViewController = MediatorControl.getInstance().getContoller().FrontTabController.tableFilesListController;

        progressBar.setProgress(0.0);

        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        MediatorControl.getInstance().setTransferActive(true);
    }

    @Override
    public void handle(long l) {
        ArrayList<String> messages = new ArrayList<>();
        int msgRecieved = msgQueue.drainTo(messages);
        if (msgRecieved > 0)
            messages.forEach(logsArea::appendText);

        ArrayList<Double> progress = new ArrayList<>();
        int progressRecieved = progressQueue.drainTo(progress);
        if (progressRecieved > 0) {
            progress.forEach(prg -> {
                if (prg != 1.0)
                    progressBar.setProgress(prg);
                else
                    progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            });
        }

        if (isInterrupted) {                                                // It's safe 'cuz it's could't be interrupted while HashMap populating
            MediatorControl.getInstance().setTransferActive(false);
            progressBar.setProgress(0.0);

            if (statusMap.size() > 0)
                for (String key : statusMap.keySet())
                    tableViewController.setFileStatus(key, statusMap.get(key));
            this.stop();
        }
    }

    public void interrupt(){
        this.isInterrupted = true;
    }
}