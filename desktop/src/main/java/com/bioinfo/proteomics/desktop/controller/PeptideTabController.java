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

import com.bioinfo.proteomics.decoy.DecoyMethod;
import com.bioinfo.proteomics.desktop.i18n.I18n;
import com.bioinfo.proteomics.desktop.i18n.Messages;
import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.mass.FragmentIon;
import com.bioinfo.proteomics.mass.IonType;
import com.bioinfo.proteomics.model.CommonModification;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.service.PeptideQuery;
import com.bioinfo.proteomics.service.PeptideReport;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controller of the "Peptide" tab: computes the masses, charge states and fragment ions of a
 * peptide written in ProForma notation, with optional common modifications, or of its decoy.
 */
public final class PeptideTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "peptide";

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;

    @FXML private TextField sequenceField;
    @FXML private Spinner<Integer> maxChargeSpinner;
    @FXML private ComboBox<MassType> peptideMassTypeBox;
    @FXML private CheckBox carbamidomethylBox;
    @FXML private CheckBox oxidationBox;
    @FXML private CheckBox phosphoBox;
    @FXML private CheckBox deamidationBox;
    @FXML private CheckBox aIonsBox;
    @FXML private CheckBox bIonsBox;
    @FXML private CheckBox yIonsBox;
    @FXML private Label peptideSummaryLabel;
    @FXML private TableView<PeptideReport.ChargeState> chargeTable;
    @FXML private TableColumn<PeptideReport.ChargeState, Integer> chargeColumn;
    @FXML private TableColumn<PeptideReport.ChargeState, Double> chargeMzColumn;
    @FXML private TableView<FragmentIon> fragmentTable;
    @FXML private TableColumn<FragmentIon, String> fragmentLabelColumn;
    @FXML private TableColumn<FragmentIon, String> fragmentSeriesColumn;
    @FXML private TableColumn<FragmentIon, Integer> fragmentOrdinalColumn;
    @FXML private TableColumn<FragmentIon, Double> fragmentMzColumn;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     */
    public PeptideTabController(DesktopModel model, I18n i18n, Consumer<String> showHelp) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
    }

    @FXML
    private void initialize() {
        Controls.translatedChoices(
                peptideMassTypeBox,
                MassType.values(),
                MassType.MONOISOTOPIC,
                type -> "massType." + type.name(),
                i18n);

        Controls.column(chargeColumn, PeptideReport.ChargeState::charge);
        Controls.decimalColumn(chargeMzColumn, PeptideReport.ChargeState::mz, 5);
        Controls.column(fragmentLabelColumn, FragmentIon::label);
        Controls.column(fragmentSeriesColumn, ion -> ion.type().symbol());
        Controls.column(fragmentOrdinalColumn, FragmentIon::ordinal);
        Controls.decimalColumn(fragmentMzColumn, FragmentIon::mz, 5);

        model.peptideReportProperty().addListener((obs, old, report) -> render());
        i18n.messagesProperty().addListener((obs, old, messages) -> render());
        render();
    }

    /**
     * Computes a peptide sent from another tab, with the options currently chosen in this tab.
     *
     * @param peptide peptide in ProForma notation
     */
    public void calculate(String peptide) {
        sequenceField.setText(peptide);
        onCalculate();
    }

    @FXML
    private void onCalculate() {
        model.analyzePeptide(query());
    }

    @FXML
    private void onReverse() {
        showDecoy(DecoyMethod.REVERSE);
    }

    @FXML
    private void onShuffle() {
        showDecoy(DecoyMethod.SHUFFLE);
    }

    /** Replaces the sequence by its decoy, computed with the options of the tab. */
    private void showDecoy(DecoyMethod method) {
        model.analyzeDecoy(query(), method).ifPresent(sequenceField::setText);
    }

    /** Builds the query from the sequence and the options chosen in the tab. */
    private PeptideQuery query() {
        Set<IonType> ions = EnumSet.noneOf(IonType.class);
        addIf(aIonsBox, IonType.A, ions);
        addIf(bIonsBox, IonType.B, ions);
        addIf(yIonsBox, IonType.Y, ions);
        Set<CommonModification> modifications = EnumSet.noneOf(CommonModification.class);
        addIf(carbamidomethylBox, CommonModification.CARBAMIDOMETHYL, modifications);
        addIf(oxidationBox, CommonModification.OXIDATION, modifications);
        addIf(phosphoBox, CommonModification.PHOSPHO, modifications);
        addIf(deamidationBox, CommonModification.DEAMIDATION, modifications);
        return new PeptideQuery(
                sequenceField.getText(),
                maxChargeSpinner.getValue(),
                peptideMassTypeBox.getValue(),
                ions,
                modifications);
    }

    private static <T> void addIf(CheckBox box, T value, Set<T> values) {
        if (box.isSelected()) {
            values.add(value);
        }
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    /** Displays the last peptide report, or empties the tab if there is none. */
    private void render() {
        PeptideReport report = model.peptideReportProperty().get();
        if (report == null) {
            peptideSummaryLabel.setText("");
            chargeTable.getItems().clear();
            fragmentTable.getItems().clear();
            return;
        }
        Messages m = i18n.messages();
        String massType = m.get("massType." + report.massType().name()).toLowerCase(m.locale());
        peptideSummaryLabel.setText(
                m.get(
                        "peptide.summary",
                        report.proForma(),
                        report.length(),
                        massType,
                        String.format(Locale.ROOT, "%.6f", report.neutralMass())));
        chargeTable.setItems(FXCollections.observableArrayList(report.chargeStates()));
        fragmentTable.setItems(FXCollections.observableArrayList(report.fragments()));
    }
}
