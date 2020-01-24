package nsusbloader;

import nsusbloader.Controllers.NSLMainController;
import nsusbloader.NSLDataTypes.EModule;

import java.util.concurrent.atomic.AtomicBoolean;

public class MediatorControl {
    private AtomicBoolean isTransferActive = new AtomicBoolean(false);  // Overcoded just for sure
    private NSLMainController mainCtrler;

    public static MediatorControl getInstance(){
        return MediatorControlHold.INSTANCE;
    }

    private static class MediatorControlHold {
        private static final MediatorControl INSTANCE = new MediatorControl();
    }
    public void setController(NSLMainController controller){
        this.mainCtrler = controller;
    }
    public NSLMainController getContoller(){ return this.mainCtrler; }

    public synchronized void setBgThreadActive(boolean isActive, EModule appModuleType) {
        isTransferActive.set(isActive);
        mainCtrler.getFrontCtrlr().notifyTransmThreadStarted(isActive, appModuleType);
        mainCtrler.getSmCtrlr().notifySmThreadStarted(isActive, appModuleType);
    }
    public synchronized boolean getTransferActive() { return this.isTransferActive.get(); }
}
