package nsusbloader.Controllers;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import nsusbloader.AppPreferences;
import nsusbloader.MediatorControl;
import nsusbloader.NSLDataTypes.EModule;
import nsusbloader.ServiceWindow;
import nsusbloader.Utilities.RcmTask;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class RcmController implements Initializable {
    @FXML
    private ToggleGroup rcmToggleGrp;

    @FXML
    private VBox rcmToolPane;

    @FXML
    private RadioButton pldrRadio1,
            pldrRadio2,
            pldrRadio3,
            pldrRadio4,
            pldrRadio5;

    @FXML
    private Button injectPldBtn;

    @FXML
    private Label payloadFNameLbl1, payloadFPathLbl1,
        payloadFNameLbl2, payloadFPathLbl2,
        payloadFNameLbl3, payloadFPathLbl3,
        payloadFNameLbl4, payloadFPathLbl4,
        payloadFNameLbl5, payloadFPathLbl5;

    @FXML
    private Label statusLbl;

    private ResourceBundle rb;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.rb = resourceBundle;
        rcmToggleGrp.selectToggle(pldrRadio1);
        pldrRadio1.setOnAction(e -> statusLbl.setText(""));
        pldrRadio2.setOnAction(e -> statusLbl.setText(""));
        pldrRadio3.setOnAction(e -> statusLbl.setText(""));
        pldrRadio4.setOnAction(e -> statusLbl.setText(""));
        pldrRadio5.setOnAction(e -> statusLbl.setText(""));

        String recentRcm1 = AppPreferences.getInstance().getRecentRcm(1);
        String recentRcm2 = AppPreferences.getInstance().getRecentRcm(2);
        String recentRcm3 = AppPreferences.getInstance().getRecentRcm(3);
        String recentRcm4 = AppPreferences.getInstance().getRecentRcm(4);
        String recentRcm5 = AppPreferences.getInstance().getRecentRcm(5);

        String myRegexp;
        if (File.separator.equals("/"))
            myRegexp = "^.+/";
        else
            myRegexp = "^.+\\\\";

        if (! recentRcm1.isEmpty()) {
            payloadFNameLbl1.setText(recentRcm1.replaceAll(myRegexp, ""));
            payloadFPathLbl1.setText(recentRcm1);
        }
        if (! recentRcm2.isEmpty()) {
            payloadFNameLbl2.setText(recentRcm2.replaceAll(myRegexp, ""));
            payloadFPathLbl2.setText(recentRcm2);
        }
        if (! recentRcm3.isEmpty()) {
            payloadFNameLbl3.setText(recentRcm3.replaceAll(myRegexp, ""));
            payloadFPathLbl3.setText(recentRcm3);
        }
        if (! recentRcm4.isEmpty()) {
            payloadFNameLbl4.setText(recentRcm4.replaceAll(myRegexp, ""));
            payloadFPathLbl4.setText(recentRcm4);
        }
        if (! recentRcm5.isEmpty()) {
            payloadFNameLbl5.setText(recentRcm5.replaceAll(myRegexp, ""));
            payloadFPathLbl5.setText(recentRcm5);
        }

       // TODO: write logic ?? Like in case PAYLOADER exist, button active. If not: not active?
        injectPldBtn.setOnAction(actionEvent -> smash());
    }

    private void smash(){
        statusLbl.setText("");
        if (MediatorControl.getInstance().getTransferActive()) {
            ServiceWindow.getErrorNotification(rb.getString("windowTitleError"), rb.getString("windowBodyPleaseFinishTransfersFirst"));
            return;
        }

        Task<Boolean> RcmTask;
        RadioButton selectedRadio = (RadioButton)rcmToggleGrp.getSelectedToggle();
        switch (selectedRadio.getId()){
            case "pldrRadio1":
                RcmTask = new RcmTask(payloadFPathLbl1.getText());
                break;
            case "pldrRadio2":
                RcmTask = new RcmTask(payloadFPathLbl2.getText());
                break;
            case "pldrRadio3":
                RcmTask = new RcmTask(payloadFPathLbl3.getText());
                break;
            case "pldrRadio4":
                RcmTask = new RcmTask(payloadFPathLbl4.getText());
                break;
            case "pldrRadio5":
                RcmTask = new RcmTask(payloadFPathLbl5.getText());
                break;
            default:
                return;
        }

        RcmTask.setOnSucceeded(event -> {
            if (RcmTask.getValue())
                statusLbl.setText(rb.getString("done_txt"));
            else
                statusLbl.setText(rb.getString("failure_txt"));
        });
        Thread RcmThread = new Thread(RcmTask);
        RcmThread.setDaemon(true);
        RcmThread.start();
    }

    @FXML
    private void bntSelectPayloader(ActionEvent event){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(rb.getString("btn_Select"));

        File validator = new File(payloadFPathLbl1.getText()).getParentFile();
        if (validator != null && validator.exists())
            fileChooser.setInitialDirectory(validator);
        else
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("bin", "*.bin"));

        File payloadFile = fileChooser.showOpenDialog(payloadFPathLbl1.getScene().getWindow());
        if (payloadFile != null) {
            final Node btn = (Node)event.getSource();

            switch (btn.getId()){
                case "selPldBtn1":
                    payloadFNameLbl1.setText(payloadFile.getName());
                    payloadFPathLbl1.setText(payloadFile.getAbsolutePath());
                    rcmToggleGrp.selectToggle(pldrRadio1);
                    break;
                case "selPldBtn2":
                    payloadFNameLbl2.setText(payloadFile.getName());
                    payloadFPathLbl2.setText(payloadFile.getAbsolutePath());
                    rcmToggleGrp.selectToggle(pldrRadio2);
                    break;
                case "selPldBtn3":
                    payloadFNameLbl3.setText(payloadFile.getName());
                    payloadFPathLbl3.setText(payloadFile.getAbsolutePath());
                    rcmToggleGrp.selectToggle(pldrRadio3);
                    break;
                case "selPldBtn4":
                    payloadFNameLbl4.setText(payloadFile.getName());
                    payloadFPathLbl4.setText(payloadFile.getAbsolutePath());
                    rcmToggleGrp.selectToggle(pldrRadio4);
                    break;
                case "selPldBtn5":
                    payloadFNameLbl5.setText(payloadFile.getName());
                    payloadFPathLbl5.setText(payloadFile.getAbsolutePath());
                    rcmToggleGrp.selectToggle(pldrRadio5);
            }
        }
    }
    @FXML
    private void bntResetPayloader(ActionEvent event){
        final Node btn = (Node)event.getSource();

        switch (btn.getId()){
            case "resPldBtn1":
                payloadFNameLbl1.setText("");
                payloadFPathLbl1.setText("");
                statusLbl.setText("");
                break;
            case "resPldBtn2":
                payloadFNameLbl2.setText("");
                payloadFPathLbl2.setText("");
                statusLbl.setText("");
                break;
            case "resPldBtn3":
                payloadFNameLbl3.setText("");
                payloadFPathLbl3.setText("");
                statusLbl.setText("");
                break;
            case "resPldBtn4":
                payloadFNameLbl4.setText("");
                payloadFPathLbl4.setText("");
                statusLbl.setText("");
                break;
            case "resPldBtn5":
                payloadFNameLbl5.setText("");
                payloadFPathLbl5.setText("");
                statusLbl.setText("");
        }
    }

    @FXML
    public void selectPldrPane(MouseEvent mouseEvent) {
        final Node selectedPane = (Node)mouseEvent.getSource();

        switch (selectedPane.getId()){
            case "pldPane1":
                pldrRadio1.fire();
                break;
            case "pldPane2":
                pldrRadio2.fire();
                break;
            case "pldPane3":
                pldrRadio3.fire();
                break;
            case "pldPane4":
                pldrRadio4.fire();
                break;
            case "pldPane5":
                pldrRadio5.fire();
                break;
        }
    }

    public void notifySmThreadStarted(boolean isStart, EModule type){
        rcmToolPane.setDisable(isStart);
        if (type.equals(EModule.RCM) && isStart){
            MediatorControl.getInstance().getContoller().logArea.clear();
        }
    }
    /**
     * Save application settings on exit
     * */
    public void updatePreferencesOnExit(){
        AppPreferences.getInstance().setRecentRcm(1, payloadFPathLbl1.getText());
        AppPreferences.getInstance().setRecentRcm(2, payloadFPathLbl2.getText());
        AppPreferences.getInstance().setRecentRcm(3, payloadFPathLbl3.getText());
        AppPreferences.getInstance().setRecentRcm(4, payloadFPathLbl4.getText());
        AppPreferences.getInstance().setRecentRcm(5, payloadFPathLbl5.getText());
    }
}
