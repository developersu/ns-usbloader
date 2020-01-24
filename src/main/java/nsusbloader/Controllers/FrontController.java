package nsusbloader.Controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.COM.NET.NETCommunications;
import nsusbloader.COM.USB.UsbCommunications;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.ServiceWindow;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;

public class FrontController implements Initializable {
    @FXML
    private Pane specialPane;
    @FXML
    private AnchorPane usbNetPane;

    @FXML
    private ChoiceBox<String> choiceProtocol, choiceNetUsb;
    @FXML
    private Label nsIpLbl;
    @FXML
    private TextField nsIpTextField;
    @FXML
    private Button switchThemeBtn;
    @FXML
    public NSTableViewController tableFilesListController;            // Accessible from Mediator (for drag-n-drop support)

    @FXML
    private Button selectNspBtn, selectSplitNspBtn, uploadStopBtn;
    private String previouslyOpenedPath;
    private Region btnUpStopImage;
    private ResourceBundle resourceBundle;
    private Task<Void> usbNetCommunications;
    private Thread workThread;
    
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
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
                disableUploadStopBtn(true);
            else
                disableUploadStopBtn(false);
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


        if (getSelectedProtocol().equals("TinFoil"))
            uploadStopBtn.setDisable(true);
        else
            uploadStopBtn.setDisable(false);
        selectNspBtn.setOnAction(e-> selectFilesBtnAction());

        selectSplitNspBtn.setOnAction(e-> selectSplitBtnAction());
        selectSplitNspBtn.getStyleClass().add("buttonSelect");

        uploadStopBtn.setOnAction(e-> uploadBtnAction());

        selectNspBtn.getStyleClass().add("buttonSelect");

        this.btnUpStopImage = new Region();
        btnUpStopImage.getStyleClass().add("regionUpload");

        uploadStopBtn.getStyleClass().add("buttonUp");
        uploadStopBtn.setGraphic(btnUpStopImage);

        this.previouslyOpenedPath = AppPreferences.getInstance().getRecent();
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
    
    
    /*-****************************************************************************************************************-*/
    /**
     * Functionality for selecting NSP button.
     * */
    private void selectFilesBtnAction(){
        List<File> filesList;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(resourceBundle.getString("btn_OpenFile"));

        File validator = new File(previouslyOpenedPath);
        if (validator.exists())
            fileChooser.setInitialDirectory(validator);
        else
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        if (getSelectedProtocol().equals("TinFoil") && MediatorControl.getInstance().getContoller().getSettingsCtrlr().getTfXciNszXczSupport())
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NSP/XCI/NSZ/XCZ", "*.nsp", "*.xci", "*.nsz", "*.xcz"));
        else if (getSelectedProtocol().equals("GoldLeaf") && (! MediatorControl.getInstance().getContoller().getSettingsCtrlr().getNSPFileFilterForGL()))
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Any file", "*.*"),
                    new FileChooser.ExtensionFilter("NSP ROM", "*.nsp")
            );
        else
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NSP ROM", "*.nsp"));

        filesList = fileChooser.showOpenMultipleDialog(specialPane.getScene().getWindow());
        if (filesList != null && !filesList.isEmpty()) {
            tableFilesListController.setFiles(filesList);
            uploadStopBtn.setDisable(false);
            previouslyOpenedPath = filesList.get(0).getParent();
        }
    }
    /**
     * Functionality for selecting Split NSP button.
     * */
    private void selectSplitBtnAction(){
        File splitFile;
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(resourceBundle.getString("btn_OpenFile"));

        File validator = new File(previouslyOpenedPath);
        if (validator.exists())
            dirChooser.setInitialDirectory(validator);
        else
            dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        splitFile = dirChooser.showDialog(specialPane.getScene().getWindow());

        if (splitFile != null && splitFile.getName().toLowerCase().endsWith(".nsp")) {
            tableFilesListController.setFile(splitFile);
            uploadStopBtn.setDisable(false);    // Is it useful?
            previouslyOpenedPath = splitFile.getParent();
        }
    }
    /**
     * It's button listener when no transmission executes
     * */
    private void uploadBtnAction(){
        if ((workThread == null || !workThread.isAlive())){
            // Collect files
            List<File> nspToUpload;
            if (tableFilesListController.getFilesForUpload() == null && getSelectedProtocol().equals("TinFoil")) {
                MediatorControl.getInstance().getContoller().logArea.setText(resourceBundle.getString("tab3_Txt_NoFolderOrFileSelected"));
                return;
            }
            else {
                if ((nspToUpload = tableFilesListController.getFilesForUpload()) != null){
                    MediatorControl.getInstance().getContoller().logArea.setText(resourceBundle.getString("tab3_Txt_FilesToUploadTitle")+"\n");
                    for (File item: nspToUpload)
                        MediatorControl.getInstance().getContoller().logArea.appendText("  "+item.getAbsolutePath()+"\n");
                }
                else {
                    MediatorControl.getInstance().getContoller().logArea.clear();
                    nspToUpload = new LinkedList<>();
                }
            }
            // If USB selected
            if (getSelectedProtocol().equals("GoldLeaf") ||
                    ( getSelectedProtocol().equals("TinFoil") && getSelectedNetUsb().equals("USB") )
            ){
                usbNetCommunications = new UsbCommunications(nspToUpload, getSelectedProtocol()+MediatorControl.getInstance().getContoller().getSettingsCtrlr().getGlOldVer(), MediatorControl.getInstance().getContoller().getSettingsCtrlr().getNSPFileFilterForGL());
                workThread = new Thread(usbNetCommunications);
                workThread.setDaemon(true);
                workThread.start();
            }
            else {      // NET INSTALL OVER TINFOIL
                if (MediatorControl.getInstance().getContoller().getSettingsCtrlr().isNsIpValidate() && ! getNsIp().matches("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"))
                    if (!ServiceWindow.getConfirmationWindow(resourceBundle.getString("windowTitleBadIp"),resourceBundle.getString("windowBodyBadIp")))
                        return;

                String nsIP = getNsIp();

                if (! MediatorControl.getInstance().getContoller().getSettingsCtrlr().getExpertModeSelected())
                    usbNetCommunications = new NETCommunications(nspToUpload, nsIP, false, "", "", "");
                else {
                    usbNetCommunications = new NETCommunications(
                            nspToUpload,
                            nsIP,
                            MediatorControl.getInstance().getContoller().getSettingsCtrlr().getNotServeSelected(),
                            MediatorControl.getInstance().getContoller().getSettingsCtrlr().getAutoIpSelected()?"":MediatorControl.getInstance().getContoller().getSettingsCtrlr().getHostIp(),
                            MediatorControl.getInstance().getContoller().getSettingsCtrlr().getRandPortSelected()?"":MediatorControl.getInstance().getContoller().getSettingsCtrlr().getHostPort(),
                            MediatorControl.getInstance().getContoller().getSettingsCtrlr().getNotServeSelected()?MediatorControl.getInstance().getContoller().getSettingsCtrlr().getHostExtra():""
                    );
                }

                workThread = new Thread(usbNetCommunications);
                workThread.setDaemon(true);
                workThread.start();
            }
        }
    }
    /**
     * It's button listener when transmission in progress
     * */
    private void stopBtnAction(){
        if (workThread != null && workThread.isAlive()){
            usbNetCommunications.cancel(false);
        }
    }
    /**
     * This thing modify UI for reusing 'Upload to NS' button and make functionality set for "Stop transmission"
     * Called from mediator
     * TODO: remove shitcoding practices
     * */
    public void notifyTransmThreadStarted(boolean isActive, EModule type){
        if (! type.equals(EModule.USB_NET_TRANSFERS)){
            usbNetPane.setDisable(isActive);
            return;
        }
        if (isActive) {
            selectNspBtn.setDisable(true);
            selectSplitNspBtn.setDisable(true);
            btnUpStopImage.getStyleClass().clear();
            btnUpStopImage.getStyleClass().add("regionStop");

            uploadStopBtn.setOnAction(e-> stopBtnAction());
            uploadStopBtn.setText(resourceBundle.getString("btn_Stop"));
            uploadStopBtn.getStyleClass().remove("buttonUp");
            uploadStopBtn.getStyleClass().add("buttonStop");
            return;
        }
        selectNspBtn.setDisable(false);
        selectSplitNspBtn.setDisable(false);
        btnUpStopImage.getStyleClass().clear();
        btnUpStopImage.getStyleClass().add("regionUpload");

        uploadStopBtn.setOnAction(e-> uploadBtnAction());
        uploadStopBtn.setText(resourceBundle.getString("btn_Upload"));
        uploadStopBtn.getStyleClass().remove("buttonStop");
        uploadStopBtn.getStyleClass().add("buttonUp");
    }
    /**
     * Crunch. This function called from NSTableViewController
     * */
    public void disableUploadStopBtn(boolean disable){
        if (getSelectedProtocol().equals("TinFoil"))
            uploadStopBtn.setDisable(disable);
        else
            uploadStopBtn.setDisable(false);
    }
    /**
     * Get 'Recent' path
     */
    public String getRecentPath(){
        return previouslyOpenedPath;
    }
}
