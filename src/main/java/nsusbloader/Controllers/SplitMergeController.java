package nsusbloader.Controllers;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.ServiceWindow;
import nsusbloader.Utilities.SplitMergeTool;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class SplitMergeController implements Initializable {
    @FXML
    private ToggleGroup splitMergeTogGrp;
    @FXML
    private VBox smToolPane;

    @FXML
    private RadioButton splitRad, mergeRad;
    @FXML
    private Button selectFileFolderBtn,
            changeSaveToBtn,
            convertBtn;
    @FXML
    private Label fileFolderLabelLbl,
            fileFolderActualPathLbl,
            saveToPathLbl,
            statusLbl;

    private ResourceBundle resourceBundle;

    private Region convertRegion;
    private Task<Boolean> smTask;
    private Thread smThread;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
        convertRegion = new Region();
        convertBtn.setGraphic(convertRegion);

        splitRad.setOnAction((actionEvent -> {
            statusLbl.setText("");
            convertRegion.getStyleClass().clear();
            convertRegion.getStyleClass().add("regionSplitToOne");
            fileFolderLabelLbl.setText(resourceBundle.getString("tabSplMrg_Txt_File"));
            selectFileFolderBtn.setText(resourceBundle.getString("tabSplMrg_Btn_SelectFile"));
            fileFolderActualPathLbl.setText("");
            convertBtn.setDisable(true);
        }));
        mergeRad.setOnAction((actionEvent -> {
            statusLbl.setText("");
            convertRegion.getStyleClass().clear();
            convertRegion.getStyleClass().add("regionOneToSplit");
            fileFolderLabelLbl.setText(resourceBundle.getString("tabSplMrg_Txt_Folder"));
            selectFileFolderBtn.setText(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));
            fileFolderActualPathLbl.setText("");
            convertBtn.setDisable(true);
        }));

        if (AppPreferences.getInstance().getSplitMergeType() == 0)
            splitRad.fire();
        else
            mergeRad.fire();

        saveToPathLbl.setText(AppPreferences.getInstance().getSplitMergeRecent());

        changeSaveToBtn.setOnAction((actionEvent -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));
            dc.setInitialDirectory(new File(saveToPathLbl.getText()));
            File saveToDir = dc.showDialog(changeSaveToBtn.getScene().getWindow());
            if (saveToDir != null)
                saveToPathLbl.setText(saveToDir.getAbsolutePath());
        }));

        selectFileFolderBtn.setOnAction(actionEvent -> {
            statusLbl.setText("");
            if (splitRad.isSelected()) {
                FileChooser fc = new FileChooser();
                fc.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFile"));
                if (! fileFolderActualPathLbl.getText().isEmpty()){
                    File temporaryFile = new File(fileFolderActualPathLbl.getText()).getParentFile();
                    if (temporaryFile != null && temporaryFile.exists())
                        fc.setInitialDirectory(temporaryFile);
                    else
                        fc.setInitialDirectory(new File(System.getProperty("user.home")));
                }
                else
                    fc.setInitialDirectory(new File(System.getProperty("user.home")));
                File fileFile = fc.showOpenDialog(changeSaveToBtn.getScene().getWindow());
                if (fileFile == null)
                    return;
                fileFolderActualPathLbl.setText(fileFile.getAbsolutePath());
            }
            else{
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));
                if (! fileFolderActualPathLbl.getText().isEmpty()){
                    File temporaryFile = new File(fileFolderActualPathLbl.getText());
                    if (temporaryFile.exists())
                        dc.setInitialDirectory(temporaryFile);
                    else
                        dc.setInitialDirectory(new File(System.getProperty("user.home")));
                }
                else
                    dc.setInitialDirectory(new File(System.getProperty("user.home")));

                File folderFile = dc.showDialog(changeSaveToBtn.getScene().getWindow());
                if (folderFile == null)
                    return;
                fileFolderActualPathLbl.setText(folderFile.getAbsolutePath());
            }
            convertBtn.setDisable(false);
        });

        convertBtn.setOnAction(actionEvent -> setConvertBtnAction());
    }

    public void notifySmThreadStarted(boolean isStart, EModule type){ // todo: refactor: remove everything, place to separate container and just disable.
        if (! type.equals(EModule.SPLIT_MERGE_TOOL)){
            smToolPane.setDisable(isStart);
            return;
        }
        if (isStart){
            MediatorControl.getInstance().getContoller().logArea.clear();
            splitRad.setDisable(true);
            mergeRad.setDisable(true);
            selectFileFolderBtn.setDisable(true);
            changeSaveToBtn.setDisable(true);

            convertBtn.setOnAction(e -> stopBtnAction());
            convertBtn.setText(resourceBundle.getString("btn_Stop"));
            convertRegion.getStyleClass().clear();
            convertRegion.getStyleClass().add("regionStop");
            convertBtn.getStyleClass().remove("buttonUp");
            convertBtn.getStyleClass().add("buttonStop");
            return;
        }
        splitRad.setDisable(false);
        mergeRad.setDisable(false);
        selectFileFolderBtn.setDisable(false);
        changeSaveToBtn.setDisable(false);

        convertBtn.setOnAction(e -> setConvertBtnAction());
        convertBtn.setText(resourceBundle.getString("tabSplMrg_Btn_Convert"));
        convertRegion.getStyleClass().clear();
        convertBtn.getStyleClass().remove("buttonStop");
        convertBtn.getStyleClass().add("buttonUp");
        if (splitRad.isSelected())
            convertRegion.getStyleClass().add("regionSplitToOne");
        else
            convertRegion.getStyleClass().add("regionOneToSplit");
    }

    /**
     * It's button listener when convert-process in progress
     * */
    private void stopBtnAction(){
        if (smThread != null && smThread.isAlive())
            smTask.cancel(false);
    }
    /**
     * It's button listener when convert-process NOT in progress
     * */
    private void setConvertBtnAction(){
        statusLbl.setText("");
        if (MediatorControl.getInstance().getTransferActive()) {
            ServiceWindow.getErrorNotification(
                    resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("windowBodyPleaseFinishTransfersFirst")
            );
            return;
        }

        if (splitRad.isSelected())
            smTask = SplitMergeTool.splitFile(fileFolderActualPathLbl.getText(), saveToPathLbl.getText());
        else
            smTask = SplitMergeTool.mergeFile(fileFolderActualPathLbl.getText(), saveToPathLbl.getText());
        smTask.setOnCancelled(event -> statusLbl.setText(resourceBundle.getString("failure_txt")));
        smTask.setOnSucceeded(event -> {
            if (smTask.getValue())
                statusLbl.setText(resourceBundle.getString("done_txt"));
            else
                statusLbl.setText(resourceBundle.getString("failure_txt"));
        });
        smThread = new Thread(smTask);
        smThread.setDaemon(true);
        smThread.start();
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
    private void handleDrop(DragEvent event) {
        Node sourceNode = (Node) event.getSource();
        File fileDrpd = event.getDragboard().getFiles().get(0);

        if (fileDrpd.isDirectory())
            mergeRad.fire();
        else
            splitRad.fire();
        fileFolderActualPathLbl.setText(fileDrpd.getAbsolutePath());
        convertBtn.setDisable(false);
        event.setDropCompleted(true);
        event.consume();
    }
    /**
     * Save application settings on exit
     * */
    public void updatePreferencesOnExit(){
        if (splitRad.isSelected())
            AppPreferences.getInstance().setSplitMergeType(0);
        else
            AppPreferences.getInstance().setSplitMergeType(1);

        AppPreferences.getInstance().setSplitMergeRecent(saveToPathLbl.getText());
    }
}