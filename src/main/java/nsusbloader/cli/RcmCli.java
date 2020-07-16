package nsusbloader.cli;

import java.io.File;

public class RcmCli {
    RcmCli(String argument) throws InterruptedException, IncorrectSetupException{
        runBackend(argument);
    }

    private void runBackend(String payload) throws InterruptedException{
        /*
        boolean isWindows = System.getProperty("os.name").toLowerCase().replace(" ", "").contains("windows");

        if (isWindows) {
            if (! payload.matches("^.:\\\\.*$"))
                payload = System.getProperty("user.dir") + File.separator + payload;
        }
        else {
            if (! payload.startsWith("/"))
                payload = System.getProperty("user.dir") + File.separator + payload;
        }
        */
        nsusbloader.Utilities.Rcm rcm = new nsusbloader.Utilities.Rcm(payload);
        Thread rcmThread = new Thread(rcm);
        rcmThread.start();
        rcmThread.join();
    }
}
