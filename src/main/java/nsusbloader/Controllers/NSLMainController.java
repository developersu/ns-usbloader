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
package nsusbloader.Controllers;

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
    private TextArea logArea;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab GamesTabHolder, RCMTabHolder, SMTabHolder, PatchesTabHolder;

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
    @FXML
    private PatchesController PatchesTabController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.resourceBundle = rb;
        logArea.setText(rb.getString("tab3_Txt_GreetingsMessage")+" "+ NSLMain.appVersion+"!\n");
        if (System.getProperty("os.name").toLowerCase().startsWith("lin"))
            if (!System.getProperty("user.name").equals("root"))
                logArea.appendText(rb.getString("tab3_Txt_EnteredAsMsg1")+System.getProperty("user.name")+"\n"+rb.getString("tab3_Txt_EnteredAsMsg2") + "\n");

        logArea.appendText(rb.getString("tab3_Txt_GreetingsMessage2")+"\n");

        AppPreferences preferences = AppPreferences.getInstance();

        if (preferences.getAutoCheckUpdates())
            checkForUpdates();

        if (preferences.getPatchesTabInvisible())
            mainTabPane.getTabs().remove(3);

        openLastOpenedTab();

        TransfersPublisher transfersPublisher = new TransfersPublisher(
                GamesTabController,
                SplitMergeTabController,
                RcmTabController,
                NXDTabController,
                PatchesTabController);

        MediatorControl.INSTANCE.configure(
                resourceBundle,
                SettingsTabController,
                logArea,
                progressBar,
                GamesTabController,
                transfersPublisher);

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
     * Save preferences before exit
     * */
    public void exit(){
        GamesTabController.updatePreferencesOnExit();
        SettingsTabController.updatePreferencesOnExit();
        SplitMergeTabController.updatePreferencesOnExit(); // NOTE: This shit above should be re-written to similar pattern
        RcmTabController.updatePreferencesOnExit();
        NXDTabController.updatePreferencesOnExit();
        PatchesTabController.updatePreferencesOnExit();
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
            case "PatchesTabHolder":
                mainTabPane.getSelectionModel().select(PatchesTabHolder);
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
