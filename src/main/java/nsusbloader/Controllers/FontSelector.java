/*
    Copyright 2019-2023 Dmitry Isaenko
     
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

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import nsusbloader.AppPreferences;

import java.util.ResourceBundle;

public class FontSelector {
    public FontSelector(ResourceBundle resourceBundle) throws Exception{
        Stage stage = new Stage();
        stage.setMinWidth(800);
        stage.setMinHeight(800);

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/FontSettings.fxml"));
        fxmlLoader.setResources(resourceBundle);

        stage.setTitle(resourceBundle.getString("tab2_Btn_ApplicationFont"));
        stage.getIcons().addAll(
                new Image("/res/app_icon32x32.png"),
                new Image("/res/app_icon48x48.png"),
                new Image("/res/app_icon64x64.png"),
                new Image("/res/app_icon128x128.png"));

        Parent parent = fxmlLoader.load();
        Scene fontScene = new Scene(parent, 550, 600);

        fontScene.getStylesheets().add(AppPreferences.getInstance().getTheme());
        parent.setStyle(AppPreferences.getInstance().getFontStyle());

        stage.setAlwaysOnTop(true);
        stage.setScene(fontScene);
        stage.show();
    }
}
