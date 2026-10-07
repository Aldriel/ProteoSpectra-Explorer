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

package com.bioinfo.proteomics.desktop.model;

import com.bioinfo.proteomics.model.Peak;

import java.util.Collection;
import java.util.Optional;

/**
 * Range of m/z values shown by the spectrum chart: the whole spectrum, or the part the user zoomed
 * into.
 *
 * @param lower smallest m/z shown
 * @param upper largest m/z shown, greater than {@code lower}
 */
public record MzRange(double lower, double upper) {

    /** Narrowest range the user can zoom into, in m/z units. */
    public static final double MIN_WIDTH = 0.05;

    /** Range shown when there is no peak. */
    private static final MzRange EMPTY = new MzRange(0, 100);

    /** Space left on each side of the outermost peaks, as a fraction of their m/z span. */
    private static final double MARGIN = 0.05;

    /** Validates the range: finite bounds, {@code lower} strictly below {@code upper}. */
    public MzRange {
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower >= upper) {
            throw new IllegalArgumentException("Invalid m/z range " + lower + " - " + upper);
        }
    }

    /**
     * Returns the range showing every peak, with a small margin on each side.
     *
     * @param peaks peaks of a spectrum, possibly empty
     * @return the range of the whole spectrum
     */
    public static MzRange enclosing(Collection<Peak> peaks) {
        if (peaks.isEmpty()) {
            return EMPTY;
        }
        double min = peaks.stream().mapToDouble(Peak::mz).min().orElseThrow();
        double max = peaks.stream().mapToDouble(Peak::mz).max().orElseThrow();
        double margin = Math.max((max - min) * MARGIN, 1);
        return new MzRange(Math.max(0, min - margin), max + margin);
    }

    /**
     * Returns the range between two m/z values selected by the user, in any order.
     *
     * @param a one end of the selection
     * @param b the other end
     * @return the range, widened around its center to at least {@link #MIN_WIDTH}
     */
    public static MzRange between(double a, double b) {
        double lower = Math.min(a, b);
        double upper = Math.max(a, b);
        if (upper - lower < MIN_WIDTH) {
            double center = (lower + upper) / 2;
            return new MzRange(center - MIN_WIDTH / 2, center + MIN_WIDTH / 2);
        }
        return new MzRange(lower, upper);
    }

    /**
     * Returns the width of the range.
     *
     * @return {@code upper - lower}
     */
    public double width() {
        return upper - lower;
    }

    /**
     * Zooms in or out around a point, which keeps its position on the screen.
     *
     * @param factor new width divided by the current width: below 1 zooms in, above 1 zooms out
     * @param center m/z that stays in place, usually the one under the mouse pointer
     * @param limits widest range allowed, usually the whole spectrum
     * @return the new range, never narrower than {@link #MIN_WIDTH} nor wider than {@code limits}
     */
    public MzRange zoom(double factor, double center, MzRange limits) {
        double width = Math.clamp(width() * factor, MIN_WIDTH, limits.width());
        double position = (center - lower) / width();
        double newLower = center - position * width;
        return shiftedInto(newLower, width, limits);
    }

    private static MzRange shiftedInto(double lower, double width, MzRange limits) {
        double start = Math.clamp(lower, limits.lower(), limits.upper() - width);
        return new MzRange(start, start + width);
    }

    /**
     * Tells whether an m/z value is inside the range.
     *
     * @param mz value to test
     * @return {@code true} if {@code lower <= mz <= upper}
     */
    public boolean contains(double mz) {
        return mz >= lower && mz <= upper;
    }

    /**
     * Returns the intensity of the highest peak inside the range, used to scale the intensity axis
     * so that small peaks remain visible when zooming.
     *
     * @param peaks peaks of the spectrum
     * @return the highest intensity in the range, or 0 if the range contains no peak
     */
    public double maxIntensity(Collection<Peak> peaks) {
        return peaks.stream()
                .filter(p -> contains(p.mz()))
                .mapToDouble(Peak::intensity)
                .max()
                .orElse(0);
    }

    /**
     * Finds the peak closest to an m/z value, typically the one the user clicked.
     *
     * @param peaks peaks of the spectrum
     * @param mz m/z value clicked
     * @param maxDistance largest accepted distance, in m/z units
     * @return the closest peak, or empty if none is within {@code maxDistance}
     */
    public static Optional<Peak> nearestPeak(
            Collection<Peak> peaks, double mz, double maxDistance) {
        Peak best = null;
        for (Peak p : peaks) {
            double distance = Math.abs(p.mz() - mz);
            if (distance <= maxDistance && (best == null || distance < Math.abs(best.mz() - mz))) {
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }
}
