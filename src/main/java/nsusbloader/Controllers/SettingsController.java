package nsusbloader.Controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import nsusbloader.AppPreferences;


import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    @FXML
    private CheckBox validateNSHostNameCb;
    @FXML
    private CheckBox expertModeCb;
    @FXML
    private CheckBox autoDetectIpCb;
    @FXML
    private CheckBox randPortCb;

    @FXML
    private TextField pcIpTextField;
    @FXML
    private TextField pcPortTextField;
    @FXML
    private TextField pcPostfixTextField;

    @FXML
    private CheckBox dontServeCb;

    @FXML
    private VBox expertSettingsVBox;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        validateNSHostNameCb.setSelected(AppPreferences.getInstance().getNsIpValidationNeeded());

        expertSettingsVBox.setDisable(AppPreferences.getInstance().getExpertMode());

        expertModeCb.setOnAction(e->{
            if (expertModeCb.isSelected())
                expertSettingsVBox.setDisable(false);
            else
                expertSettingsVBox.setDisable(true);
        });
    }

    public boolean getExpertModeSelected(){
        return expertModeCb.isSelected();
    }
    public boolean isNsIpValidate(){ return validateNSHostNameCb.isSelected(); }
}
