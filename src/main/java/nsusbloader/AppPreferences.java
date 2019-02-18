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
    public void setTheme(String theme){ preferences.put("THEME", theme); }

    public String getRecent(){ return preferences.get("RECENT", System.getProperty("user.home")); }
    public void setRecent(String path){ preferences.put("RECENT", path); }
}
