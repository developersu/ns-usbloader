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
