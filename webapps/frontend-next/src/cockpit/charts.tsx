/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ReactNode } from 'react';
import { useI18n } from '../i18n';
import { cx } from '../ui';
import { formatNumber } from '../ui/format';

/*
 * Small charts drawn as SVG. They follow a few rules: one measure per chart,
 * bars start at zero, every bar carries its value as text, colour is never
 * the only carrier of meaning, and the text uses the text colours of the theme.
 */

const BAR_HEIGHT = 10;
const RADIUS = 4;

export interface BarItem {
  key: string;
  label: ReactNode;
  /** plain text of the label, for the tooltip of the bar */
  name: string;
  value: number;
  /** shown after the value, e.g. a badge */
  extra?: ReactNode;
}

/**
 * A ranked list of horizontal bars that share one scale. The value is
 * written next to every bar, so the list can be read without the bars.
 */
export function BarList({
  items,
  tone = 'primary',
  unit,
}: {
  items: BarItem[];
  tone?: 'primary' | 'danger';
  /** what a value counts, for the tooltip: "{count} instances" */
  unit: (count: number) => string;
}) {
  const { locale } = useI18n();
  const max = Math.max(1, ...items.map((item) => item.value));
  return (
    <ol className="bar-list">
      {items.map((item) => {
        const share = (item.value / max) * 100;
        return (
          <li key={item.key} className="bar-list__row">
            <span className="bar-list__label">{item.label}</span>
            <span className="bar-list__value">
              {formatNumber(item.value, locale)}
              {item.extra}
            </span>
            <svg className="bar-list__bar" height={BAR_HEIGHT} role="presentation">
              <title>{`${item.name}: ${unit(item.value)}`}</title>
              <rect className="chart-track" width="100%" height={BAR_HEIGHT} rx={RADIUS} />
              {item.value > 0 && (
                // starts left of the plot, so that only the end at the value is rounded
                <rect
                  className={cx('chart-bar', `chart-bar--${tone}`)}
                  x={-RADIUS}
                  height={BAR_HEIGHT}
                  rx={RADIUS}
                  style={{ width: `calc(${share}% + ${RADIUS}px)` }}
                />
              )}
            </svg>
          </li>
        );
      })}
    </ol>
  );
}

export interface Part {
  key: string;
  label: string;
  value: number;
  /** position in the fixed colour order of the charts: 1 to 3 */
  series: 1 | 2 | 3;
}

/**
 * Parts of a whole as one bar. The legend below names every part with its
 * value and share, so the colours only help to find a part in the bar.
 */
export function PartsBar({ parts, label }: { parts: Part[]; label: string }) {
  const { locale } = useI18n();
  const total = parts.reduce((sum, part) => sum + part.value, 0);
  let start = 0;
  return (
    <figure className="parts">
      <svg className="parts__bar" height={16} role="img" aria-label={label}>
        <rect className="chart-track" width="100%" height={16} rx={RADIUS} />
        {total > 0 &&
          parts.map((part) => {
            const share = (part.value / total) * 100;
            const x = start;
            start += share;
            if (part.value === 0) {
              return null;
            }
            return (
              <rect
                key={part.key}
                className={`chart-series-${part.series}`}
                height={16}
                rx={2}
                // a gap of two pixels separates neighbours
                style={{ x: `${x}%`, width: `max(2px, calc(${share}% - 2px))` }}
              >
                <title>{`${part.label}: ${formatNumber(part.value, locale)}`}</title>
              </rect>
            );
          })}
      </svg>
      <figcaption>
        <ul className="parts__legend">
          {parts.map((part) => (
            <li key={part.key}>
              <span className={cx('parts__swatch', `chart-series-${part.series}`)} aria-hidden />
              <span className="parts__label">{part.label}</span>
              <span className="parts__value">{formatNumber(part.value, locale)}</span>
              <span className="parts__share">
                {total > 0 ? `${Math.round((part.value / total) * 100)}%` : '—'}
              </span>
            </li>
          ))}
        </ul>
      </figcaption>
    </figure>
  );
}

/** Share of a job that is done, e.g. the progress of a batch. */
export function Progress({
  done,
  failed,
  total,
  label,
}: {
  done: number;
  failed: number;
  total: number;
  label: string;
}) {
  const doneShare = total > 0 ? (done / total) * 100 : 0;
  const failedShare = total > 0 ? (failed / total) * 100 : 0;
  return (
    <svg className="progress" height={8} role="img" aria-label={label}>
      <rect className="chart-track" width="100%" height={8} rx={RADIUS} />
      {done > 0 && (
        <rect
          className="chart-bar chart-bar--primary"
          x={-RADIUS}
          height={8}
          rx={RADIUS}
          style={{ width: `calc(${doneShare}% + ${RADIUS}px)` }}
        />
      )}
      {failed > 0 && (
        <rect
          className="chart-bar chart-bar--danger"
          height={8}
          rx={2}
          style={{ x: `calc(${doneShare}% + 2px)`, width: `max(3px, ${failedShare}%)` }}
        />
      )}
    </svg>
  );
}
