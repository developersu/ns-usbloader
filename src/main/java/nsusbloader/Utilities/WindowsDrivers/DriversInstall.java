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
package nsusbloader.Utilities.WindowsDrivers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import nsusbloader.AppPreferences;

import java.util.ResourceBundle;

public class DriversInstall {

    private static volatile boolean isRunning;

    private Label runInstallerStatusLabel;

    public DriversInstall(ResourceBundle rb){

        if (DriversInstall.isRunning)
            return;

        DriversInstall.isRunning = true;

        DownloadDriversTask downloadTask = new DownloadDriversTask();

        Button cancelButton = new Button(rb.getString("btn_Cancel"));

        HBox hBoxInformation = new HBox();
        hBoxInformation.setAlignment(Pos.TOP_LEFT);
        hBoxInformation.getChildren().add(new Label(rb.getString("windowBodyDownloadDrivers")));

        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.progressProperty().bind(downloadTask.progressProperty());

        Label downloadStatusLabel = new Label();
        downloadStatusLabel.setWrapText(true);
        downloadStatusLabel.textProperty().bind(downloadTask.messageProperty());

        runInstallerStatusLabel = new Label();
        runInstallerStatusLabel.setWrapText(true);

        Pane fillerPane1 = new Pane();
        Pane fillerPane2 = new Pane();

        VBox parentVBox = new VBox();
        parentVBox.setAlignment(Pos.TOP_CENTER);
        parentVBox.setFillWidth(true);
        parentVBox.setSpacing(5.0);
        parentVBox.setPadding(new Insets(5.0));
        parentVBox.setFillWidth(true);
        parentVBox.getChildren().addAll(
                hBoxInformation,
                fillerPane1,
                downloadStatusLabel,
                runInstallerStatusLabel,
                fillerPane2,
                progressBar,
                cancelButton
        ); // TODO:FIX

        VBox.setVgrow(fillerPane1, Priority.ALWAYS);
        VBox.setVgrow(fillerPane2, Priority.ALWAYS);

        Stage stage = new Stage();

        stage.setTitle(rb.getString("windowTitleDownloadDrivers"));
        stage.getIcons().addAll(
                new Image("/res/dwnload_ico32x32.png"),    //TODO: REDRAW
                new Image("/res/dwnload_ico48x48.png"),
                new Image("/res/dwnload_ico64x64.png"),
                new Image("/res/dwnload_ico128x128.png")
        );
        stage.setMinWidth(400);
        stage.setMinHeight(150);

        Scene mainScene = new Scene(parentVBox, 405, 155);

        mainScene.getStylesheets().add(AppPreferences.getInstance().getTheme());
        parentVBox.setStyle(AppPreferences.getInstance().getFontStyle());

        stage.setOnHidden(windowEvent -> {
            downloadTask.cancel(true );
            DriversInstall.isRunning = false;
        });

        stage.setScene(mainScene);
        stage.show();
        stage.toFront();

        downloadTask.setOnSucceeded(event -> {
            cancelButton.setText(rb.getString("btn_Close"));

            String returnedValue = downloadTask.getValue();

            if (returnedValue == null)
                return;

            if (runInstaller(returnedValue))
                stage.close();
        });

        Thread downloadThread = new Thread(downloadTask);
        downloadThread.start();

        cancelButton.setOnAction(actionEvent -> {
            downloadTask.cancel(true );
            stage.close();
        });
    }

    private boolean runInstaller(String pathToFile) {
        try {
            Runtime.getRuntime().exec("cmd /c "+pathToFile);
            return true;
        }
        catch (Exception e){
            runInstallerStatusLabel.setText("Error: "+e);
            e.printStackTrace();
            return false;
        }
    }
}
