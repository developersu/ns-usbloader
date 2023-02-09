/*
    Copyright 2018-2022 Dmitry Isaenko
     
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
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.FilesHelper;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.ServiceWindow;
import nsusbloader.Utilities.patches.es.EsPatchMaker;
import nsusbloader.Utilities.patches.fs.FsPatchMaker;
import nsusbloader.Utilities.patches.loader.LoaderPatchMaker;

// TODO: CLI SUPPORT
public class PatchesController implements Initializable {
    @FXML
    private VBox patchesToolPane;
    @FXML
    private Button makeEsBtn, makeFsBtn, makeLoaderBtn;
    @FXML
    private Label shortNameFirmwareLbl, locationFirmwareLbl, saveToLbl, shortNameKeysLbl, locationKeysLbl, statusLbl,
        locationAtmosphereLbl, shortNameAtmoLbl;
    private Thread workThread;

    private String previouslyOpenedPath;
    private ResourceBundle resourceBundle;
    private Region convertRegionEs;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
        this.previouslyOpenedPath = System.getProperty("user.home");

        String myRegexp;
        if (File.separator.equals("/"))
            myRegexp = "^.+/";
        else
            myRegexp = "^.+\\\\";
        locationFirmwareLbl.textProperty().addListener((observableValue, currentText, updatedText) ->
                shortNameFirmwareLbl.setText(updatedText.replaceAll(myRegexp, "")));

        locationKeysLbl.textProperty().addListener((observableValue, currentText, updatedText) ->
                shortNameKeysLbl.setText(updatedText.replaceAll(myRegexp, "")));

        locationAtmosphereLbl.textProperty().addListener((observableValue, currentText, updatedText) ->
                shortNameAtmoLbl.setText(updatedText.replaceAll(myRegexp, "")));

        convertRegionEs = createCakeRegion();
        makeEsBtn.setGraphic(convertRegionEs);

        makeFsBtn.setGraphic(createCakeRegion());
        makeLoaderBtn.setGraphic(createCakeRegion());

        AppPreferences preferences = AppPreferences.getInstance();
        String keysLocation = preferences.getKeysLocation();
        File keysFile = new File(keysLocation);

        if (keysFile.exists() && keysFile.isFile()) {
            locationKeysLbl.setText(keysLocation);
        }

        saveToLbl.setText(preferences.getPatchesSaveToLocation());
        makeEsBtn.disableProperty().bind(Bindings.or(
                Bindings.isEmpty(locationFirmwareLbl.textProperty()),
                Bindings.isEmpty(locationKeysLbl.textProperty())));
        makeEsBtn.setOnAction(actionEvent -> makeEs());

        makeFsBtn.disableProperty().bind(Bindings.or(
                Bindings.isEmpty(locationFirmwareLbl.textProperty()),
                Bindings.isEmpty(locationKeysLbl.textProperty())));
        makeFsBtn.setOnAction(actionEvent -> makeFs());

        makeLoaderBtn.disableProperty().bind(Bindings.isEmpty(locationAtmosphereLbl.textProperty()));
        makeLoaderBtn.setOnAction(actionEvent -> makeLoader());
    }
    private Region createCakeRegion(){
        Region cakeRegion = new Region();
        cakeRegion.getStyleClass().add("regionCake");
        return cakeRegion;
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
        List<File> filesDropped = event.getDragboard().getFiles();
        for (File file : filesDropped){
            if (file.isDirectory()) {
                if (file.getName().toLowerCase().contains("atmosphe"))
                    locationAtmosphereLbl.setText(file.getAbsolutePath());
                else
                    locationFirmwareLbl.setText(file.getAbsolutePath());
                continue;
            }
            String fileName = file.getName().toLowerCase();
            if ((fileName.endsWith(".dat")) ||
                    (fileName.endsWith(".keys") &&
                    ! fileName.equals("dev.keys") &&
                    ! fileName.equals("title.keys")))
                locationKeysLbl.setText(file.getAbsolutePath());
            else if (fileName.equals("offsets.txt"))
                setOffsets(file);
        }
        event.setDropCompleted(true);
        event.consume();
    }

    private void setOffsets(File fileWithOffsets){
        AppPreferences preferences = AppPreferences.getInstance();
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(fileWithOffsets))) {
            String fileLine;
            String[] lineValues;
            while ((fileLine = reader.readLine()) != null) {
                if (fileLine.startsWith("#"))
                    continue;
                lineValues = fileLine.trim().split("\\s+?=\\s+?", 2);
                if (lineValues.length == 2) {
                    String[] pointer = lineValues[0].split("_", 3);
                    if (! pointer[0].equals("ES") && ! pointer[0].equals("FS"))
                        continue;
                    if (! pointer[1].matches("^([0-9A-Fa-f]{2})$"))
                        continue;
                    if (! pointer[2].matches("^([0-9A-Fa-f]{2})$"))
                        continue;
                    if (! lineValues[1].matches("^(([0-9A-Fa-f]{2})|\\.)+?$"))
                        continue;
                    preferences.setPatchOffset(lineValues[0], lineValues[1]);

                    System.out.println(pointer[0]+"_"+pointer[1]+"_"+pointer[2]+" = "+lineValues[1]);
                    count++;
                    statusLbl.setText("OK "+count);
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    @FXML
    private void selectFirmware(){
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(resourceBundle.getString("tabPatches_Lbl_Firmware"));
        directoryChooser.setInitialDirectory(new File(FilesHelper.getRealFolder(previouslyOpenedPath)));
        File firmware = directoryChooser.showDialog(patchesToolPane.getScene().getWindow());
        if (firmware == null)
            return;
        locationFirmwareLbl.setText(firmware.getAbsolutePath());
        previouslyOpenedPath = firmware.getParent();
    }
    @FXML
    private void selectAtmosphereFolder(){
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(resourceBundle.getString("tabPatches_Lbl_Atmo"));
        directoryChooser.setInitialDirectory(new File(FilesHelper.getRealFolder(previouslyOpenedPath)));
        File firmware = directoryChooser.showDialog(patchesToolPane.getScene().getWindow());
        if (firmware == null)
            return;
        locationAtmosphereLbl.setText(firmware.getAbsolutePath());
        previouslyOpenedPath = firmware.getParent();
    }
    @FXML
    private void selectSaveTo(){
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(resourceBundle.getString("tabSplMrg_Btn_SelectFolder"));
        directoryChooser.setInitialDirectory(new File(FilesHelper.getRealFolder(previouslyOpenedPath)));
        File saveToDir = directoryChooser.showDialog(patchesToolPane.getScene().getWindow());
        if (saveToDir == null)
            return;
        saveToLbl.setText(saveToDir.getAbsolutePath());
    }
    @FXML
    private void selectProdKeys(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(resourceBundle.getString("tabPatches_Lbl_Keys"));
        fileChooser.setInitialDirectory(new File(FilesHelper.getRealFolder(previouslyOpenedPath)));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("keys", "*.dat", "*.keys"));
        File keys = fileChooser.showOpenDialog(patchesToolPane.getScene().getWindow());
        if (keys == null || ! keys.exists())
            return;

        locationKeysLbl.setText(keys.getAbsolutePath());
        previouslyOpenedPath = keys.getParent();
    }

    private void makeEs(){
        if (locationFirmwareLbl.getText().isEmpty() || locationKeysLbl.getText().isEmpty()){
            ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("tabPatches_ServiceWindowMessageEsFs"));
            return;
        }

        if (workThread != null && workThread.isAlive())
            return;
        statusLbl.setText("");

        if (MediatorControl.getInstance().getTransferActive()) {
            ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("windowBodyPleaseStopOtherProcessFirst"));
            return;
        }

        EsPatchMaker esPatchMaker = new EsPatchMaker(locationFirmwareLbl.getText(), locationKeysLbl.getText(),
                saveToLbl.getText());
        workThread = new Thread(esPatchMaker);

        workThread.setDaemon(true);
        workThread.start();
    }
    private void makeFs(){
        if (locationFirmwareLbl.getText().isEmpty() || locationKeysLbl.getText().isEmpty()){
            ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("tabPatches_ServiceWindowMessageEsFs"));
            return;
        }

        if (workThread != null && workThread.isAlive())
            return;
        statusLbl.setText("");

        if (MediatorControl.getInstance().getTransferActive()) {
            ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("windowBodyPleaseStopOtherProcessFirst"));
            return;
        }

        FsPatchMaker fsPatchMaker = new FsPatchMaker(locationFirmwareLbl.getText(), locationKeysLbl.getText(),
                saveToLbl.getText());
        workThread = new Thread(fsPatchMaker);

        workThread.setDaemon(true);
        workThread.start();
    }
    private void makeLoader(){
        if (locationAtmosphereLbl.getText().isEmpty()){
            ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("tabPatches_ServiceWindowMessageLoader"));
            return;
        }

        if (workThread != null && workThread.isAlive())
            return;
        statusLbl.setText("");

        if (MediatorControl.getInstance().getTransferActive()) {
            ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleError"),
                    resourceBundle.getString("windowBodyPleaseStopOtherProcessFirst"));
            return;
        }

        LoaderPatchMaker loaderPatchMaker = new LoaderPatchMaker(locationAtmosphereLbl.getText(), saveToLbl.getText());
        workThread = new Thread(loaderPatchMaker);

        workThread.setDaemon(true);
        workThread.start();
    }
    private void interruptProcessOfPatchMaking(){
        if (workThread == null || ! workThread.isAlive())
            return;

        workThread.interrupt();
    }

    public void notifyThreadStarted(boolean isActive, EModule type) {
        if (! type.equals(EModule.PATCHES)) {
            patchesToolPane.setDisable(isActive);
            return;
        }

        convertRegionEs.getStyleClass().clear();
        makeFsBtn.setVisible(! isActive);
        makeLoaderBtn.setVisible(! isActive);

        if (isActive) {
            MediatorControl.getInstance().getContoller().logArea.clear();
            convertRegionEs.getStyleClass().add("regionStop");

            makeEsBtn.setOnAction(e-> interruptProcessOfPatchMaking());
            makeEsBtn.setText(resourceBundle.getString("btn_Stop"));
            makeEsBtn.getStyleClass().remove("buttonUp");
            makeEsBtn.getStyleClass().add("buttonStop");
            return;
        }
        convertRegionEs.getStyleClass().add("regionCake");

        makeEsBtn.setOnAction(actionEvent -> makeEs());
        makeEsBtn.setText(resourceBundle.getString("tabPatches_Btn_MakeEs"));
        makeEsBtn.getStyleClass().remove("buttonStop");
        makeEsBtn.getStyleClass().add("buttonUp");
    }

    public void setOneLineStatus(boolean statusSuccess){
        if (statusSuccess)
            statusLbl.setText(resourceBundle.getString("done_txt"));
        else
            statusLbl.setText(resourceBundle.getString("failure_txt"));
    }

    void updatePreferencesOnExit(){
        AppPreferences.getInstance().setPatchesSaveToLocation(saveToLbl.getText());
        if (locationKeysLbl.getText().isEmpty())
            return;
        AppPreferences.getInstance().setKeysLocation(locationKeysLbl.getText());
    }

}