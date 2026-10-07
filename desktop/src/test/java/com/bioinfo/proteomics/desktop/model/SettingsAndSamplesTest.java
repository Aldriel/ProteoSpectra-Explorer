/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.proteomics.desktop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.desktop.prefs.UserPreferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

class SettingsAndSamplesTest {

    private final Preferences node =
            Preferences.userRoot().node("pride-desktop-test-" + UUID.randomUUID());
    private final UserPreferences prefs = new UserPreferences(node);

    @AfterEach
    void removeNode() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void preferencesHaveDefaultsAndClampValues() {
        assertEquals(UserPreferences.DEFAULT_FONT_SIZE, prefs.fontSize());
        prefs.setFontSize(99);
        assertEquals(UserPreferences.MAX_FONT_SIZE, prefs.fontSize());
        prefs.setFontSize(1);
        assertEquals(UserPreferences.MIN_FONT_SIZE, prefs.fontSize());

        prefs.setLocale(Locale.GERMAN);
        assertEquals(Locale.ENGLISH, prefs.locale(), "unsupported languages are saved as English");
        prefs.setLocale(Locale.CANADA_FRENCH);
        assertEquals(Locale.FRENCH, prefs.locale());

        assertEquals(UserPreferences.DEFAULT_WINDOW_WIDTH, prefs.windowWidth());
        assertFalse(prefs.windowMaximized());
        prefs.setWindowState(1000, 100, false);
        assertEquals(1000, prefs.windowWidth());
        assertEquals(400, prefs.windowHeight(), "window size is at least 400 px");
        prefs.setWindowState(50, 50, true);
        assertTrue(prefs.windowMaximized());
        assertEquals(1000, prefs.windowWidth(), "size is kept while maximized");
        prefs.flush();
    }

    @Test
    void lastDirectoryMustStillExist(@TempDir Path dir) throws IOException {
        assertTrue(prefs.lastDirectory().isEmpty());
        Path sub = Files.createDirectory(dir.resolve("data"));
        prefs.setLastDirectory(sub);
        assertEquals(Optional.of(sub.toAbsolutePath()), prefs.lastDirectory());
        Files.delete(sub);
        assertTrue(prefs.lastDirectory().isEmpty());
    }

    @Test
    void saveDirectoryIsKeptApartFromTheOpenDirectory(@TempDir Path dir) {
        assertTrue(prefs.saveDirectory().isEmpty());
        prefs.setSaveDirectory(dir);
        assertEquals(Optional.of(dir.toAbsolutePath()), prefs.saveDirectory());
        assertTrue(prefs.lastDirectory().isEmpty());
    }

    @Test
    void uiSettingsPersistLanguageAndTextSize() {
        UiSettings settings = new UiSettings(prefs);
        settings.setLocale(Locale.FRENCH);
        assertEquals(Locale.FRENCH, settings.i18n().locale());
        assertEquals(Locale.FRENCH, prefs.locale());
        assertEquals("Fichier", settings.i18n().text("menu.file").get());

        settings.setFontSize(16);
        settings.increaseFontSize();
        assertEquals(17, settings.fontSize());
        assertEquals(17, prefs.fontSize());
        settings.decreaseFontSize();
        settings.decreaseFontSize();
        assertEquals(15, settings.fontSizeProperty().get());
        settings.resetFontSize();
        assertEquals(UserPreferences.DEFAULT_FONT_SIZE, settings.fontSize());
        settings.setFontSize(500);
        assertEquals(UserPreferences.MAX_FONT_SIZE, settings.fontSize());

        UiSettings reloaded = new UiSettings(prefs);
        assertEquals(Locale.FRENCH, reloaded.i18n().locale());
        assertEquals(UserPreferences.MAX_FONT_SIZE, reloaded.fontSize());
        assertTrue(UiSettings.PRESET_FONT_SIZES.contains(UserPreferences.DEFAULT_FONT_SIZE));
    }

    @Test
    void samplesDirectoryResolution(@TempDir Path dir) throws IOException {
        Path override = Files.createDirectories(dir.resolve("custom"));
        Path app = Files.createDirectories(dir.resolve("app/samples"));
        Path module = Files.createDirectories(dir.resolve("project/module"));
        Path projectSamples = Files.createDirectories(dir.resolve("project/samples"));

        assertEquals(
                Optional.of(override), SampleFiles.directory(override.toString(), null, module));
        assertEquals(
                Optional.of(app),
                SampleFiles.directory(" ", dir.resolve("app/PRIDE.exe").toString(), module));
        assertEquals(
                Optional.of(projectSamples),
                SampleFiles.directory(null, null, module),
                "falls back to the parent of the working directory (Maven module)");
        assertEquals(
                Optional.of(projectSamples),
                SampleFiles.directory(null, "", dir.resolve("project")));
        assertTrue(SampleFiles.directory(dir.resolve("nope").toString(), null, dir).isEmpty());
        assertTrue(SampleFiles.directory(null, null, dir.getRoot()).isEmpty());
    }

    @Test
    void samplesFolderOfTheProjectIsFound() {
        assertTrue(
                SampleFiles.directory().isPresent(),
                "run from the module or the project directory");
    }
}
