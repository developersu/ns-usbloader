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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;

import java.net.URL;
import java.util.ResourceBundle;

public class FontSettingsController implements Initializable {
    private final AppPreferences preferences = AppPreferences.getInstance();

    @FXML
    private Button applyBtn, cancelBtn, resetBtn;

    @FXML
    private ListView<String> fontsLv;

    @FXML
    private Spinner<Double> fontSizeSpinner;

    @FXML
    private Text exampleText;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        applyBtn.setDefaultButton(true);
        applyBtn.getStyleClass().add("buttonUp");
        applyBtn.setOnAction(e -> applyChanges());

        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> closeWindow());

        resetBtn.setOnAction(e -> reset());

        fontsLv.setCellFactory(item -> getCellFactory());
        fontsLv.setItems(getFonts());
        fontsLv.getSelectionModel().select(preferences.getFontFamily());
        fontsLv.getSelectionModel().selectedIndexProperty().addListener(
                (observableValue, oldValueNumber, newValueNumber) -> setExampleTextFont());
        fontsLv.setFixedCellSize(40.0);

        fontSizeSpinner.setEditable(false);
        fontSizeSpinner.setValueFactory(getValueFactory());

        exampleText.setText(resourceBundle.getString("fontPreviewText"));

        fontSizeSpinner.getValueFactory().setValue(preferences.getFontSize());
    }

    private ListCell<String> getCellFactory(){
        return new ListCell<>(){
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null)
                    return;
                Font font = Font.font(item);
                Text itemText = new Text(item);
                itemText.setFont(font);
                setGraphic(itemText);
            }
        };
    }

    private ObservableList<String> getFonts(){
        ObservableList<String> fonts = FXCollections.observableArrayList();
        fonts.addAll(Font.getFamilies());

        return fonts;
    }

    private SpinnerValueFactory<Double> getValueFactory(){
        return new SpinnerValueFactory<>() {
            @Override
            public void decrement(int i) {
                double value = getValue() - i;
                if (value < 4)
                    return;

                setValue(value);
                setExampleTextFont(value);
            }

            @Override
            public void increment(int i) {
                double value = getValue() + i;
                if (value > 100)
                    return;

                setValue(value);
                setExampleTextFont(value);
            }
        };
    }

    private void setExampleTextFont(){
        setExampleTextFont(fontsLv.getSelectionModel().getSelectedItem(), fontSizeSpinner.getValue());
    }
    private void setExampleTextFont(double size){
        setExampleTextFont(fontsLv.getSelectionModel().getSelectedItem(), size);
    }
    private void setExampleTextFont(String font, double size){
        exampleText.setFont(Font.font(font, size));
    }

    private void reset(){
        final Font defaultFont = Font.getDefault();
        exampleText.setFont(defaultFont);

        fontsLv.getSelectionModel().select(defaultFont.getFamily());
        fontSizeSpinner.getValueFactory().setValue(defaultFont.getSize());
    }

    private void applyChanges(){
        final String fontFamily = fontsLv.getSelectionModel().getSelectedItem();
        final double fontSize = fontSizeSpinner.getValue().intValue();

        preferences.setFontStyle(fontFamily, fontSize);

        MediatorControl.INSTANCE.getLogArea().getScene().getRoot().setStyle(
                String.format("-fx-font-family: \"%s\"; -fx-font-size: %.0f;", fontFamily, fontSize));

        closeWindow();
    }

    private void closeWindow(){
        ((Stage) cancelBtn.getScene().getWindow()).close();
    }
}
