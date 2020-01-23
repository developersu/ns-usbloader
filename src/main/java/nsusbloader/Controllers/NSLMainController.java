package nsusbloader.Controllers;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import nsusbloader.*;
import nsusbloader.ModelControllers.UpdatesChecker;

import java.io.File;
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
     * Drag-n-drop support (dragOver consumer)
     * */
    @FXML
    private void handleDragOver(DragEvent event){
        if (event.getDragboard().hasFiles())
            event.acceptTransferModes(TransferMode.ANY);
    }
    /**
     * Drag-n-drop support (drop consumer)
     * */
    @FXML
    private void handleDrop(DragEvent event){
        if (MediatorControl.getInstance().getTransferActive()) {
            event.setDropCompleted(true);
            return;
        }
        List<File> filesDropped = event.getDragboard().getFiles();

        if (FrontTabController.getSelectedProtocol().equals("TinFoil") && SettingsTabController.getTfXciNszXczSupport())
            filesDropped.removeIf(file -> ! file.getName().toLowerCase().matches("(.*\\.nsp$)|(.*\\.xci$)|(.*\\.nsz$)|(.*\\.xcz$)"));
        else if (FrontTabController.getSelectedProtocol().equals("GoldLeaf") && (! SettingsTabController.getNSPFileFilterForGL()))
            filesDropped.removeIf(file -> (file.isDirectory() && ! file.getName().toLowerCase().matches(".*\\.nsp$")));
        else
            filesDropped.removeIf(file -> ! file.getName().toLowerCase().matches(".*\\.nsp$"));

        if ( ! filesDropped.isEmpty() )
            FrontTabController.tableFilesListController.setFiles(filesDropped);

        event.setDropCompleted(true);
    }
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
    }
}
