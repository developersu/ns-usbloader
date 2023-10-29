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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;

import java.io.File;
import java.util.List;
import java.util.ResourceBundle;

public class FilesDropHandle {

    public FilesDropHandle(List<File> files, String filesRegex, String foldersRegex){
        FilesDropHandleTask filesDropHandleTask = new FilesDropHandleTask(files, filesRegex, foldersRegex);

        ResourceBundle resourceBundle = MediatorControl.getInstance().getResourceBundle();
        Button cancelButton = new Button(resourceBundle.getString("btn_Cancel"));

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setTextAlignment(TextAlignment.CENTER);
        statusLabel.textProperty().bind(filesDropHandleTask.messageProperty());

        Pane fillerPane1 = new Pane();
        Pane fillerPane2 = new Pane();

        VBox parentVBox = new VBox();
        parentVBox.setAlignment(Pos.TOP_CENTER);
        parentVBox.setFillWidth(true);
        parentVBox.setSpacing(5.0);
        parentVBox.setPadding(new Insets(5.0));
        parentVBox.setFillWidth(true);
        parentVBox.getChildren().addAll(
                statusLabel,
                fillerPane1,
                progressIndicator,
                fillerPane2,
                cancelButton
        );

        VBox.setVgrow(fillerPane1, Priority.ALWAYS);
        VBox.setVgrow(fillerPane2, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle(resourceBundle.getString("windowTitleAddingFiles"));
        stage.getIcons().addAll(
                new Image("/res/info_ico32x32.png"),
                new Image("/res/info_ico48x48.png"),
                new Image("/res/info_ico64x64.png"),
                new Image("/res/info_ico128x128.png")
        );
        stage.setMinWidth(300);
        stage.setMinHeight(175);
        stage.setAlwaysOnTop(true);
        Scene mainScene = new Scene(parentVBox, 310, 185);

        mainScene.getStylesheets().add(AppPreferences.getInstance().getTheme());
        parentVBox.setStyle(AppPreferences.getInstance().getFontStyle());

        stage.setOnHidden(windowEvent -> filesDropHandleTask.cancel(true ) );

        stage.setScene(mainScene);
        stage.show();
        stage.toFront();

        filesDropHandleTask.setOnSucceeded(event -> {
            cancelButton.setText(resourceBundle.getString("btn_Close"));

            List<File> allFiles = filesDropHandleTask.getValue();

            if (! allFiles.isEmpty()) {
                MediatorControl.getInstance().getGamesController().tableFilesListController.setFiles(allFiles);
            }
            stage.close();
        });

        new Thread(filesDropHandleTask).start();

        cancelButton.setOnAction(actionEvent -> {
            filesDropHandleTask.cancel(true);
            stage.close();
        });
    }
}
