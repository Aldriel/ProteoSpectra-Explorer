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

import com.bioinfo.proteomics.desktop.model.MzRange;
import com.bioinfo.proteomics.model.Peak;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Mouse interactions of the spectrum chart:
 *
 * <ul>
 *   <li>dragging across the chart zooms into the selected m/z range;
 *   <li>the mouse wheel zooms in and out around the pointer;
 *   <li>a double-click shows the whole spectrum again;
 *   <li>a click selects the closest peak, which is highlighted.
 * </ul>
 *
 * <p>The intensity axis always fits the highest visible peak, so small peaks stay readable when
 * zooming; a reference spectrum may be drawn below the axis, in mirror. The selection rectangle and
 * the peak marker are drawn on {@code overlay}, a pane stacked over the chart.
 */
final class SpectrumChartZoom {

    /** Width change for one notch of the mouse wheel. */
    private static final double WHEEL_FACTOR = 0.8;

    /** Shorter drags are treated as clicks. */
    private static final double MIN_DRAG_PIXELS = 5;

    /** A click selects a peak if it is at most this far from it, in pixels. */
    private static final double CLICK_PIXELS = 8;

    /** Room above the highest visible peak, as a fraction of its intensity. */
    private static final double HEADROOM = 1.08;

    /** Number of ticks wanted on each axis. */
    private static final int TICKS = 10;

    private final LineChart<Number, Number> chart;
    private final NumberAxis xAxis;
    private final NumberAxis yAxis;
    private final Pane overlay;
    private final Rectangle selection = new Rectangle();
    private final Line marker = new Line();
    private final Consumer<Optional<Peak>> onPeakSelected;

    private List<Peak> peaks = List.of();
    private List<Peak> referencePeaks = List.of();
    private boolean mirrored;
    private MzRange fullRange = MzRange.enclosing(List.of());
    private MzRange range = fullRange;
    private Peak selectedPeak;
    private Double dragStartX;

