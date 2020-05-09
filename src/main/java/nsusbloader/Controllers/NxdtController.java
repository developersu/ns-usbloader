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

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.Utilities.NxdtTask;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class NxdtController implements Initializable {
    @FXML
    private Label saveToLocationLbl, statusLbl;

    @FXML
    private Button injectPldBtn;

    private ResourceBundle rb;

    private Region btnDumpStopImage;

    private Task<Boolean> NxdtTask;
    private Thread workThread;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.rb = resourceBundle;

        File saveToValidator = new File(AppPreferences.getInstance().getNXDTSaveToLocation());
        if (saveToValidator.exists())
            saveToLocationLbl.setText(saveToValidator.getAbsolutePath());
        else
            saveToLocationLbl.setText(System.getProperty("user.home"));

        btnDumpStopImage = new Region();
        btnDumpStopImage.getStyleClass().add("regionDump");

        injectPldBtn.getStyleClass().add("buttonUp");
        injectPldBtn.setGraphic(btnDumpStopImage);

        injectPldBtn.setOnAction(event -> startDumpProcess());
    }

    @FXML
    private void bntSelectSaveTo(){
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(rb.getString("tabSplMrg_Btn_SelectFolder"));
        dc.setInitialDirectory(new File(saveToLocationLbl.getText()));
        File saveToDir = dc.showDialog(saveToLocationLbl.getScene().getWindow());
        if (saveToDir != null)
            saveToLocationLbl.setText(saveToDir.getAbsolutePath());
    }
    /**
     * Start reading commands from NXDT button handler
     * */
    private void startDumpProcess(){
        if ((workThread == null || ! workThread.isAlive())){
            MediatorControl.getInstance().getContoller().logArea.clear();

            NxdtTask = new NxdtTask(saveToLocationLbl.getText());
            NxdtTask.setOnSucceeded(event -> {
                if (NxdtTask.getValue())
                    statusLbl.setText(rb.getString("done_txt"));
                else
                    statusLbl.setText(rb.getString("failure_txt"));
            });

            workThread = new Thread(NxdtTask);
            workThread.setDaemon(true);
            workThread.start();
        }
    }

    /**
     * Interrupt thread NXDT button handler
     * */
    private void stopBtnAction(){
        if (workThread != null && workThread.isAlive()){
            NxdtTask.cancel(false);
        }
    }

    public void notifyThreadStarted(boolean isActive, EModule type){
        if (! type.equals(EModule.NXDT)){
            injectPldBtn.setDisable(isActive);
            return;
        }

        if (isActive) {
            btnDumpStopImage.getStyleClass().clear();
            btnDumpStopImage.getStyleClass().add("regionStop");

            injectPldBtn.setOnAction(e-> stopBtnAction());
            injectPldBtn.setText(rb.getString("btn_Stop"));
            injectPldBtn.getStyleClass().remove("buttonUp");
            injectPldBtn.getStyleClass().add("buttonStop");
            return;
        }
        btnDumpStopImage.getStyleClass().clear();
        btnDumpStopImage.getStyleClass().add("regionDump");

        injectPldBtn.setOnAction(e-> startDumpProcess());
        injectPldBtn.setText(rb.getString("tabNXDT_Btn_Start"));
        injectPldBtn.getStyleClass().remove("buttonStop");
        injectPldBtn.getStyleClass().add("buttonUp");
    }
    /**
     * Save application settings on exit
     * */
    public void updatePreferencesOnExit(){
        AppPreferences.getInstance().setNXDTSaveToLocation(saveToLocationLbl.getText());
    }
}
