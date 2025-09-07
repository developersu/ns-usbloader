/*
    Copyright 2019-2025 Dmitry Isaenko

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
package nsusbloader.com.usb.gl;

import nsusbloader.ModelControllers.CancellableRunnable;
import nsusbloader.ModelControllers.ILogPrinter;
import nsusbloader.NSLDataTypes.EMsgType;
import org.usb4java.DeviceHandle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static nsusbloader.com.usb.gl.Converters.*;

/**
 * GoldLeaf 1.1.1 processing
 */
public class GoldLeaf_111 extends GoldLeaf_010{

    public GoldLeaf_111(DeviceHandle handler, LinkedHashMap<String, File> nspMap,
                        CancellableRunnable task,
                        ILogPrinter logPrinter,
                        boolean nspFilter) {
        super(handler, nspMap, task, logPrinter, nspFilter);
    }

    @Override
    protected void printWelcomeMessage(){
        print("=========== GoldLeaf v1.1.1 ===========\n\t" +
                "VIRT:/ equals files added into the application\n\t" +
                "HOME:/ equals " + homePath + "\n\t" +
                "BE CAREFUL!\n\t" +
                "Due to some strange behaviour with Goldleaf v1.1.1, you will see last menu entry " +
                "'Do not click (crashes Atmosphere)'\n\t" +
                "You should better not clicking on it", EMsgType.INFO);
    }

    /**
     * Fixes issues with incorrect request for 'Home' & 'Virtual'. Forces both to return 'HOME:/'
     * v1.1.1 specific fix
     * Otherwise v.1.1.1 returns 'HOME:/' once 'Virtual' requested and ':/' once requested 'Home'
     * */

    @Override
    protected boolean getDriveCount(){
        return writeGL_PASS(intToArrLE(3),"GL Handle 'ListDrives' command");
    }

    @Override
    protected boolean getDriveInfo(int driveNo){
        if (driveNo < 0 || driveNo > 2)
            return writeGL_FAIL(INVALID_INDEX, "GL Handle 'GetDriveInfo' command [no such drive]");

        byte[] driveLabel,
                driveLabelLen,
                driveLetter,
                driveLetterLen,
                totalFreeSpace;
        long totalSizeLong;

        switch (driveNo){
            case 0:
                driveLabel = "Home".getBytes(StandardCharsets.UTF_8); // yes, it's hotfix
                driveLabelLen = intToArrLE(driveLabel.length);
                driveLetter = "VIRT".getBytes(StandardCharsets.UTF_8); // and this is fine
                driveLetterLen = intToArrLE(driveLetter.length);
                totalFreeSpace = new byte[4];
                totalSizeLong = virtDriveSize;
                break;
            case 1:
                driveLabel = "Virtual".getBytes(StandardCharsets.UTF_8); // here as well
                driveLabelLen = intToArrLE(driveLabel.length);
                driveLetter = "HOME".getBytes(StandardCharsets.UTF_8); // and here
                driveLetterLen = intToArrLE(driveLetter.length);
                var userHomeDir = new File(System.getProperty("user.home"));
                totalFreeSpace = Arrays.copyOfRange(longToArrLE(userHomeDir.getFreeSpace()), 0, 4);;
                totalSizeLong = userHomeDir.getTotalSpace();
                break;
            default:
                driveLabel = "Do not click (crashes Atmosphere)".getBytes(StandardCharsets.UTF_8); // and this one is necessary too
                driveLabelLen = intToArrLE(driveLabel.length);
                driveLetter = "VIRT".getBytes(StandardCharsets.UTF_8);
                driveLetterLen = intToArrLE(driveLetter.length);
                totalFreeSpace = new byte[4];
                totalSizeLong = virtDriveSize;
                break;
        }

        var totalSize = Arrays.copyOfRange(longToArrLE(totalSizeLong), 0, 4);

        var command = Arrays.asList(
                driveLabelLen,
                driveLabel,
                driveLetterLen,
                driveLetter,
                totalFreeSpace,
                totalSize);

        return writeGL_PASS(command, "GL Handle 'GetDriveInfo' command");
    }
}
