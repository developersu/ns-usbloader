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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.COM.NET.NETCommunications;
import nsusbloader.COM.USB.UsbCommunications;
import nsusbloader.MediatorControl;
import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.ServiceWindow;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;

public class FrontController implements Initializable {
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
    private CancellableRunnable usbNetCommunications;
    private Thread workThread;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;

        ObservableList<String> choiceProtocolList = FXCollections.observableArrayList("TinFoil", "GoldLeaf");

        choiceProtocol.setItems(choiceProtocolList);
        choiceProtocol.getSelectionModel().select(AppPreferences.getInstance().getProtocol());
        choiceProtocol.setOnAction(e-> {
            tableFilesListController.setNewProtocol(getSelectedProtocol());
            if (getSelectedProtocol().equals("GoldLeaf")) {
                choiceNetUsb.setDisable(true);
                choiceNetUsb.getSelectionModel().select("USB");
                nsIpLbl.setVisible(false);
                nsIpTextField.setVisible(false);
            }
            else {
                choiceNetUsb.setDisable(false);
                if (getSelectedNetUsb().equals("NET")) {
                    nsIpLbl.setVisible(true);
                    nsIpTextField.setVisible(true);
                }
            }
            // Really bad disable-enable upload button function
            disableUploadStopBtn(tableFilesListController.isFilesForUploadListEmpty());
        });  // Add listener to notify tableView controller
        tableFilesListController.setNewProtocol(getSelectedProtocol());   // Notify tableView controller

