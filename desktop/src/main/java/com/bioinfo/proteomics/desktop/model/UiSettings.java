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

import com.bioinfo.proteomics.desktop.i18n.I18n;
import com.bioinfo.proteomics.desktop.prefs.UserPreferences;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Language and text size of the interface, saved in the user preferences as soon as they change.
 */
public final class UiSettings {

    /** Text sizes offered by the Options menu, in pixels: small, normal, large, extra large. */
    public static final List<Integer> PRESET_FONT_SIZES =
            List.of(11, UserPreferences.DEFAULT_FONT_SIZE, 16, 20);

    private static final int FONT_STEP = 1;

    private final UserPreferences preferences;
    private final I18n i18n;
    private final ReadOnlyIntegerWrapper fontSize;

    /**
     * Creates the settings with the values saved in the preferences.
     *
     * @param preferences storage of the user settings
     */
    public UiSettings(UserPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.i18n = new I18n(preferences.locale());
        this.fontSize = new ReadOnlyIntegerWrapper(preferences.fontSize());
    }

    /** Returns the current language of the interface. */
    public I18n i18n() {
        return i18n;
    }

    /** Returns the storage of the user settings. */
    public UserPreferences preferences() {
        return preferences;
    }

    /** Returns the text size in pixels, as an observable property. */
    public ReadOnlyIntegerProperty fontSizeProperty() {
        return fontSize.getReadOnlyProperty();
    }

    /** Returns the text size in pixels. */
    public int fontSize() {
        return fontSize.get();
    }

    /** Sets the text size, clamped to the supported range. */
    public void setFontSize(int size) {
        int clamped = UserPreferences.clampFontSize(size);
        fontSize.set(clamped);
        preferences.setFontSize(clamped);
    }

    /** Enlarges the text by one pixel. */
    public void increaseFontSize() {
        setFontSize(fontSize() + FONT_STEP);
    }

    /** Shrinks the text by one pixel. */
    public void decreaseFontSize() {
        setFontSize(fontSize() - FONT_STEP);
    }

    /** Restores the default text size. */
    public void resetFontSize() {
        setFontSize(UserPreferences.DEFAULT_FONT_SIZE);
    }

    /** Switches the language of the interface; unsupported languages fall back to English. */
    public void setLocale(Locale locale) {
        i18n.setLocale(locale);
        preferences.setLocale(i18n.locale());
    }
}
