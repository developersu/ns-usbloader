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
import nsusbloader.NSLDataTypes.EFileStatus;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class NSTableViewController implements Initializable {
    @FXML
    private TableView<NSLRowModel> table;
    private ObservableList<NSLRowModel> rowsObsLst;

    private String protocol;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        rowsObsLst = FXCollections.observableArrayList();
        table.setPlaceholder(new Label());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<NSLRowModel, String> statusColumn = new TableColumn<>(resourceBundle.getString("tableStatusLbl"));
        TableColumn<NSLRowModel, String> fileNameColumn = new TableColumn<>(resourceBundle.getString("tableFileNameLbl"));
        TableColumn<NSLRowModel, String> fileSizeColumn = new TableColumn<>(resourceBundle.getString("tableSizeLbl"));
        TableColumn<NSLRowModel, Boolean> uploadColumn = new TableColumn<>(resourceBundle.getString("tableUploadLbl"));
        // See https://bugs.openjdk.java.net/browse/JDK-8157687
        statusColumn.setMinWidth(100.0);
        statusColumn.setPrefWidth(100.0);
        statusColumn.setMaxWidth(100.0);
        statusColumn.setResizable(false);

        fileNameColumn.setMinWidth(25.0);

        fileSizeColumn.setMinWidth(120.0);
        fileSizeColumn.setPrefWidth(120.0);
        fileSizeColumn.setMaxWidth(120.0);
        fileSizeColumn.setResizable(false);

        uploadColumn.setMinWidth(100.0);
        uploadColumn.setPrefWidth(100.0);
        uploadColumn.setMaxWidth(100.0);
        uploadColumn.setResizable(false);

        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("nspFileName"));
        fileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("nspFileSize"));
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
                        restrictSelection(model);
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
        table.getColumns().addAll(statusColumn, fileNameColumn, fileSizeColumn, uploadColumn);
    }
    /**
     * See uploadColumn callback. In case of GoldLeaf we have to restrict selection
     * */
    private void restrictSelection(NSLRowModel modelChecked){
        if (!protocol.equals("TinFoil") && rowsObsLst.size() > 1) {       // Tinfoil doesn't need any restrictions. If only one file in list, also useless
            for (NSLRowModel model: rowsObsLst){
                if (model != modelChecked)
                    model.setMarkForUpload(false);
            }
            table.refresh();
        }
    }
    /**
     * Add files when user selected them
     * */
    public void setFiles(List<File> files){
        rowsObsLst.clear();                 // TODO: consider table refresh
        if (files == null) {
            return;
        }
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
     * Update files in case something is wrong. Requested from UsbCommunications
     * */
    public void setFileStatus(String fileName, EFileStatus status){
        for (NSLRowModel model: rowsObsLst){
            if (model.getNspFileName().equals(fileName)){
                model.setStatus(status);
            }
        }
        table.refresh();
    }
    /**
     * Called if selected different USB protocol
     * */
    public void setNewProtocol(String newProtocol){
        protocol = newProtocol;
        if (rowsObsLst.isEmpty())
            return;
        if (newProtocol.equals("TinFoil")){
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
