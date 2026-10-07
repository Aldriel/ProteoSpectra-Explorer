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

package com.bioinfo.proteomics.desktop.i18n;

import javafx.beans.property.Property;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;

import java.util.List;

/**
 * Translates a view written with message keys in place of texts.
 *
 * <p>Every text, prompt text, tooltip, placeholder, tab, column, menu and axis label whose value is
 * a key of the bundle is bound to {@link I18n#text(String, Object...)}, so it follows language
 * changes. Values that are not keys (symbols such as {@code m/z} or {@code b}) are left untouched.
 */
public final class ViewTranslator {

    private final I18n i18n;

    public ViewTranslator(I18n i18n) {
        this.i18n = i18n;
    }

    /** Translates {@code root} and everything it contains. */
    public void translate(Node root) {
        visit(root);
    }

    /** Translates the menus of a menu bar and their items. */
    public void translate(MenuBar menuBar) {
        menuBar.getMenus().forEach(this::visitMenuItem);
    }

    private void visit(Node node) {
        if (node == null) {
            return;
        }
        if (node instanceof Labeled labeled) {
            bind(labeled.textProperty());
        }
        if (node instanceof TextInputControl input) {
            bind(input.promptTextProperty());
        }
        if (node instanceof Control control && control.getTooltip() != null) {
            bind(control.getTooltip());
        }
        if (node instanceof MenuBar menuBar) {
            translate(menuBar);
        }
        switch (node) {
            case TabPane tabs -> tabs.getTabs().forEach(this::visitTab);
            case SplitPane split -> split.getItems().forEach(this::visit);
            case TitledPane titled -> visit(titled.getContent());
            case ScrollPane scroll -> visit(scroll.getContent());
            case TableView<?> table -> {
                visit(table.getPlaceholder());
                visitColumns(table.getColumns());
            }
            case ListView<?> list -> visit(list.getPlaceholder());
            case XYChart<?, ?> chart -> {
                bind(chart.titleProperty());
                visitAxis(chart.getXAxis());
                visitAxis(chart.getYAxis());
            }
            default -> {
                // Children are visited below.
            }
        }
        if (node instanceof Parent parent && !(node instanceof XYChart<?, ?>)) {
            parent.getChildrenUnmodifiable().forEach(this::visit);
        }
    }

    private void visitTab(Tab tab) {
        bind(tab.textProperty());
        if (tab.getTooltip() != null) {
            bind(tab.getTooltip());
        }
        visit(tab.getContent());
    }

    private void visitColumns(List<? extends TableColumnBase<?, ?>> columns) {
        for (TableColumnBase<?, ?> column : columns) {
            bind(column.textProperty());
            visitColumns(column.getColumns());
        }
    }

    private void visitAxis(Axis<?> axis) {
        if (axis != null) {
            bind(axis.labelProperty());
        }
    }

    private void visitMenuItem(MenuItem item) {
        bind(item.textProperty());
        if (item instanceof Menu menu) {
            menu.getItems().forEach(this::visitMenuItem);
        }
    }

    private void bind(Tooltip tooltip) {
        bind(tooltip.textProperty());
    }

    private void bind(Property<String> property) {
        if (property.isBound()) {
            return;
        }
        String key = property.getValue();
        if (i18n.messages().has(key)) {
            property.bind(i18n.text(key));
        }
    }
}
