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
import javafx.scene.control.ChoiceBox;
import nsusbloader.AppPreferences;

import java.net.URL;
import java.util.ResourceBundle;

public class SettingsBlockGoldleafController implements Initializable {
    @FXML
    private CheckBox nspFilesFilterForGLCB;
    @FXML
    private ChoiceBox<String> glVersionChoiceBox;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        final AppPreferences preferences = AppPreferences.getInstance();

        nspFilesFilterForGLCB.setSelected(preferences.getNspFileFilterGL());
        glVersionChoiceBox.getItems().addAll(AppPreferences.GOLDLEAF_SUPPORTED_VERSIONS);

        glVersionChoiceBox.getSelectionModel().select(preferences.getGlVersion());
    }

    public boolean getNSPFileFilterForGL(){return nspFilesFilterForGLCB.isSelected(); }

    public String getGlVer() {
        return glVersionChoiceBox.getValue();
    }

    void updatePreferencesOnExit(){
        final AppPreferences preferences = AppPreferences.getInstance();

        preferences.setNspFileFilterGL(getNSPFileFilterForGL());
        preferences.setGlVersion(glVersionChoiceBox.getSelectionModel().getSelectedIndex());
    }
}
