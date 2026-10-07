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

import com.bioinfo.proteomics.desktop.help.HelpWindow;
import com.bioinfo.proteomics.desktop.i18n.I18n;
import com.bioinfo.proteomics.desktop.i18n.Messages;
import com.bioinfo.proteomics.desktop.i18n.ViewTranslator;
import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.desktop.model.SampleFiles;
import com.bioinfo.proteomics.desktop.model.UiSettings;
import com.bioinfo.proteomics.desktop.view.Dialogs;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Controller of the main window: menus, keyboard shortcuts, help and status bar.
 *
 * <p>Each tab has its own controller ({@link PeptideTabController}, {@link SpectraTabController},
 * {@link UniProtTabController}, {@link PrideTabController}).
 */
public final class MainController implements TabNavigator {

    /** Help section of each tab, in tab order: F1 opens the section of the current tab. */
    public static final List<String> TAB_HELP_SECTIONS =
            List.of(
                    PeptideTabController.HELP_SECTION,
                    SpectraTabController.HELP_SECTION,
                    UniProtTabController.HELP_SECTION,
                    PrideTabController.HELP_SECTION);

    private static final int PEPTIDE_TAB =
            TAB_HELP_SECTIONS.indexOf(PeptideTabController.HELP_SECTION);

    private static final int SPECTRA_TAB =
            TAB_HELP_SECTIONS.indexOf(SpectraTabController.HELP_SECTION);

    private static final List<String> TEXT_SIZE_KEYS =
            List.of(
                    "menu.options.textSize.small",
                    "menu.options.textSize.normal",
                    "menu.options.textSize.large",
                    "menu.options.textSize.extraLarge");

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final DesktopModel model;
    private final UiSettings settings;
    private final I18n i18n;
    private final Consumer<String> openUrl;
    private final String stylesheet;
    private HelpWindow helpWindow;

    @FXML private BorderPane root;
    @FXML private Menu languageMenu;
    @FXML private Menu textSizeMenu;
    @FXML private MenuItem openSamplesItem;
    @FXML private TabPane mainTabs;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label statusLabel;

    /** Controllers of the included tabs, injected by the FXML loader. */
    @FXML private PeptideTabController peptideTabController;

    @FXML private SpectraTabController spectraTabController;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param settings language and text size
     * @param openUrl opens web pages, folders and e-mail links with the default application
     * @param stylesheet application stylesheet, also applied to dialogs and the help window
     */
    public MainController(
            DesktopModel model, UiSettings settings, Consumer<String> openUrl, String stylesheet) {
        this.model = model;
        this.settings = settings;
        this.i18n = settings.i18n();
        this.openUrl = openUrl;
        this.stylesheet = stylesheet;
    }

    @FXML
    private void initialize() {
        buildLanguageMenu();
        buildTextSizeMenu();
        openSamplesItem.setDisable(SampleFiles.directory().isEmpty());
        new ViewTranslator(i18n).translate(root);
        root.styleProperty()
                .bind(Bindings.format("-fx-font-size: %dpx;", settings.fontSizeProperty()));
        root.sceneProperty()
                .addListener(
                        (obs, old, scene) -> {
                            if (scene != null) {
                                installAccelerators(scene);
                            }
                        });

        busyIndicator.visibleProperty().bind(model.busyProperty());
        statusLabel
                .textProperty()
                .bind(
                        Bindings.createStringBinding(
                                () -> model.statusProperty().get().render(i18n.messages()),
                                model.statusProperty(),
                                i18n.messagesProperty()));
        model.statusProperty()
                .addListener(
                        (obs, old, status) ->
                                statusLabel.pseudoClassStateChanged(ERROR, status.error()));
    }

    // ----- Menus and shortcuts -----

    /**
     * Lists each language in itself ("English", "Français"), so that users find theirs whatever the
     * current language.
     */
    private void buildLanguageMenu() {
        ToggleGroup group = new ToggleGroup();
        for (Locale locale : Messages.SUPPORTED_LOCALES) {
            String name = locale.getDisplayLanguage(locale);
            String label = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
            RadioMenuItem item = new RadioMenuItem(label);
            item.setUserData(locale);
            item.setToggleGroup(group);
            item.setOnAction(e -> settings.setLocale(locale));
            languageMenu.getItems().add(item);
        }
        selectToggle(group, i18n.locale()::equals);
    }

    /** Preset text sizes, followed by zoom in, zoom out and reset. */
    private void buildTextSizeMenu() {
        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < UiSettings.PRESET_FONT_SIZES.size(); i++) {
            int size = UiSettings.PRESET_FONT_SIZES.get(i);
            RadioMenuItem item = new RadioMenuItem();
            item.textProperty().bind(i18n.text(TEXT_SIZE_KEYS.get(i)));
            item.setUserData(size);
            item.setToggleGroup(group);
            item.setOnAction(e -> settings.setFontSize(size));
            textSizeMenu.getItems().add(item);
        }
        textSizeMenu
                .getItems()
                .addAll(
                        new SeparatorMenuItem(),
                        menuItem(
                                "menu.options.zoomIn",
                                "Shortcut+Equals",
                                settings::increaseFontSize),
                        menuItem(
                                "menu.options.zoomOut",
                                "Shortcut+Minus",
                                settings::decreaseFontSize),
                        menuItem("menu.options.zoomReset", "Shortcut+0", settings::resetFontSize));
        settings.fontSizeProperty()
                .addListener(
                        (obs, old, size) ->
                                selectToggle(group, Integer.valueOf(size.intValue())::equals));
        selectToggle(group, Integer.valueOf(settings.fontSize())::equals);
    }

    private MenuItem menuItem(String key, String accelerator, Runnable action) {
        MenuItem item = new MenuItem();
        item.textProperty().bind(i18n.text(key));
        item.setAccelerator(KeyCombination.keyCombination(accelerator));
        item.setOnAction(e -> action.run());
        return item;
    }

    /** Shortcuts without a menu item: Ctrl+1…4 select the tabs, the numeric keypad zooms. */
    private void installAccelerators(Scene scene) {
        List<KeyCode> digits =
                List.of(KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4);
        for (int i = 0; i < digits.size(); i++) {
            int tab = i;
            shortcut(scene, digits.get(i), () -> mainTabs.getSelectionModel().select(tab));
        }
        shortcut(scene, KeyCode.ADD, settings::increaseFontSize);
        shortcut(scene, KeyCode.PLUS, settings::increaseFontSize);
        shortcut(scene, KeyCode.SUBTRACT, settings::decreaseFontSize);
        shortcut(scene, KeyCode.NUMPAD0, settings::resetFontSize);
    }

    private static void shortcut(Scene scene, KeyCode key, Runnable action) {
        scene.getAccelerators()
                .put(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN), action);
    }

    private static void selectToggle(ToggleGroup group, Predicate<Object> matches) {
        Toggle match =
                group.getToggles().stream()
                        .filter(toggle -> matches.test(toggle.getUserData()))
                        .findFirst()
                        .orElse(null);
        group.selectToggle(match);
    }

    // ----- Navigation between tabs -----

    @Override
    public void calculatePeptide(String peptide) {
        mainTabs.getSelectionModel().select(PEPTIDE_TAB);
        peptideTabController.calculate(peptide);
    }

    @Override
    public void annotateWithPeptide(String peptide) {
        mainTabs.getSelectionModel().select(SPECTRA_TAB);
        spectraTabController.annotateWith(peptide);
    }

    @Override
    public void showSpectra() {
        mainTabs.getSelectionModel().select(SPECTRA_TAB);
    }

    // ----- "File" menu -----

    @FXML
    private void onOpenSpectra() {
        showSpectra();
        spectraTabController.openSpectraFile();
    }

    @FXML
    private void onOpenSamplesFolder() {
        SampleFiles.directory().ifPresent(dir -> openUrl.accept(dir.toUri().toString()));
    }

    @FXML
    private void onQuit() {
        Platform.exit();
    }

    // ----- "Help" menu -----

    /**
     * Opens the user guide at a section.
     *
     * @param section identifier of the section, such as {@code "spectra"}
     */
    public void showHelp(String section) {
        helpWindow().show(section);
    }

    @FXML
    private void onHelp() {
        int tab = Math.max(0, mainTabs.getSelectionModel().getSelectedIndex());
        showHelp(TAB_HELP_SECTIONS.get(tab));
    }

    @FXML
    private void onShortcuts() {
        dialogs().showShortcuts(i18n.messages());
    }

    @FXML
    private void onAbout() {
        dialogs().showAbout(i18n.messages(), openUrl);
    }

    @FXML
    private void onOpenUniProtSite() {
        openUrl.accept("https://www.uniprot.org");
    }

    @FXML
    private void onOpenPrideSite() {
        openUrl.accept("https://www.ebi.ac.uk/pride/");
    }

    @FXML
    private void onOpenProFormaSite() {
        openUrl.accept("https://www.psidev.info/proforma");
    }

    /**
     * Returns the help window, created on first use.
     *
     * @return the help window of this main window
     */
    public HelpWindow helpWindow() {
        if (helpWindow == null) {
            helpWindow = new HelpWindow(root.getScene().getWindow(), settings, stylesheet, openUrl);
        }
        return helpWindow;
    }

    private Dialogs dialogs() {
        return new Dialogs(root.getScene().getWindow(), root, stylesheet);
    }
}
