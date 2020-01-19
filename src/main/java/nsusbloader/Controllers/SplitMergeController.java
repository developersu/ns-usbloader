package nsusbloader.Controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ToggleGroup;

import java.net.URL;
import java.util.ResourceBundle;

public class SplitMergeController implements Initializable {
    @FXML
    private ToggleGroup splitMergeTogGrp;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        splitMergeTogGrp.selectToggle(splitMergeTogGrp.getToggles().get(0));
    }
}