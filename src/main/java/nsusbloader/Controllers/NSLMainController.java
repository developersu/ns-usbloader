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
package nsusbloader.Controllers;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import nsusbloader.*;
import nsusbloader.ModelControllers.UpdatesChecker;

import java.net.*;
import java.util.List;
import java.util.ResourceBundle;

public class NSLMainController implements Initializable {

    private ResourceBundle resourceBundle;

    @FXML
    public TextArea logArea;            // Accessible from Mediator

    @FXML
    public ProgressBar progressBar;            // Accessible from Mediator

    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab GamesTabHolder, RCMTabHolder, SMTabHolder;

    @FXML
    private GamesController GamesTabController;
    @FXML
    private SettingsController SettingsTabController;
    @FXML
    private SplitMergeController SplitMergeTabController;
    @FXML
    private RcmController RcmTabController;
    @FXML
    private NxdtController NXDTabController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.resourceBundle = rb;
        logArea.setText(rb.getString("tab3_Txt_GreetingsMessage")+" "+ NSLMain.appVersion+"!\n");
        if (System.getProperty("os.name").toLowerCase().startsWith("lin"))
            if (!System.getProperty("user.name").equals("root"))
                logArea.appendText(rb.getString("tab3_Txt_EnteredAsMsg1")+System.getProperty("user.name")+"\n"+rb.getString("tab3_Txt_EnteredAsMsg2") + "\n");

        logArea.appendText(rb.getString("tab3_Txt_GreetingsMessage2")+"\n");

        MediatorControl.getInstance().setController(this);

        if (AppPreferences.getInstance().getAutoCheckUpdates()){
            checkForUpdates();
        }

        openLastOpenedTab();
    }
    private void checkForUpdates(){
        Task<List<String>> updTask = new UpdatesChecker();
        updTask.setOnSucceeded(event->{
            List<String> result = updTask.getValue();
            if (result != null){
                if (!result.get(0).isEmpty()) {
                    SettingsTabController.getGenericSettings().setNewVersionLink(result.get(0));
                    ServiceWindow.getInfoNotification(
                            resourceBundle.getString("windowTitleNewVersionAval"),
                            resourceBundle.getString("windowTitleNewVersionAval") + ": " + result.get(0) + "\n\n" + result.get(1));
                }
            }
            else
                ServiceWindow.getInfoNotification(
                        resourceBundle.getString("windowTitleNewVersionUnknown"),
                        resourceBundle.getString("windowBodyNewVersionUnknown"));
        });
        Thread updates = new Thread(updTask);
        updates.setDaemon(true);
        updates.start();
    }
    /**
     * Get resources
     * TODO: Find better solution; used in UsbCommunications() -> GL -> SelectFile command
     * @return ResourceBundle
     */
    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
    /**
     * Provide hostServices to Settings tab
     * */
    public void setHostServices(HostServices hs ){ SettingsTabController.getGenericSettings().registerHostServices(hs);}

    /**
     * Get 'Settings' controller
     * Used by FrontController
     * */
    public SettingsController getSettingsCtrlr(){
        return SettingsTabController;
    }

    public GamesController getGamesCtrlr(){
        return GamesTabController;
    }

    public SplitMergeController getSmCtrlr(){
        return SplitMergeTabController;
    }

    public RcmController getRcmCtrlr(){ return RcmTabController; }

    public NxdtController getNXDTabController(){ return NXDTabController; }
    /**
     * Save preferences before exit
     * */
    public void exit(){
        GamesTabController.updatePreferencesOnExit();
        SettingsTabController.updatePreferencesOnExit();
        SplitMergeTabController.updatePreferencesOnExit(); // NOTE: This shit above should be re-written to similar pattern
        RcmTabController.updatePreferencesOnExit();
        NXDTabController.updatePreferencesOnExit();

        saveLastOpenedTab();
    }

    private void openLastOpenedTab(){
        String tabId = AppPreferences.getInstance().getLastOpenedTab();
        switch (tabId){
            case "GamesTabHolder":
                mainTabPane.getSelectionModel().select(GamesTabHolder);
                break;
            case "RCMTabHolder":
                mainTabPane.getSelectionModel().select(RCMTabHolder);
                break;
            case "SMTabHolder":
                mainTabPane.getSelectionModel().select(SMTabHolder);
                break;
        }
    }
    private void saveLastOpenedTab(){
        String tabId = mainTabPane.getSelectionModel().getSelectedItem().getId();
        if (tabId == null || tabId.isEmpty())
            tabId = "";
        AppPreferences.getInstance().setLastOpenedTab(tabId);
    }
}
