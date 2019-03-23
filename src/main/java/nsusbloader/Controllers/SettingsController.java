package nsusbloader.Controllers;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import nsusbloader.AppPreferences;
import nsusbloader.ServiceWindow;
import nsusbloader.ModelControllers.UpdatesChecker;

import java.net.URL;
import java.util.List;
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
    private TextField pcExtraTextField;

    @FXML
    private CheckBox dontServeCb;

    @FXML
    private VBox expertSettingsVBox;

    @FXML
    private CheckBox autoCheckUpdCb;
    @FXML
    private Hyperlink newVersionLink;
    @FXML
    private Button checkForUpdBtn;

    private HostServices hs;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        validateNSHostNameCb.setSelected(AppPreferences.getInstance().getNsIpValidationNeeded());

        expertSettingsVBox.setDisable(!AppPreferences.getInstance().getExpertMode());

        expertModeCb.setSelected(AppPreferences.getInstance().getExpertMode());
        expertModeCb.setOnAction(e->{
                expertSettingsVBox.setDisable(!expertModeCb.isSelected());
        });

        autoDetectIpCb.setSelected(AppPreferences.getInstance().getAutoDetectIp());
        pcIpTextField.setDisable(AppPreferences.getInstance().getAutoDetectIp());
        autoDetectIpCb.setOnAction(e->{
            pcIpTextField.setDisable(autoDetectIpCb.isSelected());
            if (!autoDetectIpCb.isSelected())
                pcIpTextField.requestFocus();
        });

        randPortCb.setSelected(AppPreferences.getInstance().getRandPort());
        pcPortTextField.setDisable(AppPreferences.getInstance().getRandPort());
        randPortCb.setOnAction(e->{
            pcPortTextField.setDisable(randPortCb.isSelected());
            if (!randPortCb.isSelected())
                pcPortTextField.requestFocus();
        });

        if (AppPreferences.getInstance().getNotServeRequests()){
            dontServeCb.setSelected(true);

            autoDetectIpCb.setSelected(false);
            autoDetectIpCb.setDisable(true);
            pcIpTextField.setDisable(false);

            randPortCb.setSelected(false);
            randPortCb.setDisable(true);
            pcPortTextField.setDisable(false);
        }
        pcExtraTextField.setDisable(!AppPreferences.getInstance().getNotServeRequests());

        dontServeCb.setOnAction(e->{
            if (dontServeCb.isSelected()){
                autoDetectIpCb.setSelected(false);
                autoDetectIpCb.setDisable(true);
                pcIpTextField.setDisable(false);

                randPortCb.setSelected(false);
                randPortCb.setDisable(true);
                pcPortTextField.setDisable(false);

                pcExtraTextField.setDisable(false);
                pcIpTextField.requestFocus();
            }
            else {
                autoDetectIpCb.setDisable(false);
                autoDetectIpCb.setSelected(true);
                pcIpTextField.setDisable(true);

                randPortCb.setDisable(false);
                randPortCb.setSelected(true);
                pcPortTextField.setDisable(true);

                pcExtraTextField.setDisable(true);
            }
        });

        pcIpTextField.setText(AppPreferences.getInstance().getHostIp());
        pcPortTextField.setText(AppPreferences.getInstance().getHostPort());
        pcExtraTextField.setText(AppPreferences.getInstance().getHostExtra());

        pcIpTextField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().contains(" ") | change.getControlNewText().contains("\t"))
                return null;
            else
                return change;
        }));
        pcPortTextField.setTextFormatter(new TextFormatter<Object>(change -> {
            if (change.getControlNewText().matches("^[0-9]{0,5}$")) {
                if (!change.getControlNewText().isEmpty()
                        && ((Integer.parseInt(change.getControlNewText()) > 65535) || (Integer.parseInt(change.getControlNewText()) == 0))
                ) {
                    ServiceWindow.getErrorNotification(resourceBundle.getString("windowTitleErrorPort"), resourceBundle.getString("windowBodyErrorPort"));
                    return null;
                }
                return change;
            }
            else
                return null;
        }));
        pcExtraTextField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().contains(" ") | change.getControlNewText().contains("\t"))
                return null;
            else
                return change;
        }));

        newVersionLink.setVisible(false);
        newVersionLink.setOnAction(e->{
            hs.showDocument(newVersionLink.getText());
        });

        autoCheckUpdCb.setSelected(AppPreferences.getInstance().getAutoCheckUpdates());

        Region btnSwitchImage = new Region();
        btnSwitchImage.getStyleClass().add("regionUpdatesCheck");
        checkForUpdBtn.setGraphic(btnSwitchImage);

        checkForUpdBtn.setOnAction(e->{
            Task<List<String>> updTask = new UpdatesChecker();
            updTask.setOnSucceeded(event->{
                List<String> result = updTask.getValue();
                if (result != null){
                    if (result.get(0).isEmpty()){
                        ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionNOTAval"), resourceBundle.getString("windowBodyNewVersionNOTAval"));
                    }
                    else {
                        setNewVersionLink(result.get(0));
                        ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionAval"), resourceBundle.getString("windowTitleNewVersionAval")+": "+result.get(0) + "\n\n" + result.get(1));
                    }
                }
                else {
                    ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionUnknown"), resourceBundle.getString("windowBodyNewVersionUnknown"));
                }
            });
            Thread updates = new Thread(updTask);
            updates.setDaemon(true);
            updates.start();
        });
    }

    public boolean getExpertModeSelected(){ return expertModeCb.isSelected(); }
    public boolean getAutoIpSelected(){ return autoDetectIpCb.isSelected(); }
    public boolean getRandPortSelected(){ return randPortCb.isSelected(); }
    public boolean getNotServeSelected(){ return dontServeCb.isSelected(); }

    public boolean isNsIpValidate(){ return validateNSHostNameCb.isSelected(); }

    public String getHostIp(){ return pcIpTextField.getText(); }
    public String getHostPort(){ return pcPortTextField.getText(); }
    public String getHostExtra(){ return pcExtraTextField.getText(); }
    public boolean getAutoCheckForUpdates(){ return autoCheckUpdCb.isSelected(); }

    public void registerHostServices(HostServices hostServices){this.hs = hostServices;}

    public void setNewVersionLink(String newVer){
        newVersionLink.setVisible(true);
        newVersionLink.setText("https://github.com/developersu/ns-usbloader/releases/tag/"+newVer);
    }
}