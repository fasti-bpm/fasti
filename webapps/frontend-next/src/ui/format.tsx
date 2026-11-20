/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Copy } from 'lucide-react';
import { useI18n } from '../i18n';
import { formatUtc, parseEngineDate } from '../lib/dates';
import { TIME_DISPLAYS, timeDisplay } from '../lib/userPrefs';
import { useToast } from './toast';

// Times, technical ids and numbers, shown the same way on every page.

export function Time({ value }: { value: string | null | undefined }) {
  const { relative, dateTime, locale } = useI18n();
  const date = parseEngineDate(value);
  if (!date) {
    return <span className="muted">—</span>;
  }
  // the chosen form is shown, the other two are in the tooltip
  const forms = { local: dateTime(date), relative: relative(date), utc: formatUtc(date, locale) };
  const shown = timeDisplay();
  return (
    <time
      dateTime={date.toISOString()}
      title={TIME_DISPLAYS.filter((form) => form !== shown)
        .map((form) => forms[form])
        .join(' · ')}
    >
      {forms[shown]}
    </time>
  );
}

/** A technical id: monospaced, shortened in the middle, complete in the tooltip. */
export function Id({ value, copy }: { value: string; copy?: boolean }) {
  const { t } = useI18n();
  const toast = useToast();
  const short = value.length > 20 ? `${value.slice(0, 8)}…${value.slice(-8)}` : value;
  return (
    <span className="id" title={value}>
      {short}
      {copy && (
        <button
          type="button"
          className="id__copy"
          aria-label={t('cockpit.copyId')}
          onClick={() => {
            void navigator.clipboard
              ?.writeText(value)
              .then(() => toast.show({ title: t('cockpit.copied') }))
              .catch(() => toast.show({ tone: 'error', title: t('cockpit.copyFailed') }));
          }}
        >
          <Copy aria-hidden size={13} />
        </button>
      )}
    </span>
  );
}

export function formatNumber(value: number, locale: string): string {
  return new Intl.NumberFormat(locale).format(value);
}
