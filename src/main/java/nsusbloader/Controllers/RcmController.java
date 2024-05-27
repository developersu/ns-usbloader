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

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.ServiceWindow;
import nsusbloader.Utilities.Rcm;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class RcmController implements Initializable, ISubscriber {
    @FXML
    private ToggleGroup rcmToggleGrp;

    @FXML
    private VBox rcmToolPane;

    @FXML
    private RadioButton pldrRadio1,
            pldrRadio2,
            pldrRadio3,
            pldrRadio4,
            pldrRadio5;

    @FXML
    private Button injectPldBtn;

    @FXML
    private Label payloadFNameLbl1, payloadFPathLbl1,
        payloadFNameLbl2, payloadFPathLbl2,
        payloadFNameLbl3, payloadFPathLbl3,
        payloadFNameLbl4, payloadFPathLbl4,
        payloadFNameLbl5, payloadFPathLbl5;

    @FXML
    private Label statusLbl;

    private AppPreferences preferences;
    private ResourceBundle rb;
    private String myRegexp;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.rb = resourceBundle;
        this.preferences = AppPreferences.getInstance();

        rcmToggleGrp.selectToggle(pldrRadio1);
        pldrRadio1.setOnAction(e -> statusLbl.setText(""));
        pldrRadio2.setOnAction(e -> statusLbl.setText(""));
        pldrRadio3.setOnAction(e -> statusLbl.setText(""));
        pldrRadio4.setOnAction(e -> statusLbl.setText(""));
        pldrRadio5.setOnAction(e -> statusLbl.setText(""));

        String recentRcm1 = preferences.getRecentRcm(1);
        String recentRcm2 = preferences.getRecentRcm(2);
        String recentRcm3 = preferences.getRecentRcm(3);
        String recentRcm4 = preferences.getRecentRcm(4);
        String recentRcm5 = preferences.getRecentRcm(5);
        
        if (File.separator.equals("/"))
            this.myRegexp = "^.+/";
        else
            this.myRegexp = "^.+\\\\";

        if (! recentRcm1.isEmpty()) {
            payloadFNameLbl1.setText(recentRcm1.replaceAll(myRegexp, ""));
            payloadFPathLbl1.setText(recentRcm1);
        }
        if (! recentRcm2.isEmpty()) {
            payloadFNameLbl2.setText(recentRcm2.replaceAll(myRegexp, ""));
            payloadFPathLbl2.setText(recentRcm2);
        }
        if (! recentRcm3.isEmpty()) {
            payloadFNameLbl3.setText(recentRcm3.replaceAll(myRegexp, ""));
            payloadFPathLbl3.setText(recentRcm3);
        }
        if (! recentRcm4.isEmpty()) {
            payloadFNameLbl4.setText(recentRcm4.replaceAll(myRegexp, ""));
            payloadFPathLbl4.setText(recentRcm4);
        }
        if (! recentRcm5.isEmpty()) {
            payloadFNameLbl5.setText(recentRcm5.replaceAll(myRegexp, ""));
            payloadFPathLbl5.setText(recentRcm5);
        }

        injectPldBtn.setOnAction(actionEvent -> smash());
    }

    /**
     * Drag-n-drop support (dragOver consumer)
     * */
    @FXML
    private void handleDragOver(DragEvent event){
        if (event.getDragboard().hasFiles())
            event.acceptTransferModes(TransferMode.ANY);
        event.consume();
    }
    /**
     * Drag-n-drop support (drop consumer)
     * */
    @FXML
    private void handleDrop(DragEvent event){
        Node sourceNode = (Node) event.getSource();
        File fileDrpd = event.getDragboard().getFiles().get(0);

        if (fileDrpd.isDirectory()){
            event.setDropCompleted(true);
            event.consume();
            return;
        }

        String fileNameDrpd = fileDrpd.getAbsolutePath();

        switch (sourceNode.getId()){
            case "plHbox1":
                setPayloadFile( 1, fileNameDrpd);
                break;
            case "plHbox2":
                setPayloadFile( 2, fileNameDrpd);
                break;
            case "plHbox3":
                setPayloadFile( 3, fileNameDrpd);
                break;
            case "plHbox4":
                setPayloadFile( 4, fileNameDrpd);
                break;
            case "plHbox5":
                setPayloadFile( 5, fileNameDrpd);
        }
        event.setDropCompleted(true);
        event.consume();
    }

    private void setPayloadFile(int RcmBoxNo, String fileName){
        String fileNameShort = fileName.replaceAll(myRegexp, "");
        switch (RcmBoxNo){
            case 1:
                payloadFNameLbl1.setText(fileNameShort);
                payloadFPathLbl1.setText(fileName);
                rcmToggleGrp.selectToggle(pldrRadio1);
                break;
            case 2:
                payloadFNameLbl2.setText(fileNameShort);
                payloadFPathLbl2.setText(fileName);
                rcmToggleGrp.selectToggle(pldrRadio2);
                break;
            case 3:
                payloadFNameLbl3.setText(fileNameShort);
                payloadFPathLbl3.setText(fileName);
                rcmToggleGrp.selectToggle(pldrRadio3);
                break;
            case 4:
                payloadFNameLbl4.setText(fileNameShort);
                payloadFPathLbl4.setText(fileName);
                rcmToggleGrp.selectToggle(pldrRadio4);
                break;
            case 5:
                payloadFNameLbl5.setText(fileNameShort);
                payloadFPathLbl5.setText(fileName);
                rcmToggleGrp.selectToggle(pldrRadio5);
        }
    }

    private void smash(){
        if (MediatorControl.INSTANCE.getTransferActive()) {
            ServiceWindow.getErrorNotification(rb.getString("windowTitleError"),
                    rb.getString("windowBodyPleaseStopOtherProcessFirst"));
            return;
        }

        Rcm rcmTask;
        RadioButton selectedRadio = (RadioButton)rcmToggleGrp.getSelectedToggle();
        switch (selectedRadio.getId()){
            case "pldrRadio1":
                rcmTask = new Rcm(payloadFPathLbl1.getText());
                break;
            case "pldrRadio2":
                rcmTask = new Rcm(payloadFPathLbl2.getText());
                break;
            case "pldrRadio3":
                rcmTask = new Rcm(payloadFPathLbl3.getText());
                break;
            case "pldrRadio4":
                rcmTask = new Rcm(payloadFPathLbl4.getText());
                break;
            case "pldrRadio5":
                rcmTask = new Rcm(payloadFPathLbl5.getText());
                break;
            default:
                return;
        }

        Thread rcmThread = new Thread(rcmTask);

        rcmThread.setDaemon(true);
        rcmThread.start();
    }

    @FXML
    private void bntSelectPayloader(ActionEvent event){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(rb.getString("btn_Select"));

        File validator = new File(payloadFPathLbl1.getText()).getParentFile();
        if (validator != null && validator.exists())
            fileChooser.setInitialDirectory(validator);
        else
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("bin", "*.bin"),
                new FileChooser.ExtensionFilter("Any file", "*.*")
        );

        File payloadFile = fileChooser.showOpenDialog(payloadFPathLbl1.getScene().getWindow());

        if (payloadFile == null)
            return;

        final String fullFileName = payloadFile.getAbsolutePath();
        final Node btn = (Node)event.getSource();

        switch (btn.getId()){
            case "selPldBtn1":
                setPayloadFile(1, fullFileName);
                break;
            case "selPldBtn2":
                setPayloadFile(2, fullFileName);
                break;
            case "selPldBtn3":
                setPayloadFile(3, fullFileName);
                break;
            case "selPldBtn4":
                setPayloadFile(4, fullFileName);
                break;
            case "selPldBtn5":
                setPayloadFile(5, fullFileName);
        }
    }
    @FXML
    private void bntResetPayloader(ActionEvent event){
        final Node btn = (Node)event.getSource();

        statusLbl.setText("");

        switch (btn.getId()){
            case "resPldBtn1":
                payloadFNameLbl1.setText("");
                payloadFPathLbl1.setText("");
                break;
            case "resPldBtn2":
                payloadFNameLbl2.setText("");
                payloadFPathLbl2.setText("");
                break;
            case "resPldBtn3":
                payloadFNameLbl3.setText("");
                payloadFPathLbl3.setText("");
                break;
            case "resPldBtn4":
                payloadFNameLbl4.setText("");
                payloadFPathLbl4.setText("");
                break;
            case "resPldBtn5":
                payloadFNameLbl5.setText("");
                payloadFPathLbl5.setText("");
        }
    }

    @FXML
    public void selectPldrPane(MouseEvent mouseEvent) {
        final Node selectedPane = (Node)mouseEvent.getSource();

        switch (selectedPane.getId()){
            case "pldPane1":
                pldrRadio1.fire();
                break;
            case "pldPane2":
                pldrRadio2.fire();
                break;
            case "pldPane3":
                pldrRadio3.fire();
                break;
            case "pldPane4":
                pldrRadio4.fire();
                break;
            case "pldPane5":
                pldrRadio5.fire();
                break;
        }
    }

    @Override
    public void notify(EModule type, boolean isActive, Payload payload) {
        rcmToolPane.setDisable(isActive);
        if (type.equals(EModule.RCM))
            statusLbl.setText(payload.getMessage());
    }
    /**
     * Save application settings on exit
     * */
    public void updatePreferencesOnExit(){
        preferences.setRecentRcm(1, payloadFPathLbl1.getText());
        preferences.setRecentRcm(2, payloadFPathLbl2.getText());
        preferences.setRecentRcm(3, payloadFPathLbl3.getText());
        preferences.setRecentRcm(4, payloadFPathLbl4.getText());
        preferences.setRecentRcm(5, payloadFPathLbl5.getText());
    }
}
