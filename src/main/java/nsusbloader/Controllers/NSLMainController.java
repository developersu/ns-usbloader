package nsusbloader.Controllers;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import nsusbloader.*;
import nsusbloader.ModelControllers.UpdatesChecker;
import nsusbloader.COM.NET.NETCommunications;
import nsusbloader.COM.USB.UsbCommunications;

import java.io.File;
import java.net.*;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;

public class NSLMainController implements Initializable {

    private ResourceBundle resourceBundle;

    @FXML
    public TextArea logArea;            // Accessible from Mediator
    @FXML
    private Button selectNspBtn, selectSplitNspBtn, uploadStopBtn;

    private Region btnUpStopImage;
    @FXML
    public ProgressBar progressBar;            // Accessible from Mediator

    @FXML
    private SettingsController SettingsTabController;
    @FXML
    public FrontController FrontTabController;             // Accessible from Mediator | todo: incapsulate

    private Task<Void> usbNetCommunications;
    private Thread workThread;

    private String previouslyOpenedPath;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.resourceBundle = rb;
        logArea.setText(rb.getString("tab3_Txt_GreetingsMessage")+" "+ NSLMain.appVersion+"!\n");
        if (System.getProperty("os.name").toLowerCase().startsWith("lin"))
            if (!System.getProperty("user.name").equals("root"))
                logArea.appendText(rb.getString("tab3_Txt_EnteredAsMsg1")+System.getProperty("user.name")+"\n"+rb.getString("tab3_Txt_EnteredAsMsg2") + "\n");

        logArea.appendText(rb.getString("tab3_Txt_GreetingsMessage2")+"\n");

        MediatorControl.getInstance().setController(this);

        if (FrontTabController.getSelectedProtocol().equals("TinFoil"))
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

