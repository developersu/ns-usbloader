package nsusbloader;

import java.util.prefs.Preferences;

public class AppPreferences {
    private static final AppPreferences INSTANCE = new AppPreferences();
    public static AppPreferences getInstance() { return INSTANCE; }

    private Preferences preferences;

    private AppPreferences(){ preferences = Preferences.userRoot().node("NS-USBloader"); }

    public String getTheme(){
        String theme = preferences.get("THEME", "/res/app_dark.css");           // Don't let user to change settings manually
        if (!theme.matches("(^/res/app_dark.css$)|(^/res/app_light.css$)"))
            theme = "/res/app_dark.css";
        return theme;
    }
    public String getProtocol(){
        String protocol = preferences.get("PROTOCOL", "TinFoil");           // Don't let user to change settings manually
        if (!protocol.matches("(^TinFoil$)|(^GoldLeaf$)"))
            protocol = "TinFoil";
        return protocol;
    }
    public String getNetUsb(){
        String netUsb = preferences.get("NETUSB", "USB");           // Don't let user to change settings manually
        if (!netUsb.matches("(^USB$)|(^NET$)"))
            netUsb = "USB";
        return netUsb;
    }
    public void setTheme(String theme){ preferences.put("THEME", theme); }
    public void setProtocol(String protocol){ preferences.put("PROTOCOL", protocol); }
    public void setNetUsb(String netUsb){ preferences.put("NETUSB", netUsb); }

    public void setNsIp(String ip){preferences.put("NSIP", ip);}
    public String getNsIp(){return preferences.get("NSIP", "192.168.1.42");}

    public String getRecent(){ return preferences.get("RECENT", System.getProperty("user.home")); }
    public void setRecent(String path){ preferences.put("RECENT", path); }
    //------------ SETTINGS ------------------//
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

    public String getHostIp(){ return preferences.get("HOSTIP", "0.0.0.0");}
    public void setHostIp(String ip){preferences.put("HOSTIP", ip);}

    public String getHostPort(){ return preferences.get("HOSTPORT", "6042");}
    public void setHostPort(String port){preferences.put("HOSTPORT", port);}

    public String getHostPostfix(){ return preferences.get("HOSTPOSTFIX", "");}
    public void setHostPostfix(String postfix){preferences.put("HOSTPOSTFIX", postfix);}
}
