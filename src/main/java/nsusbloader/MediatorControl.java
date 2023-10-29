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

import nsusbloader.Controllers.*;
import nsusbloader.NSLDataTypes.EModule;

import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

public class MediatorControl {
    private final AtomicBoolean isTransferActive = new AtomicBoolean(false);  // Overcoded just for sure
    private NSLMainController mainController;

    public static MediatorControl getInstance(){
        return MediatorControlHold.INSTANCE;
    }

    private static class MediatorControlHold {
        private static final MediatorControl INSTANCE = new MediatorControl();
    }
    public void setController(NSLMainController controller){
        this.mainController = controller;
    }

    public NSLMainController getContoller(){ return mainController; }
    public GamesController getGamesController(){ return mainController.getGamesCtrlr(); }
    public SettingsController getSettingsController(){ return mainController.getSettingsCtrlr(); }
    public SplitMergeController getSplitMergeController(){ return mainController.getSmCtrlr(); }
    public RcmController getRcmController(){ return mainController.getRcmCtrlr(); }
    public NxdtController getNxdtController(){ return mainController.getNXDTabController(); }
    public PatchesController getPatchesController(){ return mainController.getPatchesTabController(); }

    public ResourceBundle getResourceBundle(){
        return mainController.getResourceBundle();
    }

    public synchronized void setBgThreadActive(boolean isActive, EModule appModuleType) {
        isTransferActive.set(isActive);
        getGamesController().notifyThreadStarted(isActive, appModuleType);
        getSplitMergeController().notifyThreadStarted(isActive, appModuleType);
        getRcmController().notifyThreadStarted(isActive, appModuleType);
        getNxdtController().notifyThreadStarted(isActive, appModuleType);
        getPatchesController().notifyThreadStarted(isActive, appModuleType);
    }
    public synchronized boolean getTransferActive() { return this.isTransferActive.get(); }
    public void updateApplicationFont(String fontFamily, double fontSize){
        mainController.logArea.getScene().getRoot().setStyle(
                String.format("-fx-font-family: \"%s\"; -fx-font-size: %.0f;", fontFamily, fontSize));
    }
}
