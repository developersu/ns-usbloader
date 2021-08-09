/*
    Copyright 2019-2021 Dmitry Isaenko
     
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

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class BlockListViewController implements Initializable {

    @FXML
    private ListView<File> splitMergeListView;
    private ObservableList<File> filesList;

    private ResourceBundle resourceBundle;

    private static class FileListCell extends ListCell<File>{
        @Override
        public void updateItem(File file, boolean isEmpty){
            super.updateItem(file, isEmpty);

            if (file == null || isEmpty){
                setText(null);
                return;
            }
            String fileName = file.getName();
            setText(fileName);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
        setFilesListView();
        filesList = splitMergeListView.getItems();
    }
    private void setFilesListView(){
        splitMergeListView.setCellFactory(fileListView -> {
            ListCell<File> item = new FileListCell();
            setContextMenuToItem(item);
            return item;
        });
    }

    private <T> void setContextMenuToItem(ListCell<T> item){
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteMenuItem = new MenuItem(resourceBundle.getString("tab1_table_contextMenu_Btn_BtnDelete"));
        deleteMenuItem.setOnAction(actionEvent -> {
            filesList.remove(item.getItem());
            splitMergeListView.refresh();
        });
        MenuItem deleteAllMenuItem = new MenuItem(resourceBundle.getString("tab1_table_contextMenu_Btn_DeleteAll"));
        deleteAllMenuItem.setOnAction(actionEvent -> {
            filesList.clear();
            splitMergeListView.refresh();
        });
        contextMenu.getItems().addAll(deleteMenuItem, deleteAllMenuItem);

        item.setContextMenu(contextMenu);
    }

    public void add(File file){
        if (filesList.contains(file))
            return;
        filesList.add(file);
    }
    public void addAll(List<File> files){
        for (File file : files) {
            add(file);
        }
    }
    public ObservableList<File> getItems(){ return filesList; }
    public void clear(){
        filesList.clear();
        splitMergeListView.refresh();
    }
}
