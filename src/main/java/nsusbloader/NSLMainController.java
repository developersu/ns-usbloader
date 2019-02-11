package nsusbloader;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;

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

    private Thread usbThread;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.resourceBundle = rb;
        logArea.setText(rb.getString("logsGreetingsMessage")+" "+NSLMain.appVersion+"!\n");
        if (System.getProperty("os.name").toLowerCase().startsWith("lin"))
            if (!System.getProperty("user.name").equals("root"))
                logArea.appendText(rb.getString("logsEnteredAsMsg1")+System.getProperty("user.name")+"\n"+rb.getString("logsEnteredAsMsg2") + "\n");

        logArea.appendText(rb.getString("logsGreetingsMessage2")+"\n");

        MediatorControl.getInstance().registerController(this);

        uploadStopBtn.setDisable(true);
        selectNspBtn.setOnAction(e->{ selectFilesBtnAction(); });
        uploadStopBtn.setOnAction(e->{ uploadToNsBtnAction(); });

        this.btnUpStopImage = new Region();
        btnUpStopImage.getStyleClass().add("regionUpload");
        //uploadStopBtn.getStyleClass().remove("button");
        uploadStopBtn.getStyleClass().add("buttonUp");
        uploadStopBtn.setGraphic(btnUpStopImage);
    }
    /**
     * Functionality for selecting NSP button.
     * Uses setReady and setNotReady to simplify code readability.
     * */
    private void selectFilesBtnAction(){
        List<File> filesList;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(resourceBundle.getString("btnFileOpen"));
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));         // TODO: read from prefs
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NS ROM", "*.nsp"));

        filesList = fileChooser.showOpenMultipleDialog(logArea.getScene().getWindow());
        if (filesList != null && !filesList.isEmpty())
            setReady(filesList);
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
    private void uploadToNsBtnAction(){
        if (usbThread == null || !usbThread.isAlive()){
            UsbCommunications usbCommunications = new UsbCommunications(logArea, progressBar, nspToUpload);  //todo: progress bar
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
            uploadStopBtn.setOnAction(e->{ uploadToNsBtnAction(); });

            uploadStopBtn.setText(resourceBundle.getString("btnUpload"));

            btnUpStopImage.getStyleClass().remove("regionStop");
            btnUpStopImage.getStyleClass().add("regionUpload");

            uploadStopBtn.getStyleClass().remove("buttonStop");
            uploadStopBtn.getStyleClass().add("buttonUp");
        }
    }
}
