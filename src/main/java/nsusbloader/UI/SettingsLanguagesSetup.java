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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
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
        var encodedJarLocation =
                getClass().getProtectionDomain().getCodeSource().getLocation().getPath().replace("+", "%2B");
        thisApplicationFile = new File(URLDecoder.decode(encodedJarLocation, StandardCharsets.UTF_8));
        return thisApplicationFile.isFile();
    }

    private void parseFilesInsideJar() {
        try (var jar = new JarFile(thisApplicationFile)) {
            jar.stream()
                    .filter(entry -> entry.getName().startsWith("locale_"))
                    .map(entry -> new LocaleHolder(entry.getName()))
                    .forEach(languages::add);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseFilesInFilesystem() {
        var pathToResource = Objects.requireNonNull(getClass().getResource("/"))
                .getPath();
        try (var streamDirs = Files.newDirectoryStream(Path.of(pathToResource))) {
            streamDirs.forEach(jarFileName -> {
                var fileName = ""+jarFileName.getFileName();
                if (fileName.startsWith("locale_"))
                    languages.add(new LocaleHolder(fileName));
            });
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void sortLanguages() {
        languages.sort(Comparator.comparing(LocaleHolder::toString));
    }

    private void defineRecentlyUsedLanguageHolder() {
        recentlyUsedLanguageHolder = languages.stream()
                .filter(localeHolder -> localeHolder.getLocale().equals(
                        AppPreferences.getInstance().getLocale()))
                .findFirst()
                .orElse(null);

        if (recentlyUsedLanguageHolder == null) {
            recentlyUsedLanguageHolder = languages.stream()
                    .filter(localeHolder -> localeHolder.getLocaleCode().equals("en_US"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Unable to get default locale (en_US)"));
        }
    }

    public ObservableList<LocaleHolder> getLanguages() {
        return languages;
    }

    public LocaleHolder getRecentLanguage() {
        return recentlyUsedLanguageHolder;
    }
}