package nsusbloader.Controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;
import nsusbloader.ServiceWindow;
import nsusbloader.Utilities.SplitMergeTool;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class SplitMergeController implements Initializable {
    @FXML
    private ToggleGroup splitMergeTogGrp;
    @FXML
    private RadioButton splitRad, mergeRad;
    @FXML
    private Button selectFileFolderBtn,
            changeSaveToBtn,
            convertBtn;
    @FXML
    private Label fileFolderLabelLbl,
            fileFolderActualPathLbl,
            saveToPathLbl;

    private Region convertRegion;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        convertRegion = new Region();
        convertBtn.setGraphic(convertRegion);

        splitRad.setOnAction((actionEvent -> {
            convertRegion.getStyleClass().clear();
            convertRegion.getStyleClass().add("regionSplitToOne");
            fileFolderLabelLbl.setText(resourceBundle.getString("tabSplMrg_Txt_File"));
            selectFileFolderBtn.setText(resourceBundle.getString("tabSplMrg_Btn_SelectFile"));
            fileFolderActualPathLbl.setText("");
            convertBtn.setDisable(true);
        }));
        mergeRad.setOnAction((actionEvent -> {
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
            if (splitRad.isSelected()) {
                FileChooser fc = new FileChooser();
                fc.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFile"));
                if (fileFolderActualPathLbl.getText().isEmpty())
                    fc.setInitialDirectory(new File(System.getProperty("user.home")));
                else
                    fc.setInitialDirectory(new File(fileFolderActualPathLbl.getText()).getParentFile());
                File fileFile = fc.showOpenDialog(changeSaveToBtn.getScene().getWindow());
                if (fileFile == null)
                    return;
                fileFolderActualPathLbl.setText(fileFile.getAbsolutePath());
                convertBtn.setDisable(false);
            }
            else{
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));
                if (fileFolderActualPathLbl.getText().isEmpty())
                    dc.setInitialDirectory(new File(System.getProperty("user.home")));
                else
                    dc.setInitialDirectory(new File(fileFolderActualPathLbl.getText()));
                File folderFile = dc.showDialog(changeSaveToBtn.getScene().getWindow());
                if (folderFile == null)
                    return;
                fileFolderActualPathLbl.setText(folderFile.getAbsolutePath());
                convertBtn.setDisable(false);
            }
        });

        convertBtn.setOnAction(actionEvent -> {
            if (MediatorControl.getInstance().getTransferActive()) {
                ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleError"), resourceBundle.getString("windowBodyPleaseFinishTransfersFirst"));
                return;
            }
            MediatorControl.getInstance().setTransferActive(true);
            /*
            if (splitRad.isSelected()){
                SplitMergeTool.splitFile(fileFolderActualPathLbl.getText(), saveToPathLbl.getText());
                System.out.println("split");
            }
            else{
                System.out.println("merge");
                SplitMergeTool.mergeFile(fileFolderActualPathLbl.getText(), saveToPathLbl.getText());
            }
            */
        });
    }

    public void updatePreferencesOnExit(){
        if (splitRad.isSelected())
            AppPreferences.getInstance().setSplitMergeType(0);
        else
            AppPreferences.getInstance().setSplitMergeType(1);
        AppPreferences.getInstance().setSplitMergeRecent(saveToPathLbl.getText());
    }
}