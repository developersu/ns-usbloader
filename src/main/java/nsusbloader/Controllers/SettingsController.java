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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import nsusbloader.AppPreferences;
import nsusbloader.ServiceWindow;
import nsusbloader.ModelControllers.UpdatesChecker;
import nsusbloader.UI.LocaleHolder;
import nsusbloader.UI.SettingsLanguagesSetup;
import nsusbloader.Utilities.WindowsDrivers.DriversInstall;

import java.net.URL;
import java.util.*;

public class SettingsController implements Initializable {
    @FXML
    private CheckBox nspFilesFilterForGLCB,
            autoCheckUpdCb;

    @FXML
    private Hyperlink newVersionLink;

    @FXML
    private Button langBtn,
            checkForUpdBtn,
            drvInstBtn;
    @FXML
    private ChoiceBox<LocaleHolder> langCB;

    @FXML
    private ChoiceBox<String> glVersionChoiceBox;

    @FXML
    private SettingsBlockTinfoilController settingsBlockTinfoilController;

    private HostServices hostServices;

    public static final String[] glSupportedVersions = {"v0.5", "v0.7.x", "v0.8"};

    private ResourceBundle resourceBundle;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
        final AppPreferences preferences = AppPreferences.getInstance();

        nspFilesFilterForGLCB.setSelected(preferences.getNspFileFilterGL());



        newVersionLink.setVisible(false);
        newVersionLink.setOnAction(e-> hostServices.showDocument(newVersionLink.getText()));

        autoCheckUpdCb.setSelected(preferences.getAutoCheckUpdates());

        Region btnSwitchImage = new Region();
        btnSwitchImage.getStyleClass().add("regionUpdatesCheck");
        checkForUpdBtn.setGraphic(btnSwitchImage);

        checkForUpdBtn.setOnAction(e->checkForUpdatesAction());

        setDriversInstallFeature();

        SettingsLanguagesSetup settingsLanguagesSetup = new SettingsLanguagesSetup();
        langCB.setItems(settingsLanguagesSetup.getLanguages());
        langCB.getSelectionModel().select(settingsLanguagesSetup.getRecentLanguage());

        configureLanguageButton();

        // Set supported old versions
        glVersionChoiceBox.getItems().addAll(glSupportedVersions);
        String oldVer = preferences.getGlVersion();  // Overhead; Too much validation of consistency
        glVersionChoiceBox.getSelectionModel().select(oldVer);
    }

    private void checkForUpdatesAction(){
        Task<List<String>> updTask = new UpdatesChecker();
        updTask.setOnSucceeded(event->{
            List<String> result = updTask.getValue();

            if (result == null){
                ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionUnknown"),
                        resourceBundle.getString("windowBodyNewVersionUnknown"));
                return;
            }

            if (result.get(0).isEmpty()){
                ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionNOTAval"),
                        resourceBundle.getString("windowBodyNewVersionNOTAval"));
                return;
            }

            setNewVersionLink(result.get(0));
            ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionAval"),
                    resourceBundle.getString("windowTitleNewVersionAval")+": "+result.get(0) + "\n\n" + result.get(1));
        });
        Thread updates = new Thread(updTask);
        updates.setDaemon(true);
        updates.start();
    }

    private void setDriversInstallFeature(){
        if (isWindows()){
            Region btnDrvImage = new Region();
            btnDrvImage.getStyleClass().add("regionWindows");
            drvInstBtn.setGraphic(btnDrvImage);
            drvInstBtn.setVisible(true);
            drvInstBtn.setOnAction(actionEvent -> new DriversInstall(resourceBundle));
        }
    }
    private boolean isWindows(){
        return System.getProperty("os.name").toLowerCase().replace(" ", "").contains("windows");
    }

    private void configureLanguageButton(){
        langBtn.setOnAction(e->languageButtonAction());
    }
    private void languageButtonAction(){
        LocaleHolder localeHolder = langCB.getSelectionModel().getSelectedItem();
        AppPreferences.getInstance().setLocale(localeHolder.getLocaleCode());
        Locale newLocale = localeHolder.getLocale();
        ServiceWindow.getInfoNotification("",
                ResourceBundle.getBundle("locale", newLocale).getString("windowBodyRestartToApplyLang"));
    }

    public boolean getNSPFileFilterForGL(){return nspFilesFilterForGLCB.isSelected(); }

    public boolean getAutoCheckForUpdates(){ return autoCheckUpdCb.isSelected(); }

    public void registerHostServices(HostServices hostServices){this.hostServices = hostServices;}

    public void setNewVersionLink(String newVer){
        newVersionLink.setVisible(true);
        newVersionLink.setText("https://github.com/developersu/ns-usbloader/releases/tag/"+newVer);
    }

    public String getGlVer() {
        return glVersionChoiceBox.getValue();
    }

    public SettingsBlockTinfoilController getTinfoilSettings(){ return settingsBlockTinfoilController; }

    public void updatePreferencesOnExit(){
        AppPreferences preferences = AppPreferences.getInstance();

        preferences.setAutoCheckUpdates(getAutoCheckForUpdates());
        preferences.setNspFileFilterGL(getNSPFileFilterForGL());
        preferences.setGlVersion(getGlVer());

        settingsBlockTinfoilController.updatePreferencesOnExit();
    }
}