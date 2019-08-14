package nsusbloader.Controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;

import java.net.URL;
import java.util.ResourceBundle;

public class FrontController implements Initializable {
    @FXML
    private Pane specialPane;

    @FXML
    private ChoiceBox<String> choiceProtocol, choiceNetUsb;
    @FXML
    private Label nsIpLbl;
    @FXML
    private TextField nsIpTextField;
    @FXML
    private Button switchThemeBtn;
    @FXML
    public NSTableViewController tableFilesListController;            // Accessible from Mediator

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        specialPane.getStyleClass().add("special-pane-as-border");  // UI hacks

        ObservableList<String> choiceProtocolList = FXCollections.observableArrayList("TinFoil", "GoldLeaf");
        choiceProtocol.setItems(choiceProtocolList);
        choiceProtocol.getSelectionModel().select(AppPreferences.getInstance().getProtocol());
        choiceProtocol.setOnAction(e-> {
            tableFilesListController.setNewProtocol(choiceProtocol.getSelectionModel().getSelectedItem());
            if (choiceProtocol.getSelectionModel().getSelectedItem().equals("GoldLeaf")) {
                choiceNetUsb.setDisable(true);
                choiceNetUsb.getSelectionModel().select("USB");
                nsIpLbl.setVisible(false);
                nsIpTextField.setVisible(false);
            }
            else {
                choiceNetUsb.setDisable(false);
                if (choiceNetUsb.getSelectionModel().getSelectedItem().equals("NET")) {
                    nsIpLbl.setVisible(true);
                    nsIpTextField.setVisible(true);
                }
            }
            // Really bad disable-enable upload button function
            if (tableFilesListController.isFilesForUploadListEmpty())
                MediatorControl.getInstance().getContoller().disableUploadStopBtn(true);
            else
                MediatorControl.getInstance().getContoller().disableUploadStopBtn(false);
        });  // Add listener to notify tableView controller
        tableFilesListController.setNewProtocol(choiceProtocol.getSelectionModel().getSelectedItem());   // Notify tableView controller

        ObservableList<String> choiceNetUsbList = FXCollections.observableArrayList("USB", "NET");
        choiceNetUsb.setItems(choiceNetUsbList);
        choiceNetUsb.getSelectionModel().select(AppPreferences.getInstance().getNetUsb());
        if (choiceProtocol.getSelectionModel().getSelectedItem().equals("GoldLeaf")) {
            choiceNetUsb.setDisable(true);
            choiceNetUsb.getSelectionModel().select("USB");
        }
        choiceNetUsb.setOnAction(e->{
            if (choiceNetUsb.getSelectionModel().getSelectedItem().equals("NET")){
                nsIpLbl.setVisible(true);
                nsIpTextField.setVisible(true);
            }
            else{
                nsIpLbl.setVisible(false);
                nsIpTextField.setVisible(false);
            }
        });
        // Set and configure NS IP field behavior
        nsIpTextField.setText(AppPreferences.getInstance().getNsIp());
        if (choiceProtocol.getSelectionModel().getSelectedItem().equals("TinFoil") && choiceNetUsb.getSelectionModel().getSelectedItem().equals("NET")){
            nsIpLbl.setVisible(true);
            nsIpTextField.setVisible(true);
        }
        nsIpTextField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().contains(" ") | change.getControlNewText().contains("\t"))
                return null;
            else
                return change;
        }));
        // Set and configure switch theme button
        Region btnSwitchImage = new Region();
        btnSwitchImage.getStyleClass().add("regionLamp");
        switchThemeBtn.setGraphic(btnSwitchImage);
        this.switchThemeBtn.setOnAction(e->switchTheme());
    }
    /**
     * Changes UI theme on the go
     * */
    private void switchTheme(){
        if (switchThemeBtn.getScene().getStylesheets().get(0).equals("/res/app_dark.css")) {
            switchThemeBtn.getScene().getStylesheets().remove("/res/app_dark.css");
            switchThemeBtn.getScene().getStylesheets().add("/res/app_light.css");
        }
        else {
            switchThemeBtn.getScene().getStylesheets().remove("/res/app_light.css");
            switchThemeBtn.getScene().getStylesheets().add("/res/app_dark.css");
        }
        AppPreferences.getInstance().setTheme(switchThemeBtn.getScene().getStylesheets().get(0));
    }
    /**
     * Get selected protocol (GL/TF)
     * */
    String getSelectedProtocol(){
        return choiceProtocol.getSelectionModel().getSelectedItem();
    }
    /**
     * Get selected protocol (USB/NET)
     * */
    String getSelectedNetUsb(){
        return choiceNetUsb.getSelectionModel().getSelectedItem();
    }
    /**
     * Get NS IP address
     * */
    String getNsIp(){
        return nsIpTextField.getText();
    }
}
