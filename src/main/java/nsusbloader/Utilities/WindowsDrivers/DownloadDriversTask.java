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
package nsusbloader.Utilities.WindowsDrivers;

import javafx.concurrent.Task;

import java.io.*;
import java.net.URL;

public class DownloadDriversTask extends Task<String> {

    private static final String driverFileLocationURL = "https://github.com/developersu/NS-Drivers/releases/download/v1.0/Drivers_set.exe";
    private static final long driversFileSize = 3857375;

    private static File driversInstallerFile;

    @Override
    protected String call() {
        if (isDriversDownloaded() || downloadDrivers())
            return driversInstallerFile.getAbsolutePath();
        return null;
    }

    private boolean isDriversDownloaded(){
        return driversInstallerFile != null && driversInstallerFile.length() == driversFileSize;
    }

    private boolean downloadDrivers(){
        try {
            File tmpDirectory = File.createTempFile("nsul", null);
            if (! tmpDirectory.delete())
                return false;
            if (! tmpDirectory.mkdirs())
                return false;

            tmpDirectory.deleteOnExit();

            URL url = new URL(driverFileLocationURL);
            BufferedInputStream bis = new BufferedInputStream(url.openStream());

            driversInstallerFile = new File(tmpDirectory, "drivers.exe");
            FileOutputStream fos = new FileOutputStream(driversInstallerFile);

            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            double totalRead = 0;

            while ((bytesRead = bis.read(dataBuffer, 0, 1024)) != -1) {
                fos.write(dataBuffer, 0, bytesRead);
                totalRead += bytesRead;
                updateProgress(totalRead, driversFileSize);
                if (this.isCancelled()) {
                    bis.close();
                    fos.close();
                    updateProgress(0, 0);
                    return false;
                }
            }
            bis.close();
            fos.close();

            return true;
        }
        catch (IOException | SecurityException e){
            updateMessage("Error: "+e.toString().replaceAll(":.*$", ""));
            e.printStackTrace();
            return false;
        }
    }
}
