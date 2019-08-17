package nsusbloader;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import nsusbloader.Controllers.NSLMainController;

import java.util.Locale;
import java.util.ResourceBundle;

public class NSLMain extends Application {
    public static final String appVersion = "v0.6";
    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/NSLMain.fxml"));

        Locale userLocale = new Locale(AppPreferences.getInstance().getLanguage());      // NOTE: user locale based on ISO3 Language codes
        ResourceBundle rb = ResourceBundle.getBundle("locale", userLocale);

        loader.setResources(rb);
        Parent root = loader.load();

        primaryStage.getIcons().addAll(
                new Image(getClass().getResourceAsStream("/res/app_icon32x32.png")),
                new Image(getClass().getResourceAsStream("/res/app_icon48x48.png")),
                new Image(getClass().getResourceAsStream("/res/app_icon64x64.png")),
                new Image(getClass().getResourceAsStream("/res/app_icon128x128.png"))
        );

        primaryStage.setTitle("NS-USBloader "+appVersion);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        Scene mainScene = new Scene(root, 800, 425);

        mainScene.getStylesheets().add(AppPreferences.getInstance().getTheme());

        primaryStage.setScene(mainScene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e->{
            if (MediatorControl.getInstance().getTransferActive())
                if(! ServiceWindow.getConfirmationWindow(rb.getString("windowTitleConfirmExit"), rb.getString("windowBodyConfirmExit")))
                    e.consume();
        });

        NSLMainController controller = loader.getController();
        controller.setHostServices(getHostServices());
        primaryStage.setOnHidden(e-> controller.exit());
    }

    public static void main(String[] args) {
        if ((args.length == 1) && (args[0].equals("-v") || args[0].equals("--version"))){
            System.out.println("NS-USBloader "+NSLMain.appVersion);
        }
        else
            launch(args);
    }
}
