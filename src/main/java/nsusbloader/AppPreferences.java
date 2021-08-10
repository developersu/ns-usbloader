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

import java.util.Locale;
import java.util.prefs.Preferences;

public class AppPreferences {
    private static final AppPreferences INSTANCE = new AppPreferences();
    public static AppPreferences getInstance() { return INSTANCE; }

    private final Preferences preferences;
    private final Locale locale;
    public static final String[] goldleafSupportedVersions = {"v0.5", "v0.7.x", "v0.8"};

    private AppPreferences(){
        this.preferences = Preferences.userRoot().node("NS-USBloader");
        String localeCode = preferences.get("locale", Locale.getDefault().toString());
        this.locale = new Locale(localeCode.substring(0, 2), localeCode.substring(3, 5));
    }

    public String getTheme(){
        String theme = preferences.get("THEME", "/res/app_dark.css");           // Don't let user to change settings manually
        if (!theme.matches("(^/res/app_dark.css$)|(^/res/app_light.css$)"))
            theme = "/res/app_dark.css";
        return theme;
    }
    public int getProtocol(){
        int protocolIndex = preferences.getInt("protocol_index", 0);           // Don't let user to change settings manually
        if (protocolIndex < 0 || protocolIndex > 1)
            protocolIndex = 0;
        return protocolIndex;
    }
    public void setProtocol(int protocolIndex){ preferences.putInt("protocol_index", protocolIndex); }

    public String getNetUsb(){
        String netUsb = preferences.get("NETUSB", "USB");           // Don't let user to change settings manually
        if (!netUsb.matches("(^USB$)|(^NET$)"))
            netUsb = "USB";
        return netUsb;
    }

    public void setTheme(String theme){ preferences.put("THEME", theme); }

    public void setNetUsb(String netUsb){ preferences.put("NETUSB", netUsb); }

    public void setNsIp(String ip){preferences.put("NSIP", ip);}
    public String getNsIp(){return preferences.get("NSIP", "192.168.1.42");}

    public String getRecent(){ return FilesHelper.getRealFolder(preferences.get("RECENT", System.getProperty("user.home"))); }
    public void setRecent(String path){ preferences.put("RECENT", path); }
    //------------ SETTINGS ------------------//

    public Locale getLocale(){ return this.locale; }
    public void setLocale(String langStr){ preferences.put("locale", langStr); }

    public boolean getNsIpValidationNeeded() {return preferences.getBoolean("NSIPVALIDATION", true);}
    public void setNsIpValidationNeeded(boolean need){preferences.putBoolean("NSIPVALIDATION", need);}

    public boolean getExpertMode(){return preferences.getBoolean("EXPERTMODE", false);}
    public void setExpertMode(boolean mode){preferences.putBoolean("EXPERTMODE", mode);}

    public boolean getAutoDetectIp(){return preferences.getBoolean("AUTOHOSTIP", true);}
    public void setAutoDetectIp(boolean mode){preferences.putBoolean("AUTOHOSTIP", mode);}

    public boolean getRandPort(){return preferences.getBoolean("RANDHOSTPORT", true);}
    public void setRandPort(boolean mode){preferences.putBoolean("RANDHOSTPORT", mode);}

    public boolean getNotServeRequests(){return preferences.getBoolean("DONTSERVEREQ", false);}
    public void setNotServeRequests(boolean mode){preferences.putBoolean("DONTSERVEREQ", mode);}

    public String getHostIp(){ return preferences.get("HOSTIP", "0.0.0.0").replaceAll("(\\s)|(\t)", "");}   // who the hell said 'paranoid'?
    public void setHostIp(String ip){preferences.put("HOSTIP", ip);}

    public String getHostPort(){
        String value = preferences.get("HOSTPORT", "6042");
        if (!value.matches("^[0-9]{1,5}$"))
            return "6042";
        if ((Integer.parseInt(value) > 65535) || (Integer.parseInt(value) < 1))
            return "6042";
        return value;
    }
    public void setHostPort(String port){preferences.put("HOSTPORT", port);}

    public String getHostExtra(){ return preferences.get("HOSTEXTRA", "").replaceAll("(\\s)|(\t)", "");}    // oh just shut up...
    public void setHostExtra(String postfix){preferences.put("HOSTEXTRA", postfix);}

    public boolean getAutoCheckUpdates(){return preferences.getBoolean("AUTOCHECK4UPDATES", false); }
    public void setAutoCheckUpdates(boolean prop){preferences.putBoolean("AUTOCHECK4UPDATES", prop); }

    public boolean getDirectoriesChooserForRoms(){return preferences.getBoolean("dirchooser4roms", false); }
    public void setDirectoriesChooserForRoms(boolean prop){preferences.putBoolean("dirchooser4roms", prop); }

    public boolean getTfXCI(){return preferences.getBoolean("TF_XCI", true);}
    public void setTfXCI(boolean prop){ preferences.putBoolean("TF_XCI", prop); }

    public boolean getNspFileFilterGL(){return preferences.getBoolean("GL_NSP_FILTER", false); }
    public void setNspFileFilterGL(boolean prop){preferences.putBoolean("GL_NSP_FILTER", prop);}

    public String getGlVersion(){
        int recentGlVersionIndex = goldleafSupportedVersions.length - 1;
        String recentGlVersion = goldleafSupportedVersions[recentGlVersionIndex];
        return preferences.get("gl_version", recentGlVersion);
    }
    public void setGlVersion(String version){ preferences.put("gl_version", version);}

    public double getSceneWidth(){ return preferences.getDouble("WIND_WIDTH", 850.0); }
    public void setSceneWidth(double value){ preferences.putDouble("WIND_WIDTH", value); }

    public double getSceneHeight(){ return preferences.getDouble("WIND_HEIGHT", 525.0); }
    public void setSceneHeight(double value){ preferences.putDouble("WIND_HEIGHT", value); }
    // Split and Merge //
    public int getSplitMergeType(){ return preferences.getInt("SM_TYPE", 0); }
    public void setSplitMergeType(int value){ preferences.putInt("SM_TYPE", value); }

    public String getSplitMergeRecent(){ return FilesHelper.getRealFolder(preferences.get("SM_RECENT", System.getProperty("user.home"))); }
    public void setSplitMergeRecent(String value){ preferences.put("SM_RECENT", value); }
    // RCM //
    public String getRecentRcm(int num){ return preferences.get(String.format("RCM_%02d", num), ""); }
    public void setRecentRcm(int num, String value){ preferences.put(String.format("RCM_%02d", num), value); }
    // NXDT //
    public String getNXDTSaveToLocation(){ return FilesHelper.getRealFolder(preferences.get("nxdt_saveto", System.getProperty("user.home"))); }
    public void setNXDTSaveToLocation(String value){ preferences.put("nxdt_saveto", value); }

    public String getLastOpenedTab(){ return preferences.get("recent_tab", ""); }
    public void setLastOpenedTab(String tabId){ preferences.put("recent_tab", tabId); }
}
