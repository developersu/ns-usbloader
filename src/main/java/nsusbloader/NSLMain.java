/*
    Copyright 2019-2020 Dmitry Isaenko

    This file is part of NS-USBloader.

    NS-USBloader is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NS-USBloader is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NS-USBloader.  If not, see <https://www.gnu.org/licenses/>.
*/
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
import java.util.prefs.Preferences;

public class NSLMain extends Application {

    public static final String appVersion = "v3.0";

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
        primaryStage.setMinWidth(650);
        primaryStage.setMinHeight(450);
        Scene mainScene = new Scene(root,
                AppPreferences.getInstance().getSceneWidth(),
                AppPreferences.getInstance().getSceneHeight()
        );

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
        primaryStage.setOnHidden(e-> {
            AppPreferences.getInstance().setSceneHeight(mainScene.getHeight());
            AppPreferences.getInstance().setSceneWidth(mainScene.getWidth());
            controller.exit();
        });
    }

    public static void main(String[] args) {
        if (handleCli(args))
            return;
        launch(args);
    }

    private static boolean handleCli(String[] args){
        if (args.length != 1)
            return false;

        try {
            switch (args[0]) {
                case "-v":
                case "--version":
                    System.out.println("NS-USBloader " + NSLMain.appVersion);
                    return true;
                case "-c":
                case "--clean":
                    if (Preferences.userRoot().nodeExists("NS-USBloader")) {
                        Preferences.userRoot().node("NS-USBloader").removeNode();
                        System.out.println("Settings removed");
                    }
                    else
                        System.out.println("Nothing to remove");
                    return true;
                case "--help":
                    System.out.println("CLI Usage:\n"
                            + "\t-c, --clean\tRemove/reset settings and exit\n"
                            + "\t-v, --version \tShow application version\n"
                            + "\t--help\t\tShow this message");
                    return true;
                default:
                    return false;
            }
        }
        catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }
}
