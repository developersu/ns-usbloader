package nsusbloader.ModelControllers;

import javafx.concurrent.Task;
import nsusbloader.NSLMain;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class UpdatesChecker extends Task<List<String>> {
    @Override
    protected List<String> call() {
        String respondedJson;
        try {
            URL gitHubUrl = new URL("https://api.github.com/repos/developersu/ns-usbloader/releases/latest");
            HttpsURLConnection connection = (HttpsURLConnection) gitHubUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int status = connection.getResponseCode();
            if (status != 200) {
                return null;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            respondedJson = br.readLine();
            br.close();
            connection.disconnect();

            if (respondedJson == null)
                return null;
        }
        catch (IOException mue){
            return null;
        }

        String newVersion = respondedJson.replaceAll("(^.*\"tag_name\":\")(.?|.+?)(\".+$)", "$2");
        String changeLog = respondedJson.replaceAll("(^.*\"body\":\")(.?|.+?)(\".+$)", "$2")
                .replaceAll("\\\\r\\\\n","\n")
                .replaceAll("#+?\\s", "");      // replace #### dsds | # dsds

        if (newVersion.matches("^v(([0-9])+?\\.)+[0-9]+(-.+)$"))                // if new version have postfix like v0.1-Experimental
            newVersion = newVersion.replaceAll("(-.*$)", "");       // cut postfix

        if ( ! newVersion.matches("^v(([0-9])+?\\.)+[0-9]+$")) {                    // check if new version structure valid
            return null;
        }

        String currentVersion;
        if (NSLMain.appVersion.matches("^v(([0-9])+?\\.)+[0-9]+(-.+)$"))        // if current version have postfix like v0.1-Experimental
            currentVersion = NSLMain.appVersion.replaceAll("(-.*$)", "");       // cut postfix
        else
            currentVersion = NSLMain.appVersion;

        List<String> returningValue = new ArrayList<>();
        if (!newVersion.equals(currentVersion)) {                     //  if latest noted version in GitHub is different to this version
            returningValue.add(newVersion);
            returningValue.add(changeLog);
            return returningValue;
        }
        returningValue.add("");
        returningValue.add("");
        return returningValue;
    }
}