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

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.Optional;

public class ServiceWindow   {
    /** Create window with error notification */
    public static void getErrorNotification(String title, String body){
        getNotification(title, body, Alert.AlertType.ERROR);
    }
    /** Create window with information notification */
    public static void getInfoNotification(String title, String body){
        getNotification(title, body, Alert.AlertType.INFORMATION);
    }
    /** Real window creator */
    private static void getNotification(String title, String body, Alert.AlertType type){
        Alert alertBox = new Alert(type);
        alertBox.setTitle(title);
        alertBox.setHeaderText(null);
        alertBox.setContentText(body);
        alertBox.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        alertBox.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alertBox.setResizable(true);        // Java bug workaround for JDR11/OpenJFX. TODO: nothing. really.

        Stage dialogStage = (Stage) alertBox.getDialogPane().getScene().getWindow();
        dialogStage.setAlwaysOnTop(true);
        dialogStage.getIcons().addAll(
                new Image("/res/warn_ico32x32.png"),
                new Image("/res/warn_ico48x48.png"),
                new Image("/res/warn_ico64x64.png"),
                new Image("/res/warn_ico128x128.png")
        );
        alertBox.getDialogPane().getStylesheets().add(AppPreferences.getInstance().getTheme());
        dialogStage.getScene().getRoot().setStyle(AppPreferences.getInstance().getFontStyle());

        alertBox.show();
        dialogStage.toFront();
    }
    /**
     * Create notification window with confirm/deny
     * */
    public static boolean getConfirmationWindow(String title, String body){
        Alert alertBox = new Alert(Alert.AlertType.CONFIRMATION);
        alertBox.setTitle(title);
        alertBox.setHeaderText(null);
        alertBox.setContentText(body);
        alertBox.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        alertBox.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alertBox.setResizable(true);        // Java bug workaround for JDR11/OpenJFX. TODO: nothing. really.

        Stage dialogStage = (Stage) alertBox.getDialogPane().getScene().getWindow();
        dialogStage.setAlwaysOnTop(true);
        dialogStage.getIcons().addAll(
                new Image("/res/ask_ico32x32.png"),
                new Image("/res/ask_ico48x48.png"),
                new Image("/res/ask_ico64x64.png"),
                new Image("/res/ask_ico128x128.png")
        );

        alertBox.getDialogPane().getStylesheets().add(AppPreferences.getInstance().getTheme());
        dialogStage.getScene().getRoot().setStyle(AppPreferences.getInstance().getFontStyle());

        dialogStage.toFront();

        Optional<ButtonType> result = alertBox.showAndWait();

        return (result.isPresent() && result.get() == ButtonType.OK);
    }
}
