package nsusbloader;

import nsusbloader.Controllers.NSLMainController;

import java.util.concurrent.atomic.AtomicBoolean;

public class MediatorControl {
    private AtomicBoolean isTransferActive = new AtomicBoolean(false);  // Overcoded just for sure
    private NSLMainController applicationController;

    public static MediatorControl getInstance(){
        return MediatorControlHold.INSTANCE;
    }

    private static class MediatorControlHold {
        private static final MediatorControl INSTANCE = new MediatorControl();
    }
    public void setController(NSLMainController controller){
        this.applicationController = controller;
    }
    public NSLMainController getContoller(){ return this.applicationController; }

    public synchronized void setTransferActive(boolean state) {
        isTransferActive.set(state);
        applicationController.getFrontCtrlr().notifyTransmissionStarted(state);
    }
    public synchronized boolean getTransferActive() { return this.isTransferActive.get(); }
}
