/*
    Copyright 2019-2026 Dmitry Isaenko

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

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.URI;

import static nsusbloader.Utilities.WindowsDrivers.DriversInstall.DRIVERS_FILE_SIZE;
import static nsusbloader.Utilities.WindowsDrivers.DriversInstall.FILE_NAME;

public class DownloadDriversTask extends Task<Boolean> {

    @Override
    protected Boolean call() {
        try {
            var url = new URI("https", "github.com", "/developersu/NS-Drivers/releases/download/v2.0/Drivers_set.exe", null)
                    .toURL();
            var dataBuffer = new byte[1024];
            int bytesRead;
            double totalRead = 0;

            try (var inStream = new BufferedInputStream(url.openStream())) {
                var fileOutStream = new FileOutputStream(FILE_NAME);
                while ((bytesRead = inStream.read(dataBuffer, 0, 1024)) != -1) {
                    fileOutStream.write(dataBuffer, 0, bytesRead);
                    totalRead += bytesRead;
                    updateProgress(totalRead, DRIVERS_FILE_SIZE);
                    if (isCancelled()) {
                        fileOutStream.close();
                        return true;
                    }
                }
                fileOutStream.close();
            }
            return false;
        }
        catch (Exception e) {
            updateMessage("Error: "+e.toString().replaceAll(":.*$", ""));
            updateProgress(0, 0);
            e.printStackTrace();
            return true;
        }
    }
}
