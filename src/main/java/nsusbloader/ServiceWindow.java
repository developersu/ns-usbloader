package nsusbloader;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.Optional;

public class ServiceWindow   {
    /** Create window with error notification */
    public static void getErrorNotification(String title, String body){
        getNotification(title, body, Alert.AlertType.ERROR);
    }
    /** Create window with information notification */
    public static void getInfoNotification(String title, String body){
        getNotification(title, body, Alert.AlertType.INFORMATION);
    }
    /** Real window creator */
    private static void getNotification(String title, String body, Alert.AlertType type){
        Alert alertBox = new Alert(type);
        alertBox.setTitle(title);
        alertBox.setHeaderText(null);
        alertBox.setContentText(body);
        alertBox.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        alertBox.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alertBox.setResizable(true);        // Java bug workaround for JDR11/OpenJFX. TODO: nothing. really.
        alertBox.getDialogPane().getStylesheets().add(AppPreferences.getInstance().getTheme());

        Stage dialogStage = (Stage) alertBox.getDialogPane().getScene().getWindow();
        dialogStage.setAlwaysOnTop(true);
        dialogStage.toFront();

        alertBox.show();
    }
    /**
     * Create notification window with confirm/deny
     * */
    public static boolean getConfirmationWindow(String title, String body){
        Alert alertBox = new Alert(Alert.AlertType.CONFIRMATION);
        alertBox.setTitle(title);
        alertBox.setHeaderText(null);
        alertBox.setContentText(body);
        alertBox.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        alertBox.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alertBox.setResizable(true);        // Java bug workaround for JDR11/OpenJFX. TODO: nothing. really.
        alertBox.getDialogPane().getStylesheets().add(AppPreferences.getInstance().getTheme());
        Optional<ButtonType> result = alertBox.showAndWait();

        return (result.isPresent() && result.get() == ButtonType.OK);
    }
}
