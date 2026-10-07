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

import com.bioinfo.proteomics.desktop.AppInfo;
import com.bioinfo.proteomics.desktop.i18n.I18n;

import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;

import java.util.function.Consumer;

/**
 * Controller of the footer of the main window: opens the author's website (in the current
 * language), writes an e-mail to the author, opens the licence and the Ko-fi support page.
 */
public final class FooterController {

    private final I18n i18n;
    private final Consumer<String> openUrl;

    @FXML private Hyperlink emailLink;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param i18n current language, which chooses the version of the website
     * @param openUrl opens web pages and e-mail links with the default application
     */
    public FooterController(I18n i18n, Consumer<String> openUrl) {
        this.i18n = i18n;
        this.openUrl = openUrl;
    }

    @FXML
    private void initialize() {
        emailLink.setText(AppInfo.CONTACT_EMAIL);
    }

    @FXML
    private void onOpenWebsite() {
        openUrl.accept(i18n.messages().get("about.website"));
    }

    @FXML
    private void onWriteEmail() {
        openUrl.accept("mailto:" + AppInfo.CONTACT_EMAIL);
    }

    @FXML
    private void onSupport() {
        openUrl.accept(AppInfo.KOFI_URL);
    }

    @FXML
    private void onReadLicense() {
        openUrl.accept(AppInfo.LICENSE_URL);
    }
}
