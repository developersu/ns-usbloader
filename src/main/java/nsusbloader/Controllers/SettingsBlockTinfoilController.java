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

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import nsusbloader.AppPreferences;
import nsusbloader.ServiceWindow;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsBlockTinfoilController implements Initializable {
    @FXML
    private CheckBox xciNszXczSupportCB,
            validateNSHostNameCB,
            networkExpertModeCB,
            autoDetectIpCB,
            randomlySelectPortCB,
            noRequestsServeCB;

    @FXML
    private VBox networkExpertSettingsVBox;

    @FXML
    private TextField pcIpTF,
            pcPortTF,
            pcExtraTF;

    private ResourceBundle resourceBundle;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;

        final AppPreferences preferences = AppPreferences.getInstance();

        networkExpertSettingsVBox.disableProperty().bind(networkExpertModeCB.selectedProperty().not());

        pcIpTF.disableProperty().bind(autoDetectIpCB.selectedProperty());
        pcPortTF.disableProperty().bind(randomlySelectPortCB.selectedProperty());
        pcExtraTF.disableProperty().bind(noRequestsServeCB.selectedProperty().not());

        xciNszXczSupportCB.setSelected(preferences.getTfXCI());
        validateNSHostNameCB.setSelected(preferences.getNsIpValidationNeeded());
        networkExpertModeCB.setSelected(preferences.getExpertMode());
        pcIpTF.setText(preferences.getHostIp());
        pcPortTF.setText(preferences.getHostPort());
        pcExtraTF.setText(preferences.getHostExtra());
        autoDetectIpCB.setSelected(preferences.getAutoDetectIp());
        randomlySelectPortCB.setSelected(preferences.getRandPort());
        boolean noServeRequestsFlag = preferences.getNotServeRequests();
        if (noServeRequestsFlag){
            noServeRequestAction(true);
        }
        noRequestsServeCB.setSelected(noServeRequestsFlag);

        pcIpTF.setTextFormatter(buildSpacelessTextFormatter());
        pcPortTF.setTextFormatter(buildPortTextFormatter());
        pcExtraTF.setTextFormatter(buildSpacelessTextFormatter());

        autoDetectIpCB.setOnAction(e->pcIpTF.requestFocus());
        randomlySelectPortCB.setOnAction(e->pcPortTF.requestFocus());
        noRequestsServeCB.selectedProperty().addListener(((observableValue, oldValue, newValue) -> noServeRequestAction(newValue)));
    }

    private TextFormatter buildSpacelessTextFormatter(){
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();

            if (text.contains(" ") || text.contains("\t")){
                return null;
            }
            return change;
        });
    }

    private TextFormatter buildPortTextFormatter(){
        final String PORT_NUMBER_PATTERN = "^[0-9]{0,5}$";

        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            if (text.isEmpty()) {
                return change;
            }

            if (! text.matches(PORT_NUMBER_PATTERN)) {
                return null;
            }

            int newPortNumber = Integer.parseInt(text);

            if (newPortNumber > 65535 || newPortNumber == 0) {
                ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleErrorPort"),
                        resourceBundle.getString("windowBodyErrorPort"));
                return null;
            }

            return change;
        });
    }

    private void noServeRequestAction(boolean isNoServe){
        if (isNoServe){
            autoDetectIpCB.setDisable(true);
            autoDetectIpCB.setSelected(false);
            randomlySelectPortCB.setDisable(true);
            randomlySelectPortCB.setSelected(false);
        }
        else {
            autoDetectIpCB.setDisable(false);
            autoDetectIpCB.setSelected(true);
            randomlySelectPortCB.setDisable(false);
            randomlySelectPortCB.setSelected(true);
        }
    }

    public String getHostIp(){ return pcIpTF.getText(); }
    public String getHostPort(){ return pcPortTF.getText(); }
    public String getHostExtra(){ return pcExtraTF.getText(); }
    public boolean isXciNszXczSupport(){ return xciNszXczSupportCB.isSelected(); }
    public boolean isExpertModeSelected(){ return networkExpertModeCB.isSelected(); }
    public boolean isAutoDetectIp(){ return autoDetectIpCB.isSelected(); }
    public boolean isRandomlySelectPort(){ return randomlySelectPortCB.isSelected(); }
    public boolean isNoRequestsServe(){ return noRequestsServeCB.isSelected(); }
    public boolean isValidateNSHostName(){ return validateNSHostNameCB.isSelected(); }

    void updatePreferencesOnExit(){
        AppPreferences preferences = AppPreferences.getInstance();

        preferences.setNsIpValidationNeeded(isValidateNSHostName());
        preferences.setExpertMode(isExpertModeSelected());
        preferences.setAutoDetectIp(isAutoDetectIp());
        preferences.setRandPort(isRandomlySelectPort());
        preferences.setNotServeRequests(isNoRequestsServe());
        preferences.setHostIp(getHostIp());
        preferences.setHostPort(getHostPort());
        preferences.setHostExtra(getHostExtra());
        preferences.setTfXCI(isXciNszXczSupport());
    }
}