        if (AppPreferences.getInstance().getAutoCheckUpdates()){
            Task<List<String>> updTask = new UpdatesChecker();
            updTask.setOnSucceeded(event->{
                List<String> result = updTask.getValue();
                if (result != null){
                    if (!result.get(0).isEmpty()) {
                        SettingsTabController.setNewVersionLink(result.get(0));
                        ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionAval"), resourceBundle.getString("windowTitleNewVersionAval") + ": " + result.get(0) + "\n\n" + result.get(1));
                    }
                }
                else
                    ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionUnknown"), resourceBundle.getString("windowBodyNewVersionUnknown"));
            });
            Thread updates = new Thread(updTask);
            updates.setDaemon(true);
            updates.start();
        }
    }

    /**
     * Get resources
     * TODO: Find better solution; used in UsbCommunications() -> GL -> SelectFile command
     * @return ResourceBundle
     */
    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
    /**
     * Provide hostServices to Settings tab
     * */
    public void setHostServices(HostServices hs ){ SettingsTabController.registerHostServices(hs);}

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

        if (FrontTabController.getSelectedProtocol().equals("TinFoil") && SettingsTabController.getTfXciNszXczSupport())
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NSP/XCI/NSZ/XCZ", "*.nsp", "*.xci", "*.nsz", "*.xcz"));
        else if (FrontTabController.getSelectedProtocol().equals("GoldLeaf") && (! SettingsTabController.getNSPFileFilterForGL()))
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Any file", "*.*"),
                    new FileChooser.ExtensionFilter("NSP ROM", "*.nsp")
            );
        else
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NSP ROM", "*.nsp"));
        
        filesList = fileChooser.showOpenMultipleDialog(logArea.getScene().getWindow());
        if (filesList != null && !filesList.isEmpty()) {
            FrontTabController.tableFilesListController.setFiles(filesList);
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

        splitFile = dirChooser.showDialog(logArea.getScene().getWindow());

        if (splitFile != null && splitFile.getName().toLowerCase().endsWith(".nsp")) {
            FrontTabController.tableFilesListController.setFile(splitFile);
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
            if (FrontTabController.tableFilesListController.getFilesForUpload() == null && FrontTabController.getSelectedProtocol().equals("TinFoil")) {
                logArea.setText(resourceBundle.getString("tab3_Txt_NoFolderOrFileSelected"));
                return;
            }
            else {
                if ((nspToUpload = FrontTabController.tableFilesListController.getFilesForUpload()) != null){
                    logArea.setText(resourceBundle.getString("tab3_Txt_FilesToUploadTitle")+"\n");
                    for (File item: nspToUpload)
                        logArea.appendText("  "+item.getAbsolutePath()+"\n");
                }
                else {
                    logArea.clear();
                    nspToUpload = new LinkedList<>();
                }
            }
            // If USB selected
            if (FrontTabController.getSelectedProtocol().equals("GoldLeaf") ||
                    ( FrontTabController.getSelectedProtocol().equals("TinFoil") && FrontTabController.getSelectedNetUsb().equals("USB") )
            ){
                usbNetCommunications = new UsbCommunications(nspToUpload, FrontTabController.getSelectedProtocol()+SettingsTabController.getGlOldVer(), SettingsTabController.getNSPFileFilterForGL());
                workThread = new Thread(usbNetCommunications);
                workThread.setDaemon(true);
                workThread.start();
            }
            else {      // NET INSTALL OVER TINFOIL
                if (SettingsTabController.isNsIpValidate() && ! FrontTabController.getNsIp().matches("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"))
                    if (!ServiceWindow.getConfirmationWindow(resourceBundle.getString("windowTitleBadIp"),resourceBundle.getString("windowBodyBadIp")))
                        return;

                String nsIP = FrontTabController.getNsIp();

                if (!SettingsTabController.getExpertModeSelected())
                    usbNetCommunications = new NETCommunications(nspToUpload, nsIP, false, "", "", "");
                else {
                    usbNetCommunications = new NETCommunications(
                            nspToUpload,
                            nsIP,
                            SettingsTabController.getNotServeSelected(),
                            SettingsTabController.getAutoIpSelected()?"":SettingsTabController.getHostIp(),
                            SettingsTabController.getRandPortSelected()?"":SettingsTabController.getHostPort(),
                            SettingsTabController.getNotServeSelected()?SettingsTabController.getHostExtra():""
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
     * */
    public void notifyTransmissionStarted(boolean isTransmissionStarted){
        if (isTransmissionStarted) {
            selectNspBtn.setDisable(true);
            selectSplitNspBtn.setDisable(true);
            uploadStopBtn.setOnAction(e-> stopBtnAction());

            uploadStopBtn.setText(resourceBundle.getString("btn_Stop"));

            btnUpStopImage.getStyleClass().remove("regionUpload");
            btnUpStopImage.getStyleClass().add("regionStop");

            uploadStopBtn.getStyleClass().remove("buttonUp");
            uploadStopBtn.getStyleClass().add("buttonStop");
        }
        else {
            selectNspBtn.setDisable(false);
            selectSplitNspBtn.setDisable(false);
            uploadStopBtn.setOnAction(e-> uploadBtnAction());

            uploadStopBtn.setText(resourceBundle.getString("btn_Upload"));

            btnUpStopImage.getStyleClass().remove("regionStop");
            btnUpStopImage.getStyleClass().add("regionUpload");

            uploadStopBtn.getStyleClass().remove("buttonStop");
            uploadStopBtn.getStyleClass().add("buttonUp");
        }
    }
    /**
     * Crunch. Now you see that I'm not a programmer.. This function called from NSTableViewController
     * */
    public void disableUploadStopBtn(boolean disable){
        if (FrontTabController.getSelectedProtocol().equals("TinFoil"))
            uploadStopBtn.setDisable(disable);
        else
            uploadStopBtn.setDisable(false);
    }
    /**
     * Drag-n-drop support (dragOver consumer)
     * */
    @FXML
    private void handleDragOver(DragEvent event){
        if (event.getDragboard().hasFiles())
            event.acceptTransferModes(TransferMode.ANY);
    }
    /**
     * Drag-n-drop support (drop consumer)
     * */
    @FXML
    private void handleDrop(DragEvent event){
        if (MediatorControl.getInstance().getTransferActive()) {
            event.setDropCompleted(true);
            return;
        }
        List<File> filesDropped = event.getDragboard().getFiles();

        if (FrontTabController.getSelectedProtocol().equals("TinFoil") && SettingsTabController.getTfXciNszXczSupport())
            filesDropped.removeIf(file -> ! file.getName().toLowerCase().matches("(.*\\.nsp$)|(.*\\.xci$)|(.*\\.nsz$)|(.*\\.xcz$)"));
        else if (FrontTabController.getSelectedProtocol().equals("GoldLeaf") && (! SettingsTabController.getNSPFileFilterForGL()))
            filesDropped.removeIf(file -> (file.isDirectory() && ! file.getName().toLowerCase().matches(".*\\.nsp$")));
        else
            filesDropped.removeIf(file -> ! file.getName().toLowerCase().matches(".*\\.nsp$"));

        if ( ! filesDropped.isEmpty() )
            FrontTabController.tableFilesListController.setFiles(filesDropped);

        event.setDropCompleted(true);
    }
    /**
     * Save preferences before exit
     * */
    public void exit(){
        AppPreferences.getInstance().setAll(
                FrontTabController.getSelectedProtocol(),
                previouslyOpenedPath,
                FrontTabController.getSelectedNetUsb(),
                FrontTabController.getNsIp(),
                SettingsTabController.isNsIpValidate(),
                SettingsTabController.getExpertModeSelected(),
                SettingsTabController.getAutoIpSelected(),
                SettingsTabController.getRandPortSelected(),
                SettingsTabController.getNotServeSelected(),
                SettingsTabController.getHostIp(),
                SettingsTabController.getHostPort(),
                SettingsTabController.getHostExtra(),
                SettingsTabController.getAutoCheckForUpdates(),
                SettingsTabController.getTfXciNszXczSupport(),
                SettingsTabController.getNSPFileFilterForGL(),
                SettingsTabController.getGlOldVer()
        );
    }
}
