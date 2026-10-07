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

import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react';
import { useMessages } from '../../i18n';
import {
  between,
  contains,
  enclosing,
  maxIntensity,
  nearestPeak,
  niceTickUnit,
  ticks,
  width,
  zoom,
  type MzRange,
} from '../../model/mzRange';
import type { Peak } from '../../model/types';
import { useElementSize } from './useElementSize';

/** Width change for one notch of the mouse wheel. */
const WHEEL_FACTOR = 0.8;
/** Shorter drags are treated as clicks. */
const MIN_DRAG_PIXELS = 5;
/** A click selects a peak if it is at most this far from it, in pixels. */
const CLICK_PIXELS = 8;
/** Room above the highest visible peak, as a fraction of its intensity. */
const HEADROOM = 1.08;

const MARGIN = { top: 12, right: 18, bottom: 42, left: 70 };

interface Props {
  /** Peaks of the spectrum, drawn in grey. */
  peaks: readonly Peak[];
  /** Peaks matched by N-terminal ions, drawn in blue over the grey peaks. */
  bPeaks: readonly Peak[];
  /** Peaks matched by C-terminal ions, drawn in red. */
  yPeaks: readonly Peak[];
  /** Whether the b and y series exist (annotation or reference), for the legend. */
  showIons: boolean;
  /** Reference spectrum at the scale it is drawn, in green; empty for none. */
  referencePeaks: readonly Peak[];
  /** Whether the reference is drawn below the axis. */
  mirror: boolean;
  /** Range zoomed into, or null for the whole spectrum. */
  zoomRange: MzRange | null;
  onZoom: (range: MzRange | null) => void;
  selectedPeak: Peak | null;
  /** Called with the peak clicked, or null when the click hits no peak. */
  onPeakClick: (peak: Peak | null) => void;
  label: string;
}

/** Writes the value of a tick with as many decimals as its spacing needs. */
function tickFormat(locale: string, unit: number) {
  const decimals = Math.max(0, Math.ceil(-Math.log10(unit) - 1e-9));
  const format = new Intl.NumberFormat(locale, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
    useGrouping: false,
  });
  return (value: number) => format.format(Math.abs(value) < unit / 1e6 ? 0 : value);
}

/** Path drawing each peak as a vertical stick from the axis. */
function sticks(
  peaks: readonly Peak[],
  range: MzRange,
  x: (mz: number) => number,
  y: (intensity: number) => number,
  direction: number,
): string {
  const zero = y(0);
  let path = '';
  for (const p of peaks) {
    if (contains(range, p.mz)) {
      const px = x(p.mz).toFixed(1);
      path += `M${px} ${zero.toFixed(1)}V${y(direction * p.intensity).toFixed(1)}`;
    }
  }
  return path;
}

/**
 * Stick plot of a centroided spectrum, with the interactions of the desktop chart:
 *
 * - dragging across the chart zooms into the selected m/z range;
 * - the mouse wheel zooms in and out around the pointer;
 * - a double-click shows the whole spectrum again;
 * - a click selects the closest peak, which is highlighted in orange.
 *
 * The intensity axis always fits the highest visible peak, so small peaks stay readable when
 * zooming; a reference spectrum may be drawn below the axis, in mirror.
 */
