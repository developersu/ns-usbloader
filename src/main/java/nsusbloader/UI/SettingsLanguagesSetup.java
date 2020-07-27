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
package nsusbloader.UI;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import nsusbloader.AppPreferences;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SettingsLanguagesSetup {
    private final ObservableList<LocaleHolder> languages;
    private File thisApplicationFile;
    private LocaleHolder recentlyUsedLanguageHolder;

    public SettingsLanguagesSetup() {
        this.languages = FXCollections.observableArrayList();
        parseFiles();
        sortLanguages();
        defineRecentlyUsedLanguageHolder();
    }

    private void parseFiles() {
        if (isApplicationIsJar())   // Executed as JAR file
            parseFilesInsideJar();
        else                       // Executed within IDE
            parseFilesInFilesystem();
    }

    private boolean isApplicationIsJar() {
        getThisApplicationFile();
        return thisApplicationFile != null && thisApplicationFile.isFile();
    }

    private void getThisApplicationFile() {
        try {
            String encodedJarLocation =
                    getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("+", "%2B");
            this.thisApplicationFile = new File(URLDecoder.decode(encodedJarLocation, "UTF-8"));
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
            this.thisApplicationFile = null;
        }
    }

    private void parseFilesInsideJar() {
        try {
            JarFile jar = new JarFile(thisApplicationFile);
            Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("locale_")) {
                    languages.add(new LocaleHolder(name));
                }
            }
            jar.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void parseFilesInFilesystem() {
        URL resourceURL = this.getClass().getResource("/");
        String[] filesList = new File(resourceURL.getFile()).list(); // Screw it. This WON'T produce NullPointerException

        for (String jarFileName : filesList) {
            if (jarFileName.startsWith("locale_")) {
                languages.add(new LocaleHolder(jarFileName));
            }
        }
    }

    private void sortLanguages() {
        languages.sort(Comparator.comparing(LocaleHolder::toString));
    }

    private void defineRecentlyUsedLanguageHolder() {
        Locale localeFromPreferences = AppPreferences.getInstance().getLocale();

        for (LocaleHolder holder : languages) {
            Locale holderLocale = holder.getLocale();

            if (holderLocale.equals(localeFromPreferences)) {
                this.recentlyUsedLanguageHolder = holder;
                return;
            }
        }
        // Otherwise define default one that is "en_US"
        for (LocaleHolder holder : languages) {
            if (holder.getLocaleCode().equals("en_US")) {
                this.recentlyUsedLanguageHolder = holder;
                return;
            }
        }
    }

    public ObservableList<LocaleHolder> getLanguages() {
        return languages;
    }

    public LocaleHolder getRecentLanguage() {
        return recentlyUsedLanguageHolder;
    }
}