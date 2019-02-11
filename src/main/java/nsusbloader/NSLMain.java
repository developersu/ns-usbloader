/**
 Name: NSL-USBFoil
 @author Dmitry Isaenko
 License: GNU GPL v.3
 @see https://github.com/developersu/
 @see https://developersu.blogspot.com/
 2019, Russia
 */
package nsusbloader;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.ResourceBundle;

public class NSLMain extends Application {
    static final String appVersion = "v0.1";
    @Override
    public void start(Stage primaryStage) throws Exception{
        ResourceBundle rb;
        if (Locale.getDefault().getISO3Language().equals("rus"))
            rb = ResourceBundle.getBundle("locale", new Locale("ru"));
        else
            rb = ResourceBundle.getBundle("locale", new Locale("en"));

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/NSLMain.fxml"));

        loader.setResources(rb);
        Parent root = loader.load();

        primaryStage.getIcons().addAll(
                new Image(getClass().getResourceAsStream("/res/app_icon32x32.png")),
                new Image(getClass().getResourceAsStream("/res/app_icon48x48.png")),
                new Image(getClass().getResourceAsStream("/res/app_icon64x64.png")),
                new Image(getClass().getResourceAsStream("/res/app_icon128x128.png"))
        );

        primaryStage.setTitle("NS-USBloader");
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(375);
        Scene mainScene = new Scene(root, 800, 400);
        mainScene.getStylesheets().add("/res/app.css");
        primaryStage.setScene(mainScene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(e->{
            if (MediatorControl.getInstance().getTransferActive())
                if(! ServiceWindow.getConfirmationWindow(rb.getString("windowTitleConfirmExit"), rb.getString("windowBodyConfirmExit")))
                    e.consume();
        });
    }

    public static void main(String[] args) {
        if ((args.length == 1) && (args[0].equals("-v") || args[0].equals("--version"))){
            System.out.println("NS-USBloader "+NSLMain.appVersion);
        }
        else
            launch(args);
    }
}
