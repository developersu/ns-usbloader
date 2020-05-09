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
        mainCtrler.getRcmCtrlr().notifySmThreadStarted(isActive, appModuleType);
        mainCtrler.getNXDTabController().notifyThreadStarted(isActive, appModuleType);
    }
    public synchronized boolean getTransferActive() { return this.isTransferActive.get(); }
}