        ObservableList<String> choiceNetUsbList = FXCollections.observableArrayList("USB", "NET");
        choiceNetUsb.setItems(choiceNetUsbList);
        choiceNetUsb.getSelectionModel().select(AppPreferences.getInstance().getNetUsb());
        if (getSelectedProtocol().equals("GoldLeaf")) {
            choiceNetUsb.setDisable(true);
            choiceNetUsb.getSelectionModel().select("USB");
        }
        choiceNetUsb.setOnAction(e->{
            if (getSelectedNetUsb().equals("NET")){
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
        if (getSelectedProtocol().equals("TinFoil") && getSelectedNetUsb().equals("NET")){
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


        uploadStopBtn.setDisable(getSelectedProtocol().equals("TinFoil"));
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
        final String darkTheme = "/res/app_dark.css";
        final String lightTheme = "/res/app_light.css";
        final ObservableList<String> styleSheets = switchThemeBtn.getScene().getStylesheets();

        if (styleSheets.get(0).equals(darkTheme)) {
            styleSheets.remove(darkTheme);
            styleSheets.add(lightTheme);
        }
        else {
            styleSheets.remove(lightTheme);
            styleSheets.add(darkTheme);
        }
        AppPreferences.getInstance().setTheme(styleSheets.get(0));
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
    
    /**
     * Functionality for selecting NSP button.
     * */
    private void selectFilesBtnAction(){
        List<File> filesList;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(resourceBundle.getString("btn_OpenFile"));

        File validator = new File(previouslyOpenedPath);
        if (validator.exists() && validator.isDirectory())
            fileChooser.setInitialDirectory(validator);
        else
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        if (getSelectedProtocol().equals("TinFoil") && MediatorControl.getInstance().getContoller().getSettingsCtrlr().getTinfoilSettings().isXciNszXczSupport())
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NSP/XCI/NSZ/XCZ", "*.nsp", "*.xci", "*.nsz", "*.xcz"));
        else if (getSelectedProtocol().equals("GoldLeaf") && (! MediatorControl.getInstance().getContoller().getSettingsCtrlr().getNSPFileFilterForGL()))
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Any file", "*.*"),
                    new FileChooser.ExtensionFilter("NSP ROM", "*.nsp")
            );
        else
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NSP ROM", "*.nsp"));

        filesList = fileChooser.showOpenMultipleDialog(usbNetPane.getScene().getWindow());
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
        if (validator.exists() && validator.isDirectory())
            dirChooser.setInitialDirectory(validator);
        else
            dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        splitFile = dirChooser.showDialog(usbNetPane.getScene().getWindow());

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
        if (workThread != null && workThread.isAlive())
            return;

        // Collect files
        List<File> nspToUpload;

        TextArea logArea = MediatorControl.getInstance().getContoller().logArea;

        if (getSelectedProtocol().equals("TinFoil") && tableFilesListController.getFilesForUpload() == null) {
            logArea.setText(resourceBundle.getString("tab3_Txt_NoFolderOrFileSelected"));
            return;
        }

        if ((nspToUpload = tableFilesListController.getFilesForUpload()) != null){
            logArea.setText(resourceBundle.getString("tab3_Txt_FilesToUploadTitle")+"\n");
            nspToUpload.forEach(item -> logArea.appendText(" "+item.getAbsolutePath()+"\n"));
        }
        else {
            logArea.clear();
            nspToUpload = new LinkedList<>();
        }

        SettingsController settings = MediatorControl.getInstance().getContoller().getSettingsCtrlr();
        // If USB selected
        if (getSelectedProtocol().equals("GoldLeaf") ){
            usbNetCommunications = new UsbCommunications(nspToUpload, "GoldLeaf" + settings.getGlVer(), settings.getNSPFileFilterForGL());
        }
        else if (( getSelectedProtocol().equals("TinFoil") && getSelectedNetUsb().equals("USB") )){
            usbNetCommunications = new UsbCommunications(nspToUpload, "TinFoil", settings.getNSPFileFilterForGL());
        }
        else {      // NET INSTALL OVER TINFOIL
            final String ipValidationPattern = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
            final SettingsBlockTinfoilController tinfoilSettings = settings.getTinfoilSettings();

            if (tinfoilSettings.isValidateNSHostName() && ! getNsIp().matches(ipValidationPattern)) {
                if (!ServiceWindow.getConfirmationWindow(resourceBundle.getString("windowTitleBadIp"), resourceBundle.getString("windowBodyBadIp")))
                    return;
            }

            String nsIP = getNsIp();

            if (! tinfoilSettings.isExpertModeSelected())
                usbNetCommunications = new NETCommunications(nspToUpload, nsIP, false, "", "", "");
            else {
                usbNetCommunications = new NETCommunications(
                        nspToUpload,
                        nsIP,
                        tinfoilSettings.isNoRequestsServe(),
                        tinfoilSettings.isAutoDetectIp()?"":tinfoilSettings.getHostIp(),
                        tinfoilSettings.isRandomlySelectPort()?"":tinfoilSettings.getHostPort(),
                        tinfoilSettings.isNoRequestsServe()?tinfoilSettings.getHostExtra():""
                );
            }
        }
        workThread = new Thread(usbNetCommunications);
        workThread.setDaemon(true);
        workThread.start();
    }
    /**
     * It's button listener when transmission in progress
     * */
    private void stopBtnAction(){
        if (workThread != null && workThread.isAlive()){
            usbNetCommunications.cancel();

            if (usbNetCommunications instanceof NETCommunications){
                try{
                    ((NETCommunications) usbNetCommunications).getServerSocket().close();
                    ((NETCommunications) usbNetCommunications).getClientSocket().close();
                }
                catch (Exception ignore){ }
            }
        }
    }
    /**
     * Drag-n-drop support (dragOver consumer)
     * */
    @FXML
    private void handleDragOver(DragEvent event){
        if (event.getDragboard().hasFiles() && ! MediatorControl.getInstance().getTransferActive())
            event.acceptTransferModes(TransferMode.ANY);
        event.consume();
    }
    /**
     * Drag-n-drop support (drop consumer)
     * */
    @FXML
    private void handleDrop(DragEvent event){
        List<File> filesDropped = event.getDragboard().getFiles();
        SettingsController settingsController = MediatorControl.getInstance().getContoller().getSettingsCtrlr();
        SettingsBlockTinfoilController tinfoilSettings = settingsController.getTinfoilSettings();

        if (getSelectedProtocol().equals("TinFoil") && tinfoilSettings.isXciNszXczSupport())
            filesDropped.removeIf(file -> ! file.getName().toLowerCase().matches("(.*\\.nsp$)|(.*\\.xci$)|(.*\\.nsz$)|(.*\\.xcz$)"));
        else if (getSelectedProtocol().equals("GoldLeaf") && (! settingsController.getNSPFileFilterForGL()))
            filesDropped.removeIf(file -> (file.isDirectory() && ! file.getName().toLowerCase().matches(".*\\.nsp$")));
        else
            filesDropped.removeIf(file -> ! file.getName().toLowerCase().matches(".*\\.nsp$"));

        if ( ! filesDropped.isEmpty() )
            tableFilesListController.setFiles(filesDropped);

        event.setDropCompleted(true);
        event.consume();
    }
    /**
     * This thing modify UI for reusing 'Upload to NS' button and make functionality set for "Stop transmission"
     * Called from mediator
     * TODO: remove shitcoding practices
     * */
    public void notifyThreadStarted(boolean isActive, EModule type){
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

    public void updatePreferencesOnExit(){
        AppPreferences preferences = AppPreferences.getInstance();

        preferences.setProtocol(getSelectedProtocol());
        preferences.setRecent(getRecentPath());
        preferences.setNetUsb(getSelectedNetUsb());
        preferences.setNsIp(getNsIp());
    }
}
