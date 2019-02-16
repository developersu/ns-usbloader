package nsusbloader;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import nsusbloader.Controllers.NSTableViewController;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class NSLMainController implements Initializable {

    private ResourceBundle resourceBundle;

    private List<File> nspToUpload;

    @FXML
    private TextArea logArea;
    @FXML
    private Button selectNspBtn;
    @FXML
    private Button uploadStopBtn;
    private Region btnUpStopImage;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private ChoiceBox<String> choiceProtocol;
    @FXML
    private Button switchThemeBtn;

    @FXML
    private Pane specialPane;

    @FXML
    private NSTableViewController tableFilesListController;

    private Thread usbThread;

    private String previouslyOpenedPath;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.resourceBundle = rb;
        logArea.setText(rb.getString("logsGreetingsMessage")+" "+NSLMain.appVersion+"!\n");
        if (System.getProperty("os.name").toLowerCase().startsWith("lin"))
            if (!System.getProperty("user.name").equals("root"))
                logArea.appendText(rb.getString("logsEnteredAsMsg1")+System.getProperty("user.name")+"\n"+rb.getString("logsEnteredAsMsg2") + "\n");

        logArea.appendText(rb.getString("logsGreetingsMessage2")+"\n");

        MediatorControl.getInstance().registerController(this);

        specialPane.getStyleClass().add("special-pane-as-border");  // UI hacks

        uploadStopBtn.setDisable(true);
        selectNspBtn.setOnAction(e->{ selectFilesBtnAction(); });
        uploadStopBtn.setOnAction(e->{ uploadBtnAction(); });

        this.btnUpStopImage = new Region();
        btnUpStopImage.getStyleClass().add("regionUpload");
        //uploadStopBtn.getStyleClass().remove("button");
        uploadStopBtn.getStyleClass().add("buttonUp");
        uploadStopBtn.setGraphic(btnUpStopImage);

        ObservableList<String> choiceProtocolList = FXCollections.observableArrayList("TinFoil", "GoldLeaf");
        choiceProtocol.setItems(choiceProtocolList);
        choiceProtocol.getSelectionModel().select(0);                               // TODO: shared settings
        choiceProtocol.setOnAction(e->tableFilesListController.protocolChangeEvent(choiceProtocol.getSelectionModel().getSelectedItem()));

        this.previouslyOpenedPath = null;

        Region btnSwitchImage = new Region();
        btnSwitchImage.getStyleClass().add("regionLamp");
        switchThemeBtn.setGraphic(btnSwitchImage);
        this.switchThemeBtn.setOnAction(e->switchTheme());

    }
    /**
     * Changes UI theme on the go
     * */
    private void switchTheme(){
        if (switchThemeBtn.getScene().getStylesheets().get(0).equals("/res/app.css")) {
            switchThemeBtn.getScene().getStylesheets().remove("/res/app.css");
            switchThemeBtn.getScene().getStylesheets().add("/res/app_light.css");
        }
        else {
            switchThemeBtn.getScene().getStylesheets().add("/res/app.css");
            switchThemeBtn.getScene().getStylesheets().remove("/res/app_light.css");
        }
    }
    /**
     * Functionality for selecting NSP button.
     * Uses setReady and setNotReady to simplify code readability.
     * */
    private void selectFilesBtnAction(){
        List<File> filesList;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(resourceBundle.getString("btnFileOpen"));
        if (previouslyOpenedPath == null)
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));         // TODO: read from prefs
        else {
            File validator = new File(previouslyOpenedPath);
            if (validator.exists())
                fileChooser.setInitialDirectory(validator);         // TODO: read from prefs
            else
                fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));         // TODO: read from prefs
        }
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NS ROM", "*.nsp"));

        filesList = fileChooser.showOpenMultipleDialog(logArea.getScene().getWindow());
        if (filesList != null && !filesList.isEmpty()) {
            setReady(filesList);
            previouslyOpenedPath = filesList.get(0).getParent();
        }
        else
            setNotReady(resourceBundle.getString("logsNoFolderFileSelected"));
    }
    private void setReady(List<File> filesList){
        logArea.setText(resourceBundle.getString("logsFilesToUploadTitle")+"\n");
        for (File item: filesList)
            logArea.appendText("  "+item.getAbsolutePath()+"\n");
        nspToUpload = filesList;
        uploadStopBtn.setDisable(false);
    }
    private void setNotReady(String whyNotReady){
        logArea.setText(whyNotReady);
        nspToUpload = null;
        uploadStopBtn.setDisable(true);
    }
    /**
     * It's button listener when no transmission executes
     * */
    private void uploadBtnAction(){
        if (usbThread == null || !usbThread.isAlive()){
            UsbCommunications usbCommunications = new UsbCommunications(logArea, progressBar, nspToUpload, choiceProtocol.getSelectionModel().getSelectedItem());
            usbThread = new Thread(usbCommunications);
            usbThread.start();
        }
    }
    /**
     * It's button listener when transmission in progress
     * */
    private void stopBtnAction(){
        if (usbThread != null && usbThread.isAlive()){
            usbThread.interrupt();
        }
    }
    /**
     * This thing modify UI for reusing 'Upload to NS' button and make functionality set for "Stop transmission"
     * Called from mediator
     * */
    void notifyTransmissionStarted(boolean isTransmissionStarted){
        if (isTransmissionStarted) {
            selectNspBtn.setDisable(true);
            uploadStopBtn.setOnAction(e->{ stopBtnAction(); });

            uploadStopBtn.setText(resourceBundle.getString("btnStop"));

            btnUpStopImage.getStyleClass().remove("regionUpload");
            btnUpStopImage.getStyleClass().add("regionStop");

            uploadStopBtn.getStyleClass().remove("buttonUp");
            uploadStopBtn.getStyleClass().add("buttonStop");
        }
        else {
            selectNspBtn.setDisable(false);
            uploadStopBtn.setOnAction(e->{ uploadBtnAction(); });

            uploadStopBtn.setText(resourceBundle.getString("btnUpload"));

            btnUpStopImage.getStyleClass().remove("regionStop");
            btnUpStopImage.getStyleClass().add("regionUpload");

            uploadStopBtn.getStyleClass().remove("buttonStop");
            uploadStopBtn.getStyleClass().add("buttonUp");
        }
    }
}