export function SpectrumChart(props: Props) {
  const { peaks, bPeaks, yPeaks, showIons, referencePeaks, mirror, zoomRange, selectedPeak } =
    props;
  const m = useMessages();
  const box = useElementSize<HTMLDivElement>({ width: 800, height: 300 });
  const svg = useRef<SVGSVGElement>(null);
  const [drag, setDrag] = useState<{ start: number; end: number } | null>(null);

  const plotWidth = Math.max(10, box.width - MARGIN.left - MARGIN.right);
  const plotHeight = Math.max(10, box.height - MARGIN.top - MARGIN.bottom);

  const fullRange = useMemo(
    () => enclosing([...peaks, ...referencePeaks]),
    [peaks, referencePeaks],
  );
  const range = zoomRange ?? fullRange;
  const mirrored = mirror && referencePeaks.length > 0;

  const highest = Math.max(maxIntensity(range, peaks), maxIntensity(range, referencePeaks));
  let top = Math.max(highest * HEADROOM, 1);
  const yUnit = niceTickUnit(mirrored ? 2 * top : top);
  if (mirrored) {
    // Ticks start at the lower bound: a multiple of the unit puts one on the zero line.
    top = Math.ceil(top / yUnit) * yUnit;
  }
  const bottom = mirrored ? -top : 0;
  const xUnit = niceTickUnit(width(range));

  const x = (mz: number) => MARGIN.left + ((mz - range.lower) / width(range)) * plotWidth;
  const y = (intensity: number) => MARGIN.top + ((top - intensity) / (top - bottom)) * plotHeight;

  /** m/z under a horizontal position of the window, kept inside the plotting area. */
  const mzAt = (clientX: number) => {
    const left = svg.current?.getBoundingClientRect().left ?? 0;
    const px = Math.min(plotWidth, Math.max(0, clientX - left - MARGIN.left));
    return range.lower + (px / plotWidth) * width(range);
  };

  // The latest values, read by the wheel and drag listeners installed once.
  const latest = useRef({ range, fullRange, mzAt, props });
  latest.current = { range, fullRange, mzAt, props };

  // The wheel listener is not passive, so that the page does not scroll while zooming.
  useEffect(() => {
    const element = svg.current;
    if (!element) {
      return;
    }
    const onWheel = (e: WheelEvent) => {
      if (e.deltaY === 0 || !inPlot(element, e.clientX, e.clientY)) {
        return;
      }
      e.preventDefault();
      const { range: current, fullRange: limits, mzAt: at, props: p } = latest.current;
      const factor = e.deltaY < 0 ? WHEEL_FACTOR : 1 / WHEEL_FACTOR;
      p.onZoom(zoom(current, factor, at(e.clientX), limits));
    };
    element.addEventListener('wheel', onWheel, { passive: false });
    return () => element.removeEventListener('wheel', onWheel);
  }, []);

  const onMouseDown = (e: ReactMouseEvent) => {
    if (e.button !== 0 || !svg.current || !inPlot(svg.current, e.clientX, e.clientY)) {
      return;
    }
    e.preventDefault();
    if (e.detail === 2) {
      props.onZoom(null);
      return;
    }
    const start = e.clientX;
    setDrag({ start, end: start });
    const onMove = (move: MouseEvent) => setDrag({ start, end: move.clientX });
    const onUp = (up: MouseEvent) => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      setDrag(null);
      const { range: current, mzAt: at, props: p } = latest.current;
      if (Math.abs(up.clientX - start) >= MIN_DRAG_PIXELS) {
        p.onZoom(between(at(start), at(up.clientX)));
      } else {
        const mzPerPixel = width(current) / plotWidth;
        p.onPeakClick(nearestPeak(p.peaks, at(up.clientX), CLICK_PIXELS * mzPerPixel));
      }
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const formatX = tickFormat(m.locale, xUnit);
  const formatY = tickFormat(m.locale, yUnit);
  const xTicks = ticks(range.lower, range.upper, xUnit);
  const yTicks = ticks(bottom, top, yUnit);
  const plotLeft = svg.current?.getBoundingClientRect().left ?? 0;
  const selection =
    drag &&
    (() => {
      const clamp = (clientX: number) =>
        Math.min(MARGIN.left + plotWidth, Math.max(MARGIN.left, clientX - plotLeft));
      const a = clamp(drag.start);
      const b = clamp(drag.end);
      return { x: Math.min(a, b), width: Math.abs(b - a) };
    })();
  const marker = selectedPeak && contains(range, selectedPeak.mz) ? selectedPeak : null;

  const legend = [
    { name: m.chart.peaks, className: 'peaks', shown: true },
    { name: m.chart.bIons, className: 'b', shown: showIons },
    { name: m.chart.yIons, className: 'y', shown: showIons },
    { name: m.chart.reference, className: 'reference', shown: referencePeaks.length > 0 },
  ].filter((item) => item.shown);

  return (
    <div className="chart">
      <div className="chart-area" ref={box.ref}>
        <svg
          ref={svg}
          width={box.width}
          height={box.height}
          role="img"
          aria-label={props.label}
          onMouseDown={onMouseDown}
        >
          <defs>
            <clipPath id="plot-clip">
              <rect x={MARGIN.left} y={MARGIN.top} width={plotWidth} height={plotHeight} />
            </clipPath>
          </defs>
          <rect
            className="plot-background"
            x={MARGIN.left}
            y={MARGIN.top}
            width={plotWidth}
            height={plotHeight}
          />
          {/* Grid lines and tick labels */}
          {xTicks.map((t) => (
            <g key={`x${t}`}>
              <line className="grid" x1={x(t)} x2={x(t)} y1={MARGIN.top} y2={y(bottom)} />
              <text className="tick" x={x(t)} y={y(bottom) + 16} textAnchor="middle">
                {formatX(t)}
              </text>
            </g>
          ))}
          {yTicks.map((t) => (
            <g key={`y${t}`}>
              <line
                className="grid"
                x1={MARGIN.left}
                x2={MARGIN.left + plotWidth}
                y1={y(t)}
                y2={y(t)}
              />
              <text className="tick" x={MARGIN.left - 6} y={y(t) + 4} textAnchor="end">
                {formatY(t)}
              </text>
            </g>
          ))}
          <text
            className="axis-label"
            x={MARGIN.left + plotWidth / 2}
            y={box.height - 6}
            textAnchor="middle"
          >
            m/z
          </text>
          <text
            className="axis-label"
            transform={`translate(14 ${MARGIN.top + plotHeight / 2}) rotate(-90)`}
            textAnchor="middle"
          >
            {m.chart.intensity}
          </text>

          {/* Series, always in the same order: peaks, b ions, y ions, reference */}
          <g clipPath="url(#plot-clip)">
            <path className="series peaks" d={sticks(peaks, range, x, y, 1)} />
            <path className="series b" d={sticks(bPeaks, range, x, y, 1)} />
            <path className="series y" d={sticks(yPeaks, range, x, y, 1)} />
            <path
              className="series reference"
              d={sticks(referencePeaks, range, x, y, mirrored ? -1 : 1)}
            />
            {marker && (
              <line
                className="peak-marker"
                x1={x(marker.mz)}
                x2={x(marker.mz)}
                y1={y(0)}
                y2={y(marker.intensity)}
              />
            )}
          </g>
          <line
            className="axis"
            x1={MARGIN.left}
            x2={MARGIN.left + plotWidth}
            y1={y(0)}
            y2={y(0)}
          />
          <line className="axis" x1={MARGIN.left} x2={MARGIN.left} y1={MARGIN.top} y2={y(bottom)} />
          {selection && (
            <rect
              className="zoom-selection"
              x={selection.x}
              y={MARGIN.top}
              width={selection.width}
              height={plotHeight}
            />
          )}
        </svg>
      </div>
      <div className="chart-legend">
        {legend.map((item) => (
          <span key={item.className}>
            <span className={`swatch ${item.className}`} />
            {item.name}
          </span>
        ))}
      </div>
    </div>
  );
}

/** Tells whether a point of the window is inside the plotting area of the chart. */
function inPlot(element: SVGSVGElement, clientX: number, clientY: number): boolean {
  const rect = element.getBoundingClientRect();
  const px = clientX - rect.left;
  const py = clientY - rect.top;
  return (
    px >= MARGIN.left &&
    px <= rect.width - MARGIN.right &&
    py >= MARGIN.top &&
    py <= rect.height - MARGIN.bottom
  );
}
