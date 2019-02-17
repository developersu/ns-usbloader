package nsusbloader;

import java.util.concurrent.atomic.AtomicBoolean;

class MediatorControl {
    private AtomicBoolean isTransferActive = new AtomicBoolean(false);  // Overcoded just for sure
    private NSLMainController applicationController;

    static MediatorControl getInstance(){
        return MediatorControlHold.INSTANCE;
    }

    private static class MediatorControlHold {
        private static final MediatorControl INSTANCE = new MediatorControl();
    }
    void setController(NSLMainController controller){
        this.applicationController = controller;
    }
    NSLMainController getContoller(){ return this.applicationController; }

    synchronized void setTransferActive(boolean state) {
        isTransferActive.set(state);
        applicationController.notifyTransmissionStarted(state);
    }
    synchronized boolean getTransferActive() { return this.isTransferActive.get(); }
}
