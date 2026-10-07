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
import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.model.ProteinEntry;
import com.bioinfo.proteomics.service.ProteinDigestReport;
import com.bioinfo.proteomics.service.ProteinDigestReport.PeptideRow;
import com.bioinfo.proteomics.service.ProteomicsService;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;

import java.util.Locale;
import java.util.function.Consumer;

/** Controller of the "UniProt" tab: downloads a protein from UniProt and digests it in silico. */
public final class UniProtTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "uniprot";

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final TabNavigator navigator;

    @FXML private TextField accessionField;
    @FXML private ComboBox<Protease> proteaseBox;
    @FXML private Spinner<Integer> missedCleavageSpinner;
    @FXML private Spinner<Integer> minLengthSpinner;
    @FXML private Spinner<Integer> maxLengthSpinner;
    @FXML private ComboBox<MassType> digestMassTypeBox;
    @FXML private Label proteinSummaryLabel;
    @FXML private TableView<PeptideRow> digestTable;
    @FXML private TableColumn<PeptideRow, String> digestSequenceColumn;
    @FXML private TableColumn<PeptideRow, Integer> digestStartColumn;
    @FXML private TableColumn<PeptideRow, Integer> digestEndColumn;
    @FXML private TableColumn<PeptideRow, Integer> digestMissedColumn;
    @FXML private TableColumn<PeptideRow, Double> digestMassColumn;
    @FXML private TableColumn<PeptideRow, Double> digestMz1Column;
    @FXML private TableColumn<PeptideRow, Double> digestMz2Column;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     * @param navigator sends a peptide of the digestion to the "Peptide" or "Spectra" tab
     */
    public UniProtTabController(
            DesktopModel model, I18n i18n, Consumer<String> showHelp, TabNavigator navigator) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        Controls.translatedChoices(
                proteaseBox,
                Protease.values(),
                Protease.TRYPSIN,
                protease -> "protease." + protease.name(),
                i18n);
        Controls.translatedChoices(
                digestMassTypeBox,
                MassType.values(),
                MassType.MONOISOTOPIC,
                type -> "massType." + type.name(),
                i18n);

        Controls.column(digestSequenceColumn, PeptideRow::sequence);
        Controls.column(digestStartColumn, PeptideRow::start);
        Controls.column(digestEndColumn, PeptideRow::end);
        Controls.column(digestMissedColumn, PeptideRow::missedCleavages);
        Controls.decimalColumn(digestMassColumn, PeptideRow::neutralMass, 5);
        Controls.decimalColumn(digestMz1Column, PeptideRow::mz1, 5);
        Controls.decimalColumn(digestMz2Column, PeptideRow::mz2, 5);
        initPeptideActions();
        initLengthRange();

        model.digestReportProperty().addListener((obs, old, report) -> render());
        i18n.messagesProperty().addListener((obs, old, messages) -> render());
        render();
    }

    /**
     * A double-click on a peptide computes it in the "Peptide" tab; a right-click also offers to
     * annotate the selected spectrum with it, or to copy it.
     */
    private void initPeptideActions() {
        digestTable.setRowFactory(
                table -> {
                    TableRow<PeptideRow> row = new TableRow<>();
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getButton() == MouseButton.PRIMARY
                                        && e.getClickCount() == 2
                                        && !row.isEmpty()) {
                                    navigator.calculatePeptide(row.getItem().sequence());
                                }
                            });
                    return row;
                });
        ContextMenu menu =
                new ContextMenu(
                        Controls.menuItem(
                                "action.calculatePeptide",
                                i18n,
                                () -> withSelectedPeptide(navigator::calculatePeptide)),
                        Controls.menuItem(
                                "action.annotateSpectrum",
                                i18n,
                                () -> withSelectedPeptide(navigator::annotateWithPeptide)),
                        Controls.menuItem(
                                "action.copySequence",
                                i18n,
                                () -> withSelectedPeptide(Controls::copyToClipboard)));
        menu.setOnShowing(
                e -> {
                    boolean none = digestTable.getSelectionModel().getSelectedItem() == null;
                    menu.getItems().forEach(item -> item.setDisable(none));
                });
        digestTable.setContextMenu(menu);
    }

    /**
     * Keeps the length range valid: raising the minimum above the maximum raises the maximum, and
     * lowering the maximum below the minimum lowers the minimum.
     */
    private void initLengthRange() {
        minLengthSpinner
                .valueProperty()
                .addListener(
                        (obs, old, min) -> {
                            if (min > maxLengthSpinner.getValue()) {
                                maxLengthSpinner.getValueFactory().setValue(min);
                            }
                        });
        maxLengthSpinner
                .valueProperty()
                .addListener(
                        (obs, old, max) -> {
                            if (max < minLengthSpinner.getValue()) {
                                minLengthSpinner.getValueFactory().setValue(max);
                            }
                        });
    }

    private void withSelectedPeptide(Consumer<String> action) {
        PeptideRow row = digestTable.getSelectionModel().getSelectedItem();
        if (row != null) {
            action.accept(row.sequence());
        }
    }

    @FXML
    private void onFetchProtein() {
        // Both values are committed first: the range listeners keep minimum <= maximum.
        Controls.spinnerValue(minLengthSpinner);
        Controls.spinnerValue(maxLengthSpinner);
        model.fetchProtein(
                accessionField.getText(),
                ProteomicsService.digester(
                        proteaseBox.getValue(),
                        missedCleavageSpinner.getValue(),
                        minLengthSpinner.getValue(),
                        maxLengthSpinner.getValue()),
                digestMassTypeBox.getValue());
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    /** Displays the last digestion, or empties the tab if there is none. */
    private void render() {
        ProteinDigestReport report = model.digestReportProperty().get();
        if (report == null) {
            proteinSummaryLabel.setText("");
            digestTable.getItems().clear();
            return;
        }
        Messages m = i18n.messages();
        ProteinEntry protein = report.protein();
        String mass =
                report.proteinMass() == null
                        ? m.get("uniprot.massUnavailable")
                        : String.format(Locale.ROOT, "%.2f", report.proteinMass());
        String summary =
                m.get(
                        "uniprot.summary",
                        protein.accession(),
                        protein.entryName(),
                        protein.proteinName(),
                        protein.organism(),
                        protein.reviewed() ? "Swiss-Prot" : "TrEMBL",
                        protein.length(),
                        m.get("massType." + report.massType().name()).toLowerCase(m.locale()),
                        mass,
                        report.peptides().size(),
                        m.get("protease." + report.protease().name()),
                        report.minLength(),
                        report.maxLength());
        if (report.skippedPeptides() > 0) {
            summary += " " + m.get("uniprot.skipped", report.skippedPeptides());
        }
        proteinSummaryLabel.setText(summary);
        digestTable.setItems(FXCollections.observableArrayList(report.peptides()));
    }
}
