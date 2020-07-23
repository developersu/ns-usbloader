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
package nsusbloader.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

public class JNIRcmLoader {
    private JNIRcmLoader(){}
    public static boolean load(){
        String osName = System.getProperty("os.name").toLowerCase().replace(" ", "");
        String osArch = System.getProperty("os.arch").toLowerCase().replace(" ", "");
        String libPostfix;

        if (osName.equals("linux")){
            switch (osArch){
                case "i386":
                case "i586":
                case "i686":
                    osArch = "x86";
                    break;
                case "x86_64":
                case "amd64":
                    osArch = "amd64";
                    break;
                case "arm":
                    osArch = "arm";
                    break;
                default:
                    return false;
            }
            libPostfix = "so";
        }
        else if (osName.contains("windows")){
            osName = "windows";
            libPostfix = "dll";
            switch (osArch){
                case "x86":
                case "i386":
                case "i586":
                case "i686":
                    osArch = "x86";
                    break;
                case "x86_64":
                case "amd64":
                    osArch = "amd64";
                    break;
                default:
                    return false;
            }
        }
        else
            return false;
        final URL url_ = RcmSmash.class.getResource("/native/"+osName+"/"+osArch+"/smashlib."+libPostfix);
        if (url_ == null)
            return false;

        String proto = url_.getProtocol();

        File libraryFile;
        if (proto.equals("file")){
            // We can pick file from disk as is.
            try {
                libraryFile = new File(url_.toURI());
            }
            catch (URISyntaxException e){
                e.printStackTrace();
                return false;
            }
        }
        else if (proto.equals("jar")){
            // We have to export file to temp dir.
            InputStream inStream = RcmSmash.class.getResourceAsStream("/native/"+osName+"/"+osArch+"/smashlib."+libPostfix);
            if (inStream == null)
                return false;
            // Create temp folder
            try{
                File tmpDirFile = File.createTempFile("jni", null);
                if (! tmpDirFile.delete())
                    return false;
                if (! tmpDirFile.mkdirs())
                    return false;
                libraryFile = new File(tmpDirFile, "smashlib."+libPostfix);
                byte[] ioBuffer = new byte[8192];
                FileOutputStream foStream = new FileOutputStream(libraryFile);
                while (inStream.read(ioBuffer) != -1)
                    foStream.write(ioBuffer);
                foStream.close();
                inStream.close();
                libraryFile.deleteOnExit();
                tmpDirFile.deleteOnExit();
            }
            catch (IOException ioe){
                ioe.printStackTrace();
                return false;
            }
        }
        else
            return false;

        //System.out.println("LIB LOCATION: "+libraryFile);
        System.load(libraryFile.getAbsolutePath());
        //System.out.println("LIB LOADED");
        return true;
    }
}
