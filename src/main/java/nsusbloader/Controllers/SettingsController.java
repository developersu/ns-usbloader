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
            validateNSHostNameCb,
            expertModeCb,
            autoDetectIpCb,
            randPortCb,
            dontServeCb,
            autoCheckUpdCb,
            tfXciSpprtCb;

    @FXML
    private TextField pcIpTextField,
            pcPortTextField,
            pcExtraTextField;

    @FXML
    private VBox expertSettingsVBox;

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

    private HostServices hostServices;

    public static final String[] glSupportedVersions = {"v0.5", "v0.7.x", "v0.8"};

    private ResourceBundle resourceBundle;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
        final AppPreferences preferences = AppPreferences.getInstance();

        nspFilesFilterForGLCB.setSelected(preferences.getNspFileFilterGL());
        validateNSHostNameCb.setSelected(preferences.getNsIpValidationNeeded());
        expertSettingsVBox.setDisable(! preferences.getExpertMode());
        expertModeCb.setSelected(preferences.getExpertMode());
        expertModeCb.setOnAction(e-> expertSettingsVBox.setDisable(! expertModeCb.isSelected()));

        autoDetectIpCb.setSelected(preferences.getAutoDetectIp());
        pcIpTextField.setDisable(preferences.getAutoDetectIp());
        autoDetectIpCb.setOnAction(e->{
            pcIpTextField.setDisable(autoDetectIpCb.isSelected());
            if (! autoDetectIpCb.isSelected())
                pcIpTextField.requestFocus();
        });

        randPortCb.setSelected(preferences.getRandPort());
        pcPortTextField.setDisable(preferences.getRandPort());
        randPortCb.setOnAction(e->{
            pcPortTextField.setDisable(randPortCb.isSelected());
            if (! randPortCb.isSelected())
                pcPortTextField.requestFocus();
        });

        if (preferences.getNotServeRequests()){
            dontServeCb.setSelected(true);

            autoDetectIpCb.setSelected(false);
            autoDetectIpCb.setDisable(true);
            pcIpTextField.setDisable(false);

            randPortCb.setSelected(false);
            randPortCb.setDisable(true);
            pcPortTextField.setDisable(false);
        }
        pcExtraTextField.setDisable(! preferences.getNotServeRequests());

        dontServeCb.setOnAction(e->{
            if (dontServeCb.isSelected()){
                autoDetectIpCb.setSelected(false);
                autoDetectIpCb.setDisable(true);
                pcIpTextField.setDisable(false);

                randPortCb.setSelected(false);
                randPortCb.setDisable(true);
                pcPortTextField.setDisable(false);

                pcExtraTextField.setDisable(false);
                pcIpTextField.requestFocus();
            }
            else {
                autoDetectIpCb.setDisable(false);
                autoDetectIpCb.setSelected(true);
                pcIpTextField.setDisable(true);

                randPortCb.setDisable(false);
                randPortCb.setSelected(true);
                pcPortTextField.setDisable(true);

                pcExtraTextField.setDisable(true);
            }
        });

        pcIpTextField.setText(preferences.getHostIp());
        pcPortTextField.setText(preferences.getHostPort());
        pcExtraTextField.setText(preferences.getHostExtra());

        pcIpTextField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().contains(" ") | change.getControlNewText().contains("\t"))
                return null;
            else
                return change;
        }));
        pcPortTextField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().matches("^[0-9]{0,5}$")) {
                if (!change.getControlNewText().isEmpty()
                        && ((Integer.parseInt(change.getControlNewText()) > 65535) || (Integer.parseInt(change.getControlNewText()) == 0))
                ) {
                    ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleErrorPort"), resourceBundle.getString("windowBodyErrorPort"));
                    return null;
                }
                return change;
            }
            else
                return null;
        }));
        pcExtraTextField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().contains(" ") | change.getControlNewText().contains("\t"))
                return null;
            else
                return change;
        }));

        newVersionLink.setVisible(false);
        newVersionLink.setOnAction(e-> hostServices.showDocument(newVersionLink.getText()));

        autoCheckUpdCb.setSelected(preferences.getAutoCheckUpdates());

        Region btnSwitchImage = new Region();
        btnSwitchImage.getStyleClass().add("regionUpdatesCheck");
        checkForUpdBtn.setGraphic(btnSwitchImage);

        checkForUpdBtn.setOnAction(e->checkForUpdatesAction());

        setDriversInstallFeature();

        tfXciSpprtCb.setSelected(preferences.getTfXCI());

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
    public boolean getExpertModeSelected(){ return expertModeCb.isSelected(); }
    public boolean getAutoIpSelected(){ return autoDetectIpCb.isSelected(); }
    public boolean getRandPortSelected(){ return randPortCb.isSelected(); }
    public boolean getNotServeSelected(){ return dontServeCb.isSelected(); }

    public boolean isNsIpValidate(){ return validateNSHostNameCb.isSelected(); }

    public String getHostIp(){ return pcIpTextField.getText(); }
    public String getHostPort(){ return pcPortTextField.getText(); }
    public String getHostExtra(){ return pcExtraTextField.getText(); }
    public boolean getAutoCheckForUpdates(){ return autoCheckUpdCb.isSelected(); }
    public boolean getTfXciNszXczSupport(){ return tfXciSpprtCb.isSelected(); }           // Used also for NSZ/XCZ

    public void registerHostServices(HostServices hostServices){this.hostServices = hostServices;}

    public void setNewVersionLink(String newVer){
        newVersionLink.setVisible(true);
        newVersionLink.setText("https://github.com/developersu/ns-usbloader/releases/tag/"+newVer);
    }

    public String getGlVer() {
        return glVersionChoiceBox.getValue();
    }
    
    public void updatePreferencesOnExit(){
        AppPreferences preferences = AppPreferences.getInstance();

        preferences.setNsIpValidationNeeded(isNsIpValidate());
        preferences.setExpertMode(getExpertModeSelected());
        preferences.setAutoDetectIp(getAutoIpSelected());
        preferences.setRandPort(getRandPortSelected());
        preferences.setNotServeRequests(getNotServeSelected());
        preferences.setHostIp(getHostIp());
        preferences.setHostPort(getHostPort());
        preferences.setHostExtra(getHostExtra());
        preferences.setAutoCheckUpdates(getAutoCheckForUpdates());
        preferences.setTfXCI(getTfXciNszXczSupport());
        preferences.setNspFileFilterGL(getNSPFileFilterForGL());
        preferences.setGlVersion(getGlVer());
    }
}