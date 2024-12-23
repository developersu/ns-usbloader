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

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.Region;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;
import nsusbloader.ModelControllers.UpdatesChecker;
import nsusbloader.ServiceWindow;
import nsusbloader.UI.LocaleHolder;
import nsusbloader.UI.SettingsLanguagesSetup;
import nsusbloader.Utilities.WindowsDrivers.DriversInstall;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class SettingsBlockGenericController implements Initializable {
    @FXML
    private ChoiceBox<LocaleHolder> languagesChB;
    @FXML
    private Button fontSelectBtn;

    @FXML
    private Button submitLanguageBtn,
            driversInstallBtn,
            checkForUpdBtn;
    @FXML
    private CheckBox autoCheckForUpdatesCB,
            direcroriesChooserForRomsCB;
    @FXML
    private Hyperlink newVersionHyperlink;
    private ResourceBundle resourceBundle;
    private HostServices hostServices;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
        final AppPreferences preferences = AppPreferences.getInstance();

        autoCheckForUpdatesCB.setSelected(preferences.getAutoCheckUpdates());
        direcroriesChooserForRomsCB.setSelected(preferences.getDirectoriesChooserForRoms());
        direcroriesChooserForRomsCB.setOnAction(actionEvent ->
                MediatorControl.INSTANCE.getGamesController().setFilesSelectorButtonBehaviour(direcroriesChooserForRomsCB.isSelected())
        );

        Region btnSwitchImage = new Region();
        btnSwitchImage.getStyleClass().add("regionUpdatesCheck");
        checkForUpdBtn.setGraphic(btnSwitchImage);

        setDriversInstallFeature();

        SettingsLanguagesSetup settingsLanguagesSetup = new SettingsLanguagesSetup();
        languagesChB.setItems(settingsLanguagesSetup.getLanguages());
        languagesChB.getSelectionModel().select(settingsLanguagesSetup.getRecentLanguage());

        hostServices = MediatorControl.INSTANCE.getHostServices();
        newVersionHyperlink.setOnAction(e-> hostServices.showDocument(newVersionHyperlink.getText()));
        checkForUpdBtn.setOnAction(e->checkForUpdatesAction());
        submitLanguageBtn.setOnAction(e->languageButtonAction());
        fontSelectBtn.setOnAction(e -> openFontSettings());
    }
    private void openFontSettings() {
        try {
            new FontSettings(resourceBundle);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void setDriversInstallFeature(){
        if (isWindows()){
            Region btnDrvImage = new Region();
            btnDrvImage.getStyleClass().add("regionWindows");
            driversInstallBtn.setGraphic(btnDrvImage);
            driversInstallBtn.setVisible(true);
            driversInstallBtn.setOnAction(actionEvent -> new DriversInstall(resourceBundle));
        }
    }
    private boolean isWindows(){
        return System.getProperty("os.name").toLowerCase().replace(" ", "").contains("windows");
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

    private void languageButtonAction(){
        LocaleHolder localeHolder = languagesChB.getSelectionModel().getSelectedItem();
        AppPreferences.getInstance().setLocale(localeHolder.getLocaleCode());
        Locale newLocale = localeHolder.getLocale();
        ServiceWindow.getInfoNotification("",
                ResourceBundle.getBundle("locale", newLocale).getString("windowBodyRestartToApplyLang"));
    }

    private boolean getAutoCheckForUpdates(){
        return autoCheckForUpdatesCB.isSelected();
    }

    public boolean isDirectoriesChooserForRoms(){
        return direcroriesChooserForRomsCB.isSelected();
    }

    void setNewVersionLink(String newVer){
        newVersionHyperlink.setVisible(true);
        newVersionHyperlink.setText("https://github.com/developersu/ns-usbloader/releases/tag/"+newVer);
    }

    void updatePreferencesOnExit() {
        AppPreferences.getInstance().setAutoCheckUpdates(getAutoCheckForUpdates());
        AppPreferences.getInstance().setDirectoriesChooserForRoms(isDirectoriesChooserForRoms());
    }
}
