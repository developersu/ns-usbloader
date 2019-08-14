package nsusbloader;

import java.util.Locale;
import java.util.prefs.Preferences;

public class AppPreferences {
    private static final AppPreferences INSTANCE = new AppPreferences();
    public static AppPreferences getInstance() { return INSTANCE; }

    private Preferences preferences;

    private AppPreferences(){ preferences = Preferences.userRoot().node("NS-USBloader"); }

    public void setAll(
            String Protocol,
            String PreviouslyOpened,
            String NetUsb,
            String NsIp,
            boolean NsIpValidate,
            boolean ExpertMode,
            boolean AutoIp,
            boolean RandPort,
            boolean NotServe,
            String HostIp,
            String HostPort,
            String HostExtra,
            boolean autoCheck4Updates,
            boolean tinfoilXciSupport,
            boolean nspFileFilterForGl
            ){
        setProtocol(Protocol);
        setRecent(PreviouslyOpened);
        setNetUsb(NetUsb);
        setNsIp(NsIp);
        setNsIpValidationNeeded(NsIpValidate);
        setExpertMode(ExpertMode);
        setAutoDetectIp(AutoIp);
        setRandPort(RandPort);
        setNotServeRequests(NotServe);
        setHostIp(HostIp);
        setHostPort(HostPort);
        setHostExtra(HostExtra);
        setAutoCheckUpdates(autoCheck4Updates);
        setTfXCI(tinfoilXciSupport);
        setNspFileFilterGL(nspFileFilterForGl);
    }
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

    public boolean getTfXCI(){return preferences.getBoolean("TF_XCI", false);}
    public void setTfXCI(boolean prop){ preferences.putBoolean("TF_XCI", prop); }

    public String getLanguage(){return preferences.get("USR_LANG", Locale.getDefault().getISO3Language());}
    public void setLanguage(String langStr){preferences.put("USR_LANG", langStr);}

    public boolean getNspFileFilterGL(){return preferences.getBoolean("GL_NSP_FILTER", false); }
    public void setNspFileFilterGL(boolean prop){preferences.putBoolean("GL_NSP_FILTER", prop);}
}
