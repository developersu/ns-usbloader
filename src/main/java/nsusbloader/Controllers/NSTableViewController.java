package nsusbloader.Controllers;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;
import nsusbloader.NSLDataTypes.FileStatus;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class NSTableViewController implements Initializable {
    @FXML
    private TableView<NSLRowModel> table;
    private ObservableList<NSLRowModel> rowsObsLst;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        rowsObsLst = FXCollections.observableArrayList();
        table.setPlaceholder(new Label());

        TableColumn<NSLRowModel, String> statusColumn = new TableColumn<>("Status");            // TODO: Localization
        TableColumn<NSLRowModel, String> fileNameColumn = new TableColumn<>("File Name");            // TODO: Localization
        TableColumn<NSLRowModel, Boolean> uploadColumn = new TableColumn<>("Upload?");            // TODO: Localization
        statusColumn.setMinWidth(70.0);
        fileNameColumn.setMinWidth(270.0);
        uploadColumn.setMinWidth(70.0);

        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("nspFileName"));
        // ><
        uploadColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<NSLRowModel, Boolean>, ObservableValue<Boolean>>() {
            @Override
            public ObservableValue<Boolean> call(TableColumn.CellDataFeatures<NSLRowModel, Boolean> paramFeatures) {
                NSLRowModel model = paramFeatures.getValue();

                SimpleBooleanProperty booleanProperty = new SimpleBooleanProperty(model.isMarkForUpload());

                booleanProperty.addListener(new ChangeListener<Boolean>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> observableValue, Boolean oldValue, Boolean newValue) {
                        model.setMarkForUpload(newValue);
                        // TODO: add reference to this general class method which will validate protocol and restict selection
                    }
                });

                return booleanProperty;
            }
        });

        uploadColumn.setCellFactory(new Callback<TableColumn<NSLRowModel, Boolean>, TableCell<NSLRowModel, Boolean>>() {
            @Override
            public TableCell<NSLRowModel, Boolean> call(TableColumn<NSLRowModel, Boolean> paramFeatures) {
                CheckBoxTableCell<NSLRowModel, Boolean> cell = new CheckBoxTableCell<>();
                return cell;
            }
        });

        table.setItems(rowsObsLst);
        table.getColumns().addAll(statusColumn, fileNameColumn, uploadColumn);

        rowsObsLst.add(new NSLRowModel(new File("/tmp/dump_file"), true));
        rowsObsLst.add(new NSLRowModel(new File("/home/loper/тяжелые будни.mp4"), false));
        rowsObsLst.add(new NSLRowModel(new File("/home/loper/стихи.txt"), false));
        rowsObsLst.add(new NSLRowModel(new File("/home/loper/стихи_2"), false));
        rowsObsLst.add(new NSLRowModel(new File("/home/loper/стихи_1"), false));
    }
    /**
     * Add files when user selected them
     * */
    public void addFiles(List<File> files, String protocol){
        rowsObsLst.clear();
        if (protocol.equals("TinFoil")){
            for (File nspFile: files){
                rowsObsLst.add(new NSLRowModel(nspFile, true));
            }
        }
        else {
            rowsObsLst.clear();
            for (File nspFile: files){
                rowsObsLst.add(new NSLRowModel(nspFile, false));
            }
            rowsObsLst.get(0).setMarkForUpload(true);
        }
    }
    /**
     * Return files ready for upload. Requested from NSLMainController only
     * @return null if no files marked for upload
     *         List<File> if there are files
     * */
    public List<File> getFiles(){
        List<File> files = new ArrayList<>();
        if (rowsObsLst.isEmpty())
            return null;
        else {
            for (NSLRowModel model: rowsObsLst){
                if (model.isMarkForUpload())
                    files.add(model.getNspFile());
            }
            if (!files.isEmpty())
                return files;
            else
                return null;
        }
    }
    /**
     * Update files in case something is wrong. Requested from UsbCommunications _OR_ PFS
     * */
    public void reportFileStatus(String fileName, FileStatus status){
        for (NSLRowModel model: rowsObsLst){
            if (model.getNspFileName().equals(fileName)){
                model.setStatus(status);
            }
        }
    }
    /**
     * Called if selected different USB protocol
     * */
    public void protocolChangeEvent(String protocol){
        if (rowsObsLst.isEmpty())
            return;
        if (protocol.equals("TinFoil")){
            for (NSLRowModel model: rowsObsLst)
                model.setMarkForUpload(true);
        }
        else {
            for (NSLRowModel model: rowsObsLst)
                model.setMarkForUpload(false);
            rowsObsLst.get(0).setMarkForUpload(true);
        }
        table.refresh();
    }
}
