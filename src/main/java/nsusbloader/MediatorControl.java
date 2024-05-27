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
package nsusbloader;

import javafx.application.HostServices;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import nsusbloader.Controllers.*;
import nsusbloader.NSLDataTypes.EModule;

import java.util.ResourceBundle;

public class MediatorControl {
    public static final MediatorControl INSTANCE = new MediatorControl();

    private ResourceBundle resourceBundle;
    private TransfersPublisher transfersPublisher;
    private HostServices hostServices;
    private GamesController gamesController;
    private SettingsController settingsController;

    private TextArea logArea;
    private ProgressBar progressBar;

    private MediatorControl(){}

    public void configure(ResourceBundle resourceBundle,
                          SettingsController settingsController,
                          TextArea logArea,
                          ProgressBar progressBar,
                          GamesController gamesController,
                          TransfersPublisher transfersPublisher) {
        this.resourceBundle = resourceBundle;
        this.settingsController = settingsController;
        this.gamesController = gamesController;
        this.logArea = logArea;
        this.progressBar = progressBar;
        this.transfersPublisher = transfersPublisher;
    }
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public HostServices getHostServices() { return hostServices; }
    public ResourceBundle getResourceBundle(){ return resourceBundle; }
    public SettingsController getSettingsController() { return settingsController; }
    public GamesController getGamesController() { return gamesController; }
    public TextArea getLogArea() { return logArea; }
    public ProgressBar getProgressBar() { return progressBar; }

    public synchronized void setTransferActive(EModule appModuleType, boolean isActive, Payload payload) {
        transfersPublisher.setTransferActive(appModuleType, isActive, payload);
    }

    public synchronized boolean getTransferActive() {
        return transfersPublisher.getTransferActive();
    }
}
