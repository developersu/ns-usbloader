package nsusbloader.Controllers;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import nsusbloader.*;
import nsusbloader.ModelControllers.UpdatesChecker;

import java.net.*;
import java.util.List;
import java.util.ResourceBundle;

public class NSLMainController implements Initializable {

    private ResourceBundle resourceBundle;

    @FXML
    public TextArea logArea;            // Accessible from Mediator

    @FXML
    public ProgressBar progressBar;            // Accessible from Mediator

    @FXML
    public FrontController FrontTabController;             // Accessible from Mediator | todo: incapsulate
    @FXML
    private SettingsController SettingsTabController;
    @FXML
    private SplitMergeController SplitMergeTabController;
    @FXML
    private RcmController RcmTabController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.resourceBundle = rb;
        logArea.setText(rb.getString("tab3_Txt_GreetingsMessage")+" "+ NSLMain.appVersion+"!\n");
        if (System.getProperty("os.name").toLowerCase().startsWith("lin"))
            if (!System.getProperty("user.name").equals("root"))
                logArea.appendText(rb.getString("tab3_Txt_EnteredAsMsg1")+System.getProperty("user.name")+"\n"+rb.getString("tab3_Txt_EnteredAsMsg2") + "\n");

        logArea.appendText(rb.getString("tab3_Txt_GreetingsMessage2")+"\n");

        MediatorControl.getInstance().setController(this);

        if (AppPreferences.getInstance().getAutoCheckUpdates()){
            Task<List<String>> updTask = new UpdatesChecker();
            updTask.setOnSucceeded(event->{
                List<String> result = updTask.getValue();
                if (result != null){
                    if (!result.get(0).isEmpty()) {
                        SettingsTabController.setNewVersionLink(result.get(0));
                        ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionAval"), resourceBundle.getString("windowTitleNewVersionAval") + ": " + result.get(0) + "\n\n" + result.get(1));
                    }
                }
                else
                    ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionUnknown"), resourceBundle.getString("windowBodyNewVersionUnknown"));
            });
            Thread updates = new Thread(updTask);
            updates.setDaemon(true);
            updates.start();
        }
    }

    /**
     * Get resources
     * TODO: Find better solution; used in UsbCommunications() -> GL -> SelectFile command
     * @return ResourceBundle
     */
    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }
    /**
     * Provide hostServices to Settings tab
     * */
    public void setHostServices(HostServices hs ){ SettingsTabController.registerHostServices(hs);}

    /**
     * Get 'Settings' controller
     * Used by FrontController
     * */
    public SettingsController getSettingsCtrlr(){
        return SettingsTabController;
    }

    public FrontController getFrontCtrlr(){
        return FrontTabController;
    }

    public SplitMergeController getSmCtrlr(){
        return SplitMergeTabController;
    }

    public RcmController getRcmCtrlr(){ return RcmTabController; }
    /**
     * Save preferences before exit
     * */
    public void exit(){
        AppPreferences.getInstance().setAll(
                FrontTabController.getSelectedProtocol(),
                FrontTabController.getRecentPath(),
                FrontTabController.getSelectedNetUsb(),
                FrontTabController.getNsIp(),
                SettingsTabController.isNsIpValidate(),
                SettingsTabController.getExpertModeSelected(),
                SettingsTabController.getAutoIpSelected(),
                SettingsTabController.getRandPortSelected(),
                SettingsTabController.getNotServeSelected(),
                SettingsTabController.getHostIp(),
                SettingsTabController.getHostPort(),
                SettingsTabController.getHostExtra(),
                SettingsTabController.getAutoCheckForUpdates(),
                SettingsTabController.getTfXciNszXczSupport(),
                SettingsTabController.getNSPFileFilterForGL(),
                SettingsTabController.getGlOldVer()
        );

        SplitMergeTabController.updatePreferencesOnExit(); // NOTE: This shit above should be re-written to similar pattern
        RcmTabController.updatePreferencesOnExit();
    }
}
