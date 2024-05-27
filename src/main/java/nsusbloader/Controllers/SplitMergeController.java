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

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.FilesHelper;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.ServiceWindow;
import nsusbloader.Utilities.splitmerge.SplitMergeTaskExecutor;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class SplitMergeController implements Initializable, ISubscriber {
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
    private Label saveToPathLbl,
            statusLbl;

    @FXML
    private BlockListViewController BlockListViewController;

    private ResourceBundle resourceBundle;

    private Region convertRegion;
    private Thread smThread;
    private Runnable smTask;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;

        convertRegion = new Region();
        convertBtn.setGraphic(convertRegion);
        convertBtn.disableProperty().bind(Bindings.isEmpty(BlockListViewController.getItems()));

        splitRad.setOnAction((actionEvent -> {
            statusLbl.setText("");
            convertRegion.getStyleClass().clear();
            convertRegion.getStyleClass().add("regionSplitToOne");
            selectFileFolderBtn.setText(resourceBundle.getString("tabSplMrg_Btn_SelectFile"));
            BlockListViewController.clear();
        }));
        mergeRad.setOnAction((actionEvent -> {
            statusLbl.setText("");
            convertRegion.getStyleClass().clear();
            convertRegion.getStyleClass().add("regionOneToSplit");
            selectFileFolderBtn.setText(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));
            BlockListViewController.clear();
        }));

        if (AppPreferences.getInstance().getSplitMergeType() == 0)
            splitRad.fire();
        else
            mergeRad.fire();

        String previouslyUsedSaveToPath = AppPreferences.getInstance().getSplitMergeRecent();

        saveToPathLbl.setText(FilesHelper.getRealFolder(previouslyUsedSaveToPath));

        changeSaveToBtn.setOnAction((actionEvent -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));

            String saveToLocation = FilesHelper.getRealFolder(saveToPathLbl.getText());
            directoryChooser.setInitialDirectory(new File(saveToLocation));

            File saveToDir = directoryChooser.showDialog(changeSaveToBtn.getScene().getWindow());
            if (saveToDir != null)
                saveToPathLbl.setText(saveToDir.getAbsolutePath());
        }));

        selectFileFolderBtn.setOnAction(actionEvent -> {
            statusLbl.setText("");
            List<File> alreadyAddedFiles = BlockListViewController.getItems();
            if (splitRad.isSelected()) {
                FileChooser fc = new FileChooser();
                fc.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFile"));
                if (! alreadyAddedFiles.isEmpty()){
                    String recentLocation = FilesHelper.getRealFolder(alreadyAddedFiles.get(0).getParentFile().getAbsolutePath());
                    fc.setInitialDirectory(new File(recentLocation));
                }
                else
                    fc.setInitialDirectory(new File(System.getProperty("user.home")));
                List<File> files = fc.showOpenMultipleDialog(changeSaveToBtn.getScene().getWindow());
                if (files == null || files.isEmpty())
                    return;
                this.BlockListViewController.addAll(files);
            }
            else{
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));
                if (! alreadyAddedFiles.isEmpty()){
                    String recentLocation = FilesHelper.getRealFolder(alreadyAddedFiles.get(0).getParentFile().getAbsolutePath());
                    dc.setInitialDirectory(new File(recentLocation));
                }
                else
                    dc.setInitialDirectory(new File(System.getProperty("user.home")));

                File folderFile = dc.showDialog(changeSaveToBtn.getScene().getWindow());
                if (folderFile == null)
                    return;
                this.BlockListViewController.add(folderFile);
            }
        });

        convertBtn.setOnAction(actionEvent -> setConvertBtnAction());
    }

    /**
     * It's button listener when convert-process in progress
     * */
    private void stopBtnAction(){
        if (smThread != null && smThread.isAlive()) {
            smThread.interrupt();
        }
    }
    /**
     * It's button listener when convert-process NOT in progress
     * */
    private void setConvertBtnAction(){
        if (MediatorControl.INSTANCE.getTransferActive()) {
            ServiceWindow.getErrorNotification(
                    resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("windowBodyPleaseFinishTransfersFirst")
            );
            return;
        }

        if (splitRad.isSelected())
            smTask = new SplitMergeTaskExecutor(true, BlockListViewController.getItems(), saveToPathLbl.getText());
        else
            smTask = new SplitMergeTaskExecutor(false, BlockListViewController.getItems(), saveToPathLbl.getText());
        smThread = new Thread(smTask);
        smThread.setDaemon(true);
        smThread.start();
    }
    /**
     * Drag-n-drop support (dragOver consumer)
     * */
    @FXML
    private void handleDragOver(DragEvent event){
        if (event.getDragboard().hasFiles() && ! MediatorControl.INSTANCE.getTransferActive())
            event.acceptTransferModes(TransferMode.ANY);
        event.consume();
    }
    /**
     * Drag-n-drop support (drop consumer)
     * */
    @FXML
    private void handleDrop(DragEvent event) {
        List<File> files = event.getDragboard().getFiles();
        File firstFile = files.get(0);

        if (firstFile.isDirectory())
            mergeRad.fire();
        else
            splitRad.fire();

        this.BlockListViewController.addAll(files);

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

    @Override
    public void notify(EModule type, boolean isActive, Payload payload) {
        // todo: refactor: remove everything, place to separate container and just disable.

        if (! type.equals(EModule.SPLIT_MERGE_TOOL)){
            smToolPane.setDisable(isActive);
            return;
        }

        statusLbl.setText(payload.getMessage());

        if (isActive){
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
}