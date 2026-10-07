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

import com.bioinfo.proteomics.client.PrideClient;
import com.bioinfo.proteomics.desktop.i18n.I18n;
import com.bioinfo.proteomics.desktop.i18n.Messages;
import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.desktop.model.PrideSearchPage;
import com.bioinfo.proteomics.desktop.model.UiSettings;
import com.bioinfo.proteomics.desktop.view.Dialogs;
import com.bioinfo.proteomics.model.PrideFile;
import com.bioinfo.proteomics.model.PrideProject;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.parser.SpectrumFileFormat;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Controller of the "PRIDE" tab: searches PRIDE Archive projects, displays the details and files of
 * a project, and downloads its spectrum files into the "Spectra" tab.
 */
public final class PrideTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "pride";

    /** Files above this size, or of unknown size, are downloaded only after confirmation. */
    static final long LARGE_FILE_BYTES = 50L * 1024 * 1024;

    /** CSS pseudo-class greying out the files that the application cannot read. */
    private static final PseudoClass UNREADABLE = PseudoClass.getPseudoClass("unreadable");

    private static final String NONE = "—";
    private static final String DOI_RESOLVER = "https://doi.org/";
    private static final String PRIDE_PROJECT_PAGE =
            "https://www.ebi.ac.uk/pride/archive/projects/";

    private final DesktopModel model;
    private final UiSettings settings;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final TabNavigator navigator;
    private final Consumer<String> openUrl;
    private final String stylesheet;

    @FXML private TextField queryField;
    @FXML private Label resultsLabel;
    @FXML private TableView<PrideProject> projectTable;
    @FXML private Label resultsPlaceholder;
    @FXML private TableColumn<PrideProject, String> projectAccessionColumn;
    @FXML private TableColumn<PrideProject, String> projectTitleColumn;
    @FXML private TableColumn<PrideProject, String> projectDateColumn;
    @FXML private Button previousPageButton;
    @FXML private Label pageLabel;
    @FXML private Button nextPageButton;
    @FXML private Label projectPlaceholder;
    @FXML private ScrollPane projectSheet;
    @FXML private Label projectTitleLabel;
    @FXML private Hyperlink doiLink;
    @FXML private Label datesLabel;
    @FXML private Label organismsLabel;
    @FXML private Label instrumentsLabel;
    @FXML private Label keywordsLabel;
    @FXML private Label submittersLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label filesLabel;
    @FXML private CheckBox readableOnlyBox;
    @FXML private TableView<PrideFile> fileTable;
    @FXML private TableColumn<PrideFile, String> fileNameColumn;
    @FXML private TableColumn<PrideFile, String> fileCategoryColumn;
    @FXML private TableColumn<PrideFile, Long> fileSizeColumn;
    @FXML private Button prideDownloadButton;
    @FXML private Button prideSaveButton;
    @FXML private ProgressBar downloadProgressBar;
    @FXML private Button cancelButton;

    /** Files of the project, filtered by the "readable files only" box. */
    private FilteredList<PrideFile> visibleFiles;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param settings current language and saved folders
     * @param showHelp opens the user guide at the given section
     * @param navigator opens the "Spectra" tab once a file is loaded
     * @param openUrl opens web pages with the default browser
     * @param stylesheet application stylesheet, applied to the confirmation dialog
     */
    public PrideTabController(
            DesktopModel model,
            UiSettings settings,
            Consumer<String> showHelp,
            TabNavigator navigator,
            Consumer<String> openUrl,
            String stylesheet) {
        this.model = model;
        this.settings = settings;
        this.i18n = settings.i18n();
        this.showHelp = showHelp;
        this.navigator = navigator;
        this.openUrl = openUrl;
        this.stylesheet = stylesheet;
    }

    @FXML
    private void initialize() {
        initProjectTable();
        initFileTable();
        initDownloadControls();
        model.prideSearchPageProperty()
                .addListener(
                        (obs, old, page) -> {
                            projectTable
                                    .getItems()
                                    .setAll(page == null ? List.of() : page.projects());
                            selectCurrentProject();
                            renderResults();
                        });
        model.prideProjectProperty().addListener((obs, old, project) -> renderProject());
        i18n.messagesProperty()
                .addListener(
                        (obs, old, messages) -> {
                            renderResults();
                            renderProject();
                            fileTable.refresh();
                        });
        renderResults();
        renderProject();
    }

    // ----- Search results -----

    /** Shows the projects of the current page; choosing one displays its details and files. */
    private void initProjectTable() {
        Controls.column(projectAccessionColumn, PrideProject::accession);
        Controls.column(projectTitleColumn, PrideProject::title);
        Controls.column(projectDateColumn, PrideProject::publicationDate);
        projectTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, old, project) -> {
                            if (project != null && project != model.prideProjectProperty().get()) {
                                model.selectPrideProject(project);
                            }
                        });
    }

    /** Highlights the displayed project when it is one of the results, e.g. after an accession. */
    private void selectCurrentProject() {
        PrideProject current = model.prideProjectProperty().get();
        if (current != null && projectTable.getItems().contains(current)) {
            projectTable.getSelectionModel().select(current);
        }
    }

    /** Fills the result table and the page controls from the last search. */
    private void renderResults() {
        Messages m = i18n.messages();
        PrideSearchPage page = model.prideSearchPageProperty().get();
        if (page == null) {
            resultsLabel.setText("");
            resultsPlaceholder.setText(m.get("pride.resultsPlaceholder"));
            pageLabel.setText("");
            previousPageButton.setDisable(true);
            nextPageButton.setDisable(true);
            return;
        }
        resultsLabel.setText(
                page.projects().isEmpty()
                        ? ""
                        : m.get(
                                "pride.results",
                                page.firstRank(),
                                page.lastRank(),
                                page.keywords()));
        resultsPlaceholder.setText(m.get("pride.noResultsFor", page.keywords()));
        pageLabel.setText(m.get("pride.page", page.page() + 1));
        previousPageButton.setDisable(page.page() == 0);
        nextPageButton.setDisable(page.lastPage());
    }

    @FXML
    private void onSearch() {
        String query = queryField.getText() == null ? "" : queryField.getText().strip();
        if (query.isEmpty()) {
            queryField.requestFocus();
            model.reportError("error.prideQueryEmpty");
        } else if (PrideClient.isValidProjectAccession(query)) {
            model.fetchPrideProject(query);
        } else {
            model.searchPrideProjects(query, 0);
        }
    }

    @FXML
    private void onPreviousPage() {
        changePage(-1);
    }

    @FXML
    private void onNextPage() {
        changePage(1);
    }

    private void changePage(int delta) {
        PrideSearchPage page = model.prideSearchPageProperty().get();
        if (page != null) {
            model.searchPrideProjects(page.keywords(), Math.max(0, page.page() + delta));
        }
    }

    // ----- Project details -----

    /** Displays the details of the current project, or a hint when there is none. */
    private void renderProject() {
        Messages m = i18n.messages();
        PrideProject p = model.prideProjectProperty().get();
        projectSheet.setVisible(p != null);
        projectPlaceholder.setVisible(p == null);
        renderFilesLabel();
        if (p == null) {
            return;
        }
        projectTitleLabel.setText(p.accession() + " — " + p.title());
        doiLink.setText(p.doi().isEmpty() ? NONE : p.doi());
        doiLink.setDisable(p.doi().isEmpty());
        datesLabel.setText(m.get("pride.datesValue", p.submissionDate(), p.publicationDate()));
        organismsLabel.setText(joined(p.organisms()));
        instrumentsLabel.setText(joined(p.instruments()));
        keywordsLabel.setText(joined(p.keywords()));
        submittersLabel.setText(joined(p.submitters()));
        descriptionLabel.setText(p.description());
        projectSheet.setVvalue(0);
    }

    private static String joined(List<String> values) {
        return values.isEmpty() ? NONE : String.join(", ", values);
    }

    @FXML
    private void onOpenDoi() {
        PrideProject p = model.prideProjectProperty().get();
        if (p != null && !p.doi().isEmpty()) {
            openUrl.accept(DOI_RESOLVER + p.doi());
        }
    }

    @FXML
    private void onOpenPridePage() {
        PrideProject p = model.prideProjectProperty().get();
        if (p != null) {
            openUrl.accept(PRIDE_PROJECT_PAGE + p.accession());
        }
    }

    // ----- Files -----

    /**
     * Lists the files of the project; readable files are downloaded with a double-click or Enter,
     * the others are greyed out.
     */
    private void initFileTable() {
        visibleFiles = new FilteredList<>(model.prideFiles());
        visibleFiles
                .predicateProperty()
                .bind(
                        Bindings.createObjectBinding(
                                () ->
                                        readableOnlyBox.isSelected()
                                                ? PrideTabController::isReadable
                                                : file -> true,
                                readableOnlyBox.selectedProperty()));
        fileTable.setItems(visibleFiles);
        visibleFiles.addListener(
                (ListChangeListener<PrideFile>)
                        change -> {
                            renderFilesLabel();
                            Platform.runLater(this::selectFirstReadableFile);
                        });

        Controls.column(fileNameColumn, PrideFile::name);
        Controls.column(fileCategoryColumn, PrideFile::category);
        Controls.column(fileSizeColumn, PrideFile::sizeBytes);
        fileSizeColumn.setCellFactory(column -> new SizeCell());
        fileTable.setRowFactory(table -> new FileRow());
        fileTable.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER) {
                        onDownload();
                    }
                });
    }

    /** Shows the number of files listed, once a project is displayed. */
    private void renderFilesLabel() {
        Messages m = i18n.messages();
        filesLabel.setText(
                model.prideProjectProperty().get() == null
                        ? m.get("pride.filesTitle")
                        : m.get("pride.files", visibleFiles.size()));
    }

    /** Preselects the first readable file, so that it can be opened at once. */
    private void selectFirstReadableFile() {
        if (fileTable.getSelectionModel().getSelectedItem() == null) {
            visibleFiles.stream()
                    .filter(PrideTabController::isReadable)
                    .findFirst()
                    .ifPresent(file -> fileTable.getSelectionModel().select(file));
        }
    }

    /** Tells whether the application reads a file: MGF or JSON, with a download address. */
    static boolean isReadable(PrideFile file) {
        return file.downloadUriOptional().isPresent()
                && SpectrumFileFormat.fromFileName(file.name()).isPresent();
    }

    /** Enables the download button for readable files; shows the progress while downloading. */
    private void initDownloadControls() {
        prideDownloadButton
                .disableProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> {
                                    PrideFile file =
                                            fileTable.getSelectionModel().getSelectedItem();
                                    return model.downloadingProperty().get()
                                            || file == null
                                            || !isReadable(file);
                                },
                                fileTable.getSelectionModel().selectedItemProperty(),
                                model.downloadingProperty()));
        prideSaveButton
                .disableProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> {
                                    PrideFile file =
                                            fileTable.getSelectionModel().getSelectedItem();
                                    return model.downloadingProperty().get()
                                            || file == null
                                            || file.downloadUriOptional().isEmpty();
                                },
                                fileTable.getSelectionModel().selectedItemProperty(),
                                model.downloadingProperty()));
        downloadProgressBar.progressProperty().bind(model.downloadProgressProperty());
        for (var control : List.of(downloadProgressBar, cancelButton)) {
            control.visibleProperty().bind(model.downloadingProperty());
            control.managedProperty().bind(model.downloadingProperty());
        }
    }

    /** Downloads the selected file and opens its spectra in the "Spectra" tab. */
    @FXML
    private void onDownload() {
        PrideFile file = fileTable.getSelectionModel().getSelectedItem();
        if (file == null || !isReadable(file) || model.downloadingProperty().get()) {
            return;
        }
        afterSizeCheck(
                file,
                size ->
                        model.downloadPrideFile(
                                file, size, ParseMode.LENIENT, navigator::showSpectra));
    }

    /** Saves the selected file, whatever its format, in a folder chosen by the user. */
    @FXML
    private void onSave() {
        PrideFile file = fileTable.getSelectionModel().getSelectedItem();
        if (file == null
                || file.downloadUriOptional().isEmpty()
                || model.downloadingProperty().get()) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(i18n.messages().get("dialog.save.title", file.name()));
        settings.preferences()
                .saveDirectory()
                .or(PrideTabController::downloadsFolder)
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);
        File folder = chooser.showDialog(fileTable.getScene().getWindow());
        if (folder == null) {
            return;
        }
        Path directory = folder.toPath();
        settings.preferences().setSaveDirectory(directory);
        afterSizeCheck(file, size -> model.savePrideFile(file, size, directory));
    }

    /** The "Downloads" folder of the user, when there is one. */
    private static Optional<Path> downloadsFolder() {
        Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
        return Files.isDirectory(downloads) ? Optional.of(downloads) : Optional.empty();
    }

    /**
     * Asks the server for the exact size of a file, then runs the download: large files, and files
     * of unknown size, need a confirmation first. A file already downloaded needs neither.
     */
    private void afterSizeCheck(PrideFile file, LongConsumer download) {
        if (model.localCopy(file).isPresent()) {
            download.accept(-1);
            return;
        }
        model.checkPrideFileSize(
                file,
                size -> {
                    boolean small = size >= 0 && size <= LARGE_FILE_BYTES;
                    if (small || confirmDownload(file, size)) {
                        download.accept(size);
                    }
                });
    }

    private boolean confirmDownload(PrideFile file, long size) {
        Messages m = i18n.messages();
        Dialogs dialogs =
                new Dialogs(
                        fileTable.getScene().getWindow(),
                        fileTable.getScene().getRoot(),
                        stylesheet);
        return dialogs.confirmDownload(m, file.name(), FileSizes.format(size, m));
    }

    @FXML
    private void onCancelDownload() {
        model.cancelDownload();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    /** Cell showing a file size with a unit ("16.4 MB"), or a dash when it is unknown. */
    private final class SizeCell extends TableCell<PrideFile, Long> {
        @Override
        protected void updateItem(Long size, boolean empty) {
            super.updateItem(size, empty);
            if (empty || size == null) {
                setText(null);
            } else {
                setText(size > 0 ? FileSizes.format(size, i18n.messages()) : NONE);
            }
        }
    }

    /** Row greyed out when the file cannot be read; a double-click downloads a readable file. */
    private final class FileRow extends TableRow<PrideFile> {
        FileRow() {
            setOnMouseClicked(
                    event -> {
                        if (event.getButton() == MouseButton.PRIMARY
                                && event.getClickCount() == 2
                                && !isEmpty()) {
                            onDownload();
                        }
                    });
        }

        @Override
        protected void updateItem(PrideFile file, boolean empty) {
            super.updateItem(file, empty);
            pseudoClassStateChanged(UNREADABLE, !empty && file != null && !isReadable(file));
        }
    }
}
