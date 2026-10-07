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

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.function.Function;

/** Configuration helpers for the JavaFX controls, shared by the controllers. */
final class Controls {

    private Controls() {}

    /**
     * Fills a combo box with constants displayed through translated labels. The labels follow
     * language changes.
     *
     * @param box combo box to fill
     * @param values selectable values
     * @param initial value selected at start
     * @param key message key of the label of each value
     * @param i18n current language
     */
    static <T> void translatedChoices(
            ComboBox<T> box, T[] values, T initial, Function<T, String> key, I18n i18n) {
        box.setItems(FXCollections.observableArrayList(values));
        box.setValue(initial);
        applyLabels(box, key, i18n.messages());
        i18n.messagesProperty()
                .addListener((obs, old, messages) -> applyLabels(box, key, messages));
    }

    private static <T> void applyLabels(
            ComboBox<T> box, Function<T, String> key, Messages messages) {
        box.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(T value) {
                        return value == null ? "" : messages.get(key.apply(value));
                    }

                    @Override
                    public T fromString(String text) {
                        throw new UnsupportedOperationException("Combo boxes are not editable");
                    }
                });
        box.setCellFactory(
                list ->
                        new ListCell<>() {
                            @Override
                            protected void updateItem(T value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : messages.get(key.apply(value)));
                            }
                        });
        box.setButtonCell(box.getCellFactory().call(null));
    }

    /**
     * Returns the value of an editable spinner, including a number typed but not yet confirmed with
     * Enter. Text that is not a number is discarded; values out of range are clamped.
     *
     * @param spinner integer spinner
     * @return the current value
     */
    static int spinnerValue(Spinner<Integer> spinner) {
        SpinnerValueFactory<Integer> factory = spinner.getValueFactory();
        try {
            factory.setValue(factory.getConverter().fromString(spinner.getEditor().getText()));
        } catch (RuntimeException e) {
            spinner.getEditor().setText(factory.getConverter().toString(factory.getValue()));
        }
        return spinner.getValue();
    }

    /**
     * Creates a menu item whose text follows language changes.
     *
     * @param key message key of the text
     * @param i18n current language
     * @param action run when the item is chosen
     * @return the menu item
     */
    static MenuItem menuItem(String key, I18n i18n, Runnable action) {
        MenuItem item = new MenuItem();
        item.textProperty().bind(i18n.text(key));
        item.setOnAction(e -> action.run());
        return item;
    }

    /**
     * Copies a text to the system clipboard.
     *
     * @param text text to copy
     */
    static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Displays in a table column the value returned by {@code getter} for each row.
     *
     * @param column column to configure
     * @param getter extracts the cell value from the row
     */
    static <S, T> void column(TableColumn<S, T> column, Function<S, T> getter) {
        column.setCellValueFactory(
                cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
    }

    /**
     * Displays a decimal number with a fixed number of decimals, independently of the language.
     *
     * @param column column to configure
     * @param getter extracts the number from the row
     * @param decimals number of digits after the decimal point
     */
    static <S> void decimalColumn(
            TableColumn<S, Double> column, Function<S, Double> getter, int decimals) {
        column(column, getter);
        String format = "%." + decimals + "f";
        column.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Double value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(
                                        empty || value == null
                                                ? null
                                                : String.format(Locale.ROOT, format, value));
                            }
                        });
    }
}
