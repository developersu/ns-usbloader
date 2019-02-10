package nsusbloader;

import javafx.animation.AnimationTimer;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;

public class MessagesConsumer extends AnimationTimer {
    private final BlockingQueue<String> msgQueue;
    private final TextArea logsArea;

    private final BlockingQueue<Double> progressQueue;
    private final ProgressBar progressBar;

    private boolean isInterrupted;

    MessagesConsumer(BlockingQueue<String> msgQueue, TextArea logsArea, BlockingQueue<Double> progressQueue, ProgressBar progressBar){
        this.msgQueue = msgQueue;
        this.logsArea = logsArea;

        this.progressBar = progressBar;
        this.progressQueue = progressQueue;

        progressBar.setProgress(0.0);
        this.isInterrupted = false;
        MediatorControl.getInstance().setTransferActive(true);
    }

    @Override
    public void handle(long l) {
        ArrayList<String> messages = new ArrayList<>();
        int msgRecieved = msgQueue.drainTo(messages);
        if (msgRecieved > 0)
            messages.forEach(msg -> logsArea.appendText(msg));

        ArrayList<Double> progress = new ArrayList<>();
        int progressRecieved = progressQueue.drainTo(progress);
        if (progressRecieved > 0)
            progress.forEach(prg -> progressBar.setProgress(prg));

        if (isInterrupted) {
            MediatorControl.getInstance().setTransferActive(false);
            progressBar.setProgress(0.0);
            this.stop();
        }
        //TODO
    }

    void interrupt(){
        this.isInterrupted = true;
    }
}
