package nsusbloader;

class MediatorControl {
    private boolean isTransferActive = false;
    private NSLMainController applicationController;

    static MediatorControl getInstance(){
        return MediatorControlHold.INSTANCE;
    }

    private static class MediatorControlHold {
        private static final MediatorControl INSTANCE = new MediatorControl();
    }
    void registerController(NSLMainController controller){
        this.applicationController = controller;
    }

    synchronized void setTransferActive(boolean state) {
        isTransferActive = state;
        applicationController.notifyTransmissionStarted(state);
    }
    synchronized boolean getTransferActive() {
        return this.isTransferActive;
    }
}