    /**
     * Installs the interactions.
     *
     * @param chart spectrum chart, whose axes are {@link NumberAxis}
     * @param overlay pane stacked over the chart, receiving the selection rectangle and the marker
     * @param onPeakSelected called with the peak clicked, or empty when the click hits no peak
     */
    SpectrumChartZoom(
            LineChart<Number, Number> chart,
            Pane overlay,
            Consumer<Optional<Peak>> onPeakSelected) {
        this.chart = chart;
        this.xAxis = (NumberAxis) chart.getXAxis();
        this.yAxis = (NumberAxis) chart.getYAxis();
        this.overlay = overlay;
        this.onPeakSelected = onPeakSelected;

        selection.getStyleClass().add("zoom-selection");
        selection.setVisible(false);
        marker.getStyleClass().add("peak-marker");
        marker.setVisible(false);
        overlay.getChildren().addAll(selection, marker);
        overlay.setMouseTransparent(true);
        // A mirrored reference is drawn below the axis: its intensities are shown without sign.
        StringConverter<Number> labels = new NumberAxis.DefaultFormatter(yAxis);
        yAxis.setTickLabelFormatter(
                new StringConverter<>() {
                    @Override
                    public String toString(Number value) {
                        return labels.toString(Math.abs(value.doubleValue()));
                    }

                    @Override
                    public Number fromString(String text) {
                        return labels.fromString(text);
                    }
                });

        chart.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
        chart.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);
        chart.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onReleased);
        chart.addEventHandler(ScrollEvent.SCROLL, this::onScroll);
        chart.widthProperty().addListener((obs, old, w) -> updateMarkerLater());
        chart.heightProperty().addListener((obs, old, h) -> updateMarkerLater());
    }

    /**
     * Sets the peaks shown by the chart.
     *
     * @param newPeaks peaks of the spectrum displayed
     * @param keepRange {@code true} to keep the current zoom (same spectrum redrawn), {@code false}
     *     to show the whole spectrum
     */
    void setPeaks(List<Peak> newPeaks, boolean keepRange) {
        peaks = List.copyOf(newPeaks);
        List<Peak> all = new ArrayList<>(peaks);
        all.addAll(referencePeaks);
        fullRange = MzRange.enclosing(all);
        if (!keepRange) {
            selectedPeak = null;
        }
        show(keepRange ? range : fullRange);
    }

    /**
     * Sets the reference spectrum drawn with the peaks; call it before {@link #setPeaks}, which
     * applies it. The intensity axis then fits the highest visible peak of both spectra.
     *
     * @param scaledPeaks peaks of the reference, at the scale they are drawn; empty for none
     * @param mirror {@code true} if the reference is drawn below the axis
     */
    void setReference(List<Peak> scaledPeaks, boolean mirror) {
        referencePeaks = List.copyOf(scaledPeaks);
        mirrored = mirror && !referencePeaks.isEmpty();
    }

    /** Shows the whole spectrum. */
    void showAll() {
        show(fullRange);
    }

    /**
     * Highlights a peak, or removes the highlight.
     *
     * @param peak peak to highlight, or {@code null}
     */
    void highlight(Peak peak) {
        selectedPeak = peak;
        updateMarkerLater();
    }

    private void show(MzRange newRange) {
        range = newRange;
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(range.lower());
        xAxis.setUpperBound(range.upper());
        xAxis.setTickUnit(niceTickUnit(range.width()));
        double highest = Math.max(range.maxIntensity(peaks), range.maxIntensity(referencePeaks));
        double top = Math.max(highest * HEADROOM, 1);
        double tickUnit = niceTickUnit(mirrored ? 2 * top : top);
        if (mirrored) {
            // Ticks start at the lower bound: a multiple of the unit puts one on the zero line.
            top = Math.ceil(top / tickUnit) * tickUnit;
        }
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(mirrored ? -top : 0);
        yAxis.setUpperBound(top);
        yAxis.setTickUnit(tickUnit);
        updateMarkerLater();
    }

    /**
     * Returns a round tick spacing (1, 2 or 5 times a power of ten) giving about {@link #TICKS}
     * ticks over {@code span}.
     */
    static double niceTickUnit(double span) {
        double raw = span / TICKS;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double fraction = raw / magnitude;
        double nice = fraction < 1.5 ? 1 : fraction < 3.5 ? 2 : fraction < 7.5 ? 5 : 10;
        return nice * magnitude;
    }

    // ----- Mouse handling -----

    private void onPressed(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || !inPlotArea(e)) {
            return;
        }
        if (e.getClickCount() == 2) {
            showAll();
            dragStartX = null;
            return;
        }
        dragStartX = e.getSceneX();
    }

    private void onDragged(MouseEvent e) {
        if (dragStartX == null) {
            return;
        }
        Bounds plot = plotBounds();
        double start = overlay.sceneToLocal(dragStartX, 0).getX();
        double end =
                Math.clamp(
                        overlay.sceneToLocal(e.getSceneX(), 0).getX(),
                        plot.getMinX(),
                        plot.getMaxX());
        selection.setX(Math.min(start, end));
        selection.setY(plot.getMinY());
        selection.setWidth(Math.abs(end - start));
        selection.setHeight(plot.getHeight());
        selection.setVisible(true);
    }

    private void onReleased(MouseEvent e) {
        if (dragStartX == null) {
            return;
        }
        double startX = dragStartX;
        dragStartX = null;
        selection.setVisible(false);
        if (Math.abs(e.getSceneX() - startX) >= MIN_DRAG_PIXELS) {
            show(MzRange.between(mzAt(startX), mzAt(e.getSceneX())));
        } else {
            Optional<Peak> peak =
                    MzRange.nearestPeak(peaks, mzAt(e.getSceneX()), CLICK_PIXELS * mzPerPixel());
            highlight(peak.orElse(null));
            onPeakSelected.accept(peak);
        }
    }

    private void onScroll(ScrollEvent e) {
        if (e.getDeltaY() == 0 || !inPlotArea(e.getSceneX(), e.getSceneY())) {
            return;
        }
        double factor = e.getDeltaY() > 0 ? WHEEL_FACTOR : 1 / WHEEL_FACTOR;
        show(range.zoom(factor, mzAt(e.getSceneX()), fullRange));
        e.consume();
    }

    // ----- Coordinates -----

    private boolean inPlotArea(MouseEvent e) {
        return inPlotArea(e.getSceneX(), e.getSceneY());
    }

    private boolean inPlotArea(double sceneX, double sceneY) {
        return plotBounds().contains(overlay.sceneToLocal(sceneX, sceneY));
    }

    /** Bounds of the plotting area (inside the axes), in the coordinates of the overlay. */
    private Bounds plotBounds() {
        Node plot = chart.lookup(".chart-plot-background");
        return overlay.sceneToLocal(plot.localToScene(plot.getBoundsInLocal()));
    }

    private double mzAt(double sceneX) {
        Point2D local = xAxis.sceneToLocal(sceneX, 0);
        return xAxis.getValueForDisplay(local.getX()).doubleValue();
    }

    private double mzPerPixel() {
        return range.width() / Math.max(xAxis.getWidth(), 1);
    }

    /** Places the marker once the axes have been laid out with their new bounds. */
    private void updateMarkerLater() {
        Platform.runLater(this::updateMarker);
    }

    private void updateMarker() {
        if (selectedPeak == null || !range.contains(selectedPeak.mz())) {
            marker.setVisible(false);
            return;
        }
        Point2D base = toOverlay(selectedPeak.mz(), 0);
        Point2D top = toOverlay(selectedPeak.mz(), selectedPeak.intensity());
        marker.setStartX(base.getX());
        marker.setStartY(base.getY());
        marker.setEndX(top.getX());
        marker.setEndY(top.getY());
        marker.setVisible(true);
    }

    private Point2D toOverlay(double mz, double intensity) {
        double x = xAxis.localToScene(xAxis.getDisplayPosition(mz), 0).getX();
        double y = yAxis.localToScene(0, yAxis.getDisplayPosition(intensity)).getY();
        return overlay.sceneToLocal(x, y);
    }
}
