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

package com.bioinfo.proteomics.desktop.controller;

import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.desktop.model.UiSettings;

import javafx.util.Callback;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Creates the controllers declared in the FXML views and gives them the model they work on.
 *
 * <p>The main view is loaded first, so the main controller exists when the tabs are created: the
 * "?" button of each tab opens the help through it, and the tabs send data to one another through
 * it (see {@link TabNavigator}).
 */
public final class ControllerFactory implements Callback<Class<?>, Object> {

    private final DesktopModel model;
    private final UiSettings settings;
    private final Consumer<String> openUrl;
    private final String stylesheet;
    private MainController main;

    /**
     * Creates a factory for one main window.
     *
     * @param model application state shared by all the controllers
     * @param settings language and text size
     * @param openUrl opens web pages, folders and e-mail links with the default application
     * @param stylesheet application stylesheet, also applied to dialogs and the help window
     */
    public ControllerFactory(
            DesktopModel model, UiSettings settings, Consumer<String> openUrl, String stylesheet) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.openUrl = Objects.requireNonNull(openUrl, "openUrl");
        this.stylesheet = Objects.requireNonNull(stylesheet, "stylesheet");
    }

    @Override
    public Object call(Class<?> type) {
        if (type == MainController.class) {
            main = new MainController(model, settings, openUrl, stylesheet);
            return main;
        }
        Consumer<String> showHelp = section -> main.showHelp(section);
        if (type == PeptideTabController.class) {
            return new PeptideTabController(model, settings.i18n(), showHelp);
        }
        if (type == SpectraTabController.class) {
            return new SpectraTabController(model, settings, showHelp, main);
        }
        if (type == UniProtTabController.class) {
            return new UniProtTabController(model, settings.i18n(), showHelp, main);
        }
        if (type == FooterController.class) {
            return new FooterController(settings.i18n(), openUrl);
        }
        if (type == PrideTabController.class) {
            return new PrideTabController(model, settings, showHelp, main, openUrl, stylesheet);
        }
        throw new IllegalArgumentException("Unexpected controller " + type);
    }
}
