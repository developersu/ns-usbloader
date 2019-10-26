package nsusbloader.Controllers;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EFileStatus;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
        table.setEditable(false);               // At least with hacks it works as expected. Otherwise - null pointer exception
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setOnKeyPressed(keyEvent -> {
                if (!rowsObsLst.isEmpty()) {
                    if (keyEvent.getCode() == KeyCode.DELETE && !MediatorControl.getInstance().getTransferActive()) {
                        rowsObsLst.removeAll(table.getSelectionModel().getSelectedItems());
                        if (rowsObsLst.isEmpty())
                            MediatorControl.getInstance().getContoller().disableUploadStopBtn(true);    // TODO: change to something better
                        table.refresh();
                    } else if (keyEvent.getCode() == KeyCode.SPACE) {
                        for (NSLRowModel item : table.getSelectionModel().getSelectedItems()) {
                            item.setMarkForUpload(!item.isMarkForUpload());
                        }
                        table.refresh();
                    }
                }
                keyEvent.consume();
        });

        TableColumn<NSLRowModel, String> statusColumn = new TableColumn<>(resourceBundle.getString("tab1_table_Lbl_Status"));
        TableColumn<NSLRowModel, String> fileNameColumn = new TableColumn<>(resourceBundle.getString("tab1_table_Lbl_FileName"));
        TableColumn<NSLRowModel, Long> fileSizeColumn = new TableColumn<>(resourceBundle.getString("tab1_table_Lbl_Size"));
        TableColumn<NSLRowModel, Boolean> uploadColumn = new TableColumn<>(resourceBundle.getString("tab1_table_Lbl_Upload"));

        statusColumn.setEditable(false);
        fileNameColumn.setEditable(false);
        fileSizeColumn.setEditable(false);
        uploadColumn.setEditable(true);

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
        uploadColumn.setCellValueFactory(paramFeatures -> {
            NSLRowModel model = paramFeatures.getValue();

            SimpleBooleanProperty booleanProperty = new SimpleBooleanProperty(model.isMarkForUpload());

            booleanProperty.addListener((observableValue, oldValue, newValue) -> model.setMarkForUpload(newValue));
            return booleanProperty;
        });

        uploadColumn.setCellFactory(paramFeatures -> new CheckBoxTableCell<>());
        fileSizeColumn.setCellFactory(col -> new TableCell<NSLRowModel, Long>() {
            @Override
            protected void updateItem(Long length, boolean empty) {
                if (length == null || empty) {
                    setText("");
                }
                else {
                    setText(formatByteSize(length));
                }
            }
        });
        table.setRowFactory(        // this shit is made to implement context menu. It's such a pain..
                nslRowModelTableView -> {
                    final TableRow<NSLRowModel> row = new TableRow<>();
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem deleteMenuItem = new MenuItem(resourceBundle.getString("tab1_table_contextMenu_Btn_BtnDelete"));
                    deleteMenuItem.setOnAction(actionEvent -> {
                        rowsObsLst.remove(row.getItem());
                        if (rowsObsLst.isEmpty())
                            MediatorControl.getInstance().getContoller().disableUploadStopBtn(true);    // TODO: change to something better
                        table.refresh();
                    });
                    MenuItem deleteAllMenuItem = new MenuItem(resourceBundle.getString("tab1_table_contextMenu_Btn_DeleteAll"));
                    deleteAllMenuItem.setOnAction(actionEvent -> {
                        rowsObsLst.clear();
                        MediatorControl.getInstance().getContoller().disableUploadStopBtn(true);    // TODO: change to something better
                        table.refresh();
                    });
                    contextMenu.getItems().addAll(deleteMenuItem, deleteAllMenuItem);

                    row.setContextMenu(contextMenu);
                    row.contextMenuProperty().bind(
                            Bindings.when(
                                    Bindings.isNotNull(
                                            row.itemProperty()))
                                            .then(MediatorControl.getInstance().getTransferActive()?null:contextMenu)
                                            .otherwise((ContextMenu) null)
                    );
                    // Just.. don't ask..
                    row.setOnMouseClicked(mouseEvent -> {
                        if (!row.isEmpty() && mouseEvent.getButton() == MouseButton.PRIMARY){
                            NSLRowModel thisItem = row.getItem();
                            thisItem.setMarkForUpload(!thisItem.isMarkForUpload());
                            table.refresh();
                        }
                        mouseEvent.consume();
                    });
                    return row;
                }
        );
        table.setItems(rowsObsLst);
        table.getColumns().add(statusColumn);
        table.getColumns().add(fileNameColumn);
        table.getColumns().add(fileSizeColumn);
        table.getColumns().add(uploadColumn);
    }
    /**
     * Add single file when user selected it (Split file usually)
     * */
    public void setFile(File file){
        if ( ! rowsObsLst.isEmpty()){
            List<String> filesAlreayInList = new ArrayList<>();
            for (NSLRowModel model : rowsObsLst)
                filesAlreayInList.add(model.getNspFileName());

            if ( ! filesAlreayInList.contains(file.getName()))
                rowsObsLst.add(new NSLRowModel(file, true));
        }
        else {
            rowsObsLst.add(new NSLRowModel(file, true));
            MediatorControl.getInstance().getContoller().disableUploadStopBtn(false);
        }
        table.refresh();
    }
    /**
     * Add files when user selected them
     * */
    public void setFiles(List<File> newFiles){
        if (!rowsObsLst.isEmpty()){
            List<String> filesAlreayInList = new ArrayList<>();
            for (NSLRowModel model : rowsObsLst)
                    filesAlreayInList.add(model.getNspFileName());
            for (File file: newFiles)
                if (!filesAlreayInList.contains(file.getName())) {
                    rowsObsLst.add(new NSLRowModel(file, true));
                }
        }
        else {
            for (File file: newFiles)
                rowsObsLst.add(new NSLRowModel(file, true));
            MediatorControl.getInstance().getContoller().disableUploadStopBtn(false);
        }
        //rowsObsLst.get(0).setMarkForUpload(true);
        table.refresh();
    }
    /**
     * Return files ready for upload. Requested from NSLMainController only -> uploadBtnAction()                            //TODO: set undefined
     * @return null if no files marked for upload
     *         List<File> if there are files
     * */
    public List<File> getFilesForUpload(){
        List<File> files = new ArrayList<>();
        if (rowsObsLst.isEmpty())
            return null;
        else {
            for (NSLRowModel model: rowsObsLst){
                if (model.isMarkForUpload()){
                    files.add(model.getNspFile());
                    model.setStatus(EFileStatus.INDETERMINATE);
                }
            }
            if (!files.isEmpty()) {
                table.refresh();
                return files;
            }
            else
                return null;
        }
    }
    public boolean isFilesForUploadListEmpty(){
        return rowsObsLst.isEmpty();
    }
    /**
     * Update files in case something is wrong. Requested from UsbCommunications
     * */
    public void setFileStatus(String fileName, EFileStatus status){
        for (NSLRowModel model: rowsObsLst){
            if (model.getNspFileName().equals(fileName))
                model.setStatus(status);
        }
        table.refresh();
    }
    /**
     * Called if selected different USB protocol
     * */
    public void setNewProtocol(String newProtocol){
        if (rowsObsLst.isEmpty())
            return;
        if (newProtocol.equals("GoldLeaf")) {
            rowsObsLst.removeIf(current -> current.getNspFileName().toLowerCase().endsWith("xci") ||
                    current.getNspFileName().toLowerCase().endsWith("nsz") ||
                    current.getNspFileName().toLowerCase().endsWith("xcz"));
        }
        else
            rowsObsLst.removeIf(current -> ! current.getNspFileName().toLowerCase().endsWith("nsp"));
        table.refresh();
    }
    /**
     * Used for showing in 'Size' column size representation in human-readable format
     * */
    private String formatByteSize(double length) {
        final String[] unitNames = { "bytes", "KiB", "MiB", "GiB", "TiB"};
        int i;
        for (i = 0; length > 1024 && i < unitNames.length - 1; i++) {
            length = length / 1024;
        }
      return String.format("%,.2f %s", length, unitNames[i]);
    }

}