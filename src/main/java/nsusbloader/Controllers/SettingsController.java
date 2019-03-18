package nsusbloader.Controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import nsusbloader.AppPreferences;


import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    @FXML
    private CheckBox validateNSHostNameCb;
    @FXML
    private TextField pcIpTextField;
    @FXML
    private CheckBox expertModeCb;
    @FXML
    private Label hostIpLbl;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        validateNSHostNameCb.setSelected(AppPreferences.getInstance().getNsIpValidationNeeded());

        if (AppPreferences.getInstance().getExpertMode()) {
            expertModeCb.setSelected(true);
            hostIpLbl.setVisible(true);
            pcIpTextField.setVisible(true);
        }
        expertModeCb.setOnAction(e->{
            if (expertModeCb.isSelected()){
                hostIpLbl.setVisible(true);
                pcIpTextField.setVisible(true);
            }
            else {
                hostIpLbl.setVisible(false);
                pcIpTextField.setVisible(false);
            }
        });
    }

    public boolean getExpertModeSelected(){
        return expertModeCb.isSelected();
    }
    public boolean isNsIpValidate(){ return validateNSHostNameCb.isSelected(); }
}
