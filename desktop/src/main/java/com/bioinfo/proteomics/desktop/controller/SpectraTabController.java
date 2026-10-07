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

import com.bioinfo.proteomics.desktop.i18n.I18n;
import com.bioinfo.proteomics.desktop.i18n.Messages;
import com.bioinfo.proteomics.desktop.model.ComparisonMode;
import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.desktop.model.ReferenceOrigin;
import com.bioinfo.proteomics.desktop.model.SampleFiles;
import com.bioinfo.proteomics.desktop.model.UiSettings;
import com.bioinfo.proteomics.mass.AnnotationResult;
import com.bioinfo.proteomics.mass.PeakAnnotation;
import com.bioinfo.proteomics.mass.SpectrumSimilarity;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.Peak;
import com.bioinfo.proteomics.model.Spectrum;
import com.bioinfo.proteomics.parser.ParseIssue;
import com.bioinfo.proteomics.parser.ParseMode;

import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controller of the "Spectra" tab: loads MS/MS spectra from a JSON or MGF file or from PRIDE (USI),
 * draws them and annotates their peaks with the fragment ions of a peptide.
 */
public final class SpectraTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "spectra";

    /** CSS pseudo-class highlighting the parsing errors in the issue list. */
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    /** Placeholder shown for missing spectrum attributes. */
    private static final String NONE = "—";

    private final DesktopModel model;
    private final UiSettings settings;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final TabNavigator navigator;

    @FXML private ComboBox<ParseMode> parseModeBox;
    @FXML private TextField usiField;
    @FXML private ListView<Spectrum> spectrumList;
    @FXML private Label spectrumDetailsLabel;
    @FXML private TextField annotationPeptideField;
    @FXML private TextField toleranceField;
    @FXML private ComboBox<Tolerance.Unit> toleranceUnitBox;
    @FXML private ComboBox<Spectrum> referenceBox;
    @FXML private ComboBox<ComparisonMode> comparisonModeBox;
    @FXML private Button clearReferenceButton;
    @FXML private Label similarityLabel;
    @FXML private LineChart<Number, Number> spectrumChart;
    @FXML private Pane chartOverlay;
    @FXML private Label peakInfoLabel;
    @FXML private TableView<PeakAnnotation> annotationTable;
    @FXML private TableColumn<PeakAnnotation, String> annotationLabelColumn;
    @FXML private TableColumn<PeakAnnotation, Double> annotationTheoColumn;
    @FXML private TableColumn<PeakAnnotation, Double> annotationObsColumn;
    @FXML private TableColumn<PeakAnnotation, Double> annotationErrorColumn;
    @FXML private TableColumn<PeakAnnotation, Double> annotationIntensityColumn;
    @FXML private TitledPane issuesPane;
    @FXML private ListView<ParseIssue> issueList;

    private SpectrumChartZoom chartZoom;

    /** Spectrum currently drawn, to keep the zoom when only its annotation changes. */
    private Spectrum drawnSpectrum;

    /** Peak clicked by the user, or {@code null}. */
    private Peak selectedPeak;

    /** Set while the table selection follows a click on the chart, to avoid a feedback loop. */
    private boolean syncingSelection;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param settings language and user preferences (last folder opened)
     * @param showHelp opens the user guide at the given section
     * @param navigator sends the peptide of a spectrum to the "Peptide" tab
     */
    public SpectraTabController(
            DesktopModel model,
            UiSettings settings,
            Consumer<String> showHelp,
            TabNavigator navigator) {
        this.model = model;
        this.settings = settings;
        this.i18n = settings.i18n();
        this.showHelp = showHelp;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        Controls.translatedChoices(
                parseModeBox,
                ParseMode.values(),
                ParseMode.LENIENT,
                mode -> "parseMode." + mode.name(),
                i18n);
        Controls.translatedChoices(
                toleranceUnitBox,
                Tolerance.Unit.values(),
                Tolerance.Unit.PPM,
                unit -> "toleranceUnit." + unit.name(),
                i18n);

        Controls.column(annotationLabelColumn, PeakAnnotation::label);
        Controls.decimalColumn(annotationTheoColumn, a -> a.ion().mz(), 5);
        Controls.decimalColumn(annotationObsColumn, a -> a.peak().mz(), 5);
        Controls.decimalColumn(annotationErrorColumn, PeakAnnotation::errorPpm, 2);
        Controls.decimalColumn(annotationIntensityColumn, a -> a.peak().intensity(), 1);

        initSpectrumList();
        initIssueList();
        initChart();
        initComparison();

        model.annotationProperty().addListener((obs, old, result) -> render());
        i18n.messagesProperty()
                .addListener(
                        (obs, old, messages) -> {
                            issueList.refresh();
                            showSimilarity();
                            render();
                        });
        render();
    }

    /**
     * Sets up the comparison with a reference spectrum: choice of the reference among those loaded,
     * drawing mode, similarity score, and tolerance shared with the annotation.
     */
    private void initComparison() {
        Controls.translatedChoices(
                comparisonModeBox,
                ComparisonMode.values(),
                ComparisonMode.MIRROR,
                mode -> "comparisonMode." + mode.name(),
                i18n);
        referenceBox.setItems(model.referenceSpectra());
        referenceBox.setCellFactory(list -> new ReferenceCell());
        referenceBox.setButtonCell(new ReferenceCell());
        i18n.messagesProperty()
                .addListener(
                        (obs, old, messages) -> referenceBox.setButtonCell(new ReferenceCell()));
        referenceBox.valueProperty().bindBidirectional(model.referenceSpectrumProperty());
        BooleanBinding noReference = model.referenceSpectrumProperty().isNull();
        referenceBox.disableProperty().bind(noReference);
        comparisonModeBox.disableProperty().bind(noReference);
        clearReferenceButton.disableProperty().bind(noReference);
        similarityLabel.managedProperty().bind(similarityLabel.visibleProperty());

        model.referenceSpectrumProperty().addListener((obs, old, reference) -> render());
        comparisonModeBox.valueProperty().addListener((obs, old, mode) -> render());
        model.similarityProperty().addListener((obs, old, similarity) -> showSimilarity());
        toleranceField.textProperty().addListener((obs, old, text) -> shareTolerance());
        toleranceUnitBox.valueProperty().addListener((obs, old, unit) -> shareTolerance());
        shareTolerance();
        showSimilarity();
    }

    /** Uses the tolerance typed for the annotation to compare the spectra, when it is valid. */
    private void shareTolerance() {
        double tolerance = parseTolerance(toleranceField.getText());
        if (tolerance > 0 && toleranceUnitBox.getValue() != null) {
            model.setComparisonTolerance(new Tolerance(tolerance, toleranceUnitBox.getValue()));
        }
    }

    /** Shows the similarity of the selected and reference spectra, hidden without reference. */
    private void showSimilarity() {
        SpectrumSimilarity similarity = model.similarityProperty().get();
        similarityLabel.setVisible(similarity != null);
        if (similarity != null) {
            Messages m = i18n.messages();
            Tolerance tolerance = model.comparisonToleranceProperty().get();
            similarityLabel.setText(
                    m.get(
                            "spectra.similarity",
                            similarity.cosine(),
                            similarity.matchedPeaks(),
                            tolerance.value(),
                            m.get("toleranceUnit." + tolerance.unit().name())));
        }
    }

    @FXML
    private void onOpenReference() {
        chooseSpectraFile()
                .ifPresent(path -> model.loadReferenceSpectra(path, parseModeBox.getValue()));
    }

    @FXML
    private void onTheoretical() {
        model.showTheoreticalReference(annotationPeptideField.getText());
    }

    @FXML
    private void onClearReference() {
        model.clearReference();
    }

    /** Sets up zooming and peak selection, kept in sync with the annotation table. */
    private void initChart() {
        spectrumChart.setAxisSortingPolicy(LineChart.SortingPolicy.NONE);
        chartZoom =
                new SpectrumChartZoom(
                        spectrumChart, chartOverlay, peak -> selectPeak(peak.orElse(null), true));
        annotationTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, old, row) -> {
                            if (row != null && !syncingSelection) {
                                selectPeak(row.peak(), false);
                            }
                        });
    }

    /**
     * Keeps the selection of the list and the selected spectrum of the model in sync. A right-click
     * on an identified spectrum sends its peptide to the "Peptide" tab; any spectrum can become the
     * reference compared with the others.
     */
    private void initSpectrumList() {
        spectrumList.setItems(model.spectra());
        spectrumList.setCellFactory(list -> new SpectrumCell());
        MenuItem calculate =
                Controls.menuItem(
                        "action.calculatePeptide",
                        i18n,
                        () -> selectedPeptide().ifPresent(navigator::calculatePeptide));
        MenuItem copy =
                Controls.menuItem(
                        "action.copySequence",
                        i18n,
                        () -> selectedPeptide().ifPresent(Controls::copyToClipboard));
        MenuItem reference =
                Controls.menuItem(
                        "action.useAsReference",
                        i18n,
                        () ->
                                Optional.ofNullable(model.selectedSpectrumProperty().get())
                                        .ifPresent(model::useAsReference));
        ContextMenu menu = new ContextMenu(calculate, copy, new SeparatorMenuItem(), reference);
        menu.setOnShowing(
                e -> {
                    boolean noPeptide = selectedPeptide().isEmpty();
                    calculate.setDisable(noPeptide);
                    copy.setDisable(noPeptide);
                    reference.setDisable(model.selectedSpectrumProperty().get() == null);
                });
        spectrumList.setContextMenu(menu);
        spectrumList
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, s) -> model.selectedSpectrumProperty().set(s));
        model.selectedSpectrumProperty()
                .addListener(
                        (obs, old, s) -> {
                            if (s != null
                                    && spectrumList.getSelectionModel().getSelectedItem() != s) {
                                spectrumList.getSelectionModel().select(s);
                            }
                            render();
                        });
    }

    /** Shows the parsing issues, and expands their pane as soon as there is one. */
    private void initIssueList() {
        issueList.setItems(model.issues());
        issueList.setCellFactory(list -> new IssueCell());
        model.issues()
                .addListener(
                        (ListChangeListener<ParseIssue>)
                                change -> issuesPane.setExpanded(!model.issues().isEmpty()));
    }

    /**
     * Asks the user for a JSON or MGF file of spectra and loads it. Also called by the "File ›
     * Open" menu.
     */
    public void openSpectraFile() {
        chooseSpectraFile().ifPresent(path -> model.loadSpectra(path, parseModeBox.getValue()));
    }

    /**
     * Asks the user for a JSON or MGF file of spectra, starting in the last folder used.
     *
     * @return the file chosen; empty if the user cancelled
     */
    private Optional<Path> chooseSpectraFile() {
        Messages m = i18n.messages();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(m.get("dialog.open.title"));
        chooser.getExtensionFilters()
                .addAll(
                        new FileChooser.ExtensionFilter(
                                m.get("file.spectra"), "*.json", "*.mgf", "*.json.gz", "*.mgf.gz"),
                        new FileChooser.ExtensionFilter(m.get("file.json"), "*.json"),
                        new FileChooser.ExtensionFilter(m.get("file.mgf"), "*.mgf", "*.mgf.gz"),
                        new FileChooser.ExtensionFilter(m.get("file.all"), "*.*"));
        settings.preferences()
                .lastDirectory()
                .or(SampleFiles::directory)
                .map(Path::toFile)
                .ifPresent(chooser::setInitialDirectory);
        File file = chooser.showOpenDialog(spectrumList.getScene().getWindow());
        if (file == null) {
            return Optional.empty();
        }
        Path path = file.toPath();
        if (path.getParent() != null) {
            settings.preferences().setLastDirectory(path.getParent());
        }
        return Optional.of(path);
    }

    @FXML
    private void onOpenSpectra() {
        openSpectraFile();
    }

    @FXML
    private void onLoadUsi() {
        model.loadSpectrumByUsi(usiField.getText());
    }

    @FXML
    private void onAnnotate() {
        double tolerance = parseTolerance(toleranceField.getText());
        if (!(tolerance > 0)) {
            toleranceField.requestFocus();
            model.reportError("error.tolerance");
            return;
        }
        model.annotateSelected(
                annotationPeptideField.getText(),
                new Tolerance(tolerance, toleranceUnitBox.getValue()),
                MassType.MONOISOTOPIC);
    }

    /**
     * Annotates the selected spectrum with a peptide sent from another tab.
     *
     * @param peptide peptide in ProForma notation
     */
    public void annotateWith(String peptide) {
        annotationPeptideField.setText(peptide);
        onAnnotate();
    }

    /** Peptide identified in the selected spectrum, if any. */
    private Optional<String> selectedPeptide() {
        return Optional.ofNullable(model.selectedSpectrumProperty().get())
                .flatMap(Spectrum::peptideOptional);
    }

    @FXML
    private void onFullView() {
        chartZoom.showAll();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    /**
     * Selects a peak: highlights it on the chart, describes it below the chart and, when it was
     * clicked on the chart, selects its row in the annotation table.
     *
     * @param peak peak selected, or {@code null} to clear the selection
     * @param fromChart {@code true} if the user clicked the chart, {@code false} for the table
     */
    private void selectPeak(Peak peak, boolean fromChart) {
        selectedPeak = peak;
        chartZoom.highlight(peak);
        if (fromChart) {
            syncingSelection = true;
            Optional<PeakAnnotation> row = annotationOf(peak);
            row.ifPresentOrElse(
                    r -> {
                        annotationTable.getSelectionModel().select(r);
                        annotationTable.scrollTo(r);
                    },
                    () -> annotationTable.getSelectionModel().clearSelection());
            syncingSelection = false;
        }
        showPeakInfo();
    }

    /** Annotation of a peak in the current annotation result, if any. */
    private Optional<PeakAnnotation> annotationOf(Peak peak) {
        AnnotationResult annotation = model.annotationProperty().get();
        if (peak == null || annotation == null) {
            return Optional.empty();
        }
        return annotation.annotations().stream().filter(a -> a.peak().equals(peak)).findFirst();
    }

    /** Describes the selected peak below the chart, or explains how to use the chart. */
    private void showPeakInfo() {
        Messages m = i18n.messages();
        Spectrum spectrum = model.selectedSpectrumProperty().get();
        if (spectrum == null) {
            peakInfoLabel.setText("");
            return;
        }
        if (selectedPeak == null) {
            peakInfoLabel.setText(m.get("spectra.chartHint"));
            return;
        }
        double basePeak = spectrum.peaks().stream().mapToDouble(Peak::intensity).max().orElse(0);
        double relative = basePeak > 0 ? 100 * selectedPeak.intensity() / basePeak : 0;
        String match =
                annotationOf(selectedPeak)
                        .map(
                                a ->
                                        m.get(
                                                "spectra.peakMatched",
                                                a.label(),
                                                format("%.5f", a.ion().mz()),
                                                format("%+.2f", a.errorPpm())))
                        .orElseGet(
                                () ->
                                        m.get(
                                                model.annotationProperty().get() == null
                                                        ? "spectra.peakNotAnnotated"
                                                        : "spectra.peakUnmatched"));
        peakInfoLabel.setText(
                m.get(
                        "spectra.peakInfo",
                        format("%.5f", selectedPeak.mz()),
                        format("%.1f", selectedPeak.intensity()),
                        format("%.1f", relative),
                        match));
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    /**
     * Reads the tolerance typed by the user, who may use a comma as decimal separator.
     *
     * @return the tolerance, or -1 if the text is not a number
     */
    private static double parseTolerance(String text) {
        try {
            return Double.parseDouble(text.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Draws the selected spectrum, its b and y ions if it was annotated, and the reference spectrum
     * if there is one. The zoom and the selected peak are kept while the same spectrum is redrawn.
     *
     * <p>The series always come in the same order (peaks, b ions, y ions, reference), because the
     * stylesheet colours them by position.
     */
    private void render() {
        Spectrum spectrum = model.selectedSpectrumProperty().get();
        AnnotationResult annotation = model.annotationProperty().get();
        Spectrum reference = model.referenceSpectrumProperty().get();
        boolean sameSpectrum = spectrum != null && spectrum == drawnSpectrum;
        drawnSpectrum = spectrum;
        if (!sameSpectrum) {
            selectedPeak = null;
        }
        spectrumChart.getData().clear();
        annotationTable.getItems().clear();
        if (spectrum == null) {
            spectrumDetailsLabel.setText("");
            chartZoom.setReference(List.of(), false);
            chartZoom.setPeaks(List.of(), false);
            showPeakInfo();
            return;
        }
        Messages m = i18n.messages();
        spectrumDetailsLabel.setText(details(spectrum, m));
        spectrumChart.getData().add(sticks(m.get("chart.peaks"), spectrum.peaks(), 1));
        List<PeakAnnotation> annotations =
                annotation == null ? List.of() : annotation.annotations();
        if (annotation != null || reference != null) {
            spectrumChart
                    .getData()
                    .add(sticks(m.get("chart.bIons"), ionPeaks(annotations, true), 1));
            spectrumChart
                    .getData()
                    .add(sticks(m.get("chart.yIons"), ionPeaks(annotations, false), 1));
            annotationTable.setItems(FXCollections.observableArrayList(annotations));
        }
        List<Peak> referencePeaks = reference == null ? List.of() : scaledTo(spectrum, reference);
        boolean mirror = comparisonModeBox.getValue() == ComparisonMode.MIRROR;
        if (reference != null) {
            spectrumChart
                    .getData()
                    .add(sticks(m.get("chart.reference"), referencePeaks, mirror ? -1 : 1));
        }
        chartZoom.setReference(referencePeaks, mirror);
        chartZoom.setPeaks(spectrum.peaks(), sameSpectrum);
        selectPeak(selectedPeak, true);
    }

    /**
     * Rescales the peaks of a reference so that its base peak is as high as the one of the spectrum
     * it is compared with: spectra from different instruments, or a theoretical spectrum, have
     * unrelated intensity scales.
     */
    private static List<Peak> scaledTo(Spectrum spectrum, Spectrum reference) {
        double target = spectrum.basePeak().map(Peak::intensity).orElse(0.0);
        double base = reference.basePeak().map(Peak::intensity).orElse(0.0);
        double factor = target > 0 && base > 0 ? target / base : 1;
        return reference.peaks().stream()
                .map(p -> new Peak(p.mz(), p.intensity() * factor))
                .toList();
    }

    /** One-line description of a spectrum: id, MS level, precursor, retention time, peaks. */
    private static String details(Spectrum spectrum, Messages m) {
        String precursor =
                spectrum.precursorOptional()
                        .map(p -> String.format(Locale.ROOT, "%.4f (%+d)", p.mz(), p.charge()))
                        .orElse(NONE);
        String retentionTime =
                spectrum.retentionTime() == null
                        ? NONE
                        : String.format(Locale.ROOT, "%.1f s", spectrum.retentionTime());
        return m.get(
                "spectra.details",
                spectrum.id(),
                String.valueOf(spectrum.msLevel()),
                precursor,
                retentionTime,
                spectrum.peaks().size());
    }

    /** Peaks matched by N-terminal ions (a, b) or by C-terminal ions (y). */
    private static List<Peak> ionPeaks(List<PeakAnnotation> annotations, boolean nTerminal) {
        return annotations.stream()
                .filter(a -> a.ion().type().isNTerminal() == nTerminal)
                .map(PeakAnnotation::peak)
                .toList();
    }

    /**
     * Draws peaks as vertical sticks: the line chart goes up and down at each m/z, which is the
     * usual representation of a mass spectrum.
     *
     * @param direction 1 to draw the sticks upwards, -1 to draw them downwards (mirror)
     */
    private static XYChart.Series<Number, Number> sticks(
            String name, List<Peak> peaks, int direction) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (Peak p : peaks) {
            series.getData().add(new XYChart.Data<>(p.mz(), 0));
            series.getData().add(new XYChart.Data<>(p.mz(), direction * p.intensity()));
            series.getData().add(new XYChart.Data<>(p.mz(), 0));
        }
        return series;
    }

    /** Cell of the reference choice: spectrum id and peptide, or the theoretical peptide. */
    private final class ReferenceCell extends ListCell<Spectrum> {
        @Override
        protected void updateItem(Spectrum s, boolean empty) {
            super.updateItem(s, empty);
            ReferenceOrigin origin = model.referenceOriginProperty().get();
            if (empty || s == null) {
                setText(i18n.messages().get("spectra.noReference"));
            } else if (origin != null && origin.kind() == ReferenceOrigin.Kind.THEORETICAL) {
                setText(i18n.messages().get("spectra.theoreticalName", s.id()));
            } else {
                setText(s.id() + s.peptideOptional().map(p -> "  [" + p + "]").orElse(""));
            }
        }
    }

    /** List cell showing the spectrum id followed by its peptide, if known. */
    private static final class SpectrumCell extends ListCell<Spectrum> {
        @Override
        protected void updateItem(Spectrum s, boolean empty) {
            super.updateItem(s, empty);
            if (empty || s == null) {
                setText(null);
            } else {
                setText(s.id() + s.peptideOptional().map(p -> "  [" + p + "]").orElse(""));
            }
        }
    }

    /** List cell showing a translated parsing issue; errors are highlighted. */
    private final class IssueCell extends ListCell<ParseIssue> {
        @Override
        protected void updateItem(ParseIssue issue, boolean empty) {
            super.updateItem(issue, empty);
            boolean blank = empty || issue == null;
            if (blank) {
                setText(null);
            } else {
                Messages m = i18n.messages();
                String severity = m.get("issue." + issue.severity().name());
                setText(m.get("spectra.issue", severity, issue.location(), issue.message()));
            }
            pseudoClassStateChanged(ERROR, !blank && issue.severity() == ParseIssue.Severity.ERROR);
        }
    }
}
