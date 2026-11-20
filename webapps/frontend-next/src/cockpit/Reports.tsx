/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { getDurationReport, getStartedPerDay, orNull } from '../api/operations';
import { useI18n } from '../i18n';
import { toEngineDate } from '../lib/dates';
import { EmptyState, ErrorState, Loading, Skeleton } from '../ui';
import { BarList } from './charts';
import { COCKPIT, EngineWideNote } from './common';
import { Card } from '../ui/page';
import { formatDuration } from './model';

const DAYS = 14;

/** Reports from history: instances started per day and the duration of instances per month. */
export function HistoryReports() {
  const { t, locale } = useI18n();
  const started = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'started-per-day'],
    queryFn: ({ signal }) => orNull(getStartedPerDay(DAYS, toEngineDate, signal)),
    staleTime: 60_000,
  });
  const durations = useQuery({
    queryKey: [COCKPIT, 'dashboard', 'duration-report'],
    queryFn: ({ signal }) => orNull(getDurationReport('month', undefined, signal)),
    staleTime: 60_000,
  });
  const day = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', weekday: 'short' });
  const month = new Intl.DateTimeFormat(locale, { month: 'long' });

  return (
    <>
      <Card title={t('ops.reports.started', { count: DAYS })}>
        {started.isError ? (
          <ErrorState error={started.error} onRetry={() => void started.refetch()} />
        ) : started.data === null ? (
          <EmptyState icon={History} title={t('cockpit.history.unavailable')} />
        ) : !started.data ? (
          <Loading label={t('state.loading')}>
            <Skeleton lines={5} />
          </Loading>
        ) : (
          <BarList
            items={started.data.map((entry) => ({
              key: entry.day.toISOString(),
              name: day.format(entry.day),
              label: day.format(entry.day),
              value: entry.count,
            }))}
            unit={(count) => t('cockpit.instances.count', { count })}
          />
        )}
      </Card>
      <Card title={t('ops.reports.duration')}>
        <EngineWideNote />
        {durations.isError ? (
          <ErrorState error={durations.error} onRetry={() => void durations.refetch()} />
        ) : durations.data === null ? (
          <EmptyState icon={History} title={t('cockpit.history.unavailable')} />
        ) : !durations.data ? (
          <Loading label={t('state.loading')}>
            <Skeleton lines={5} />
          </Loading>
        ) : durations.data.length === 0 ? (
          <EmptyState icon={History} title={t('ops.reports.noDurations')} />
        ) : (
          <BarList
            items={durations.data.map((entry) => {
              const label = month.format(new Date(2000, entry.period - 1, 1));
              return {
                key: `${entry.period}`,
                name: label,
                label,
                value: Math.round(entry.average / 60_000),
                extra: (
                  <span className="muted">
                    {' '}
                    ({formatDuration(entry.minimum, locale)} – {formatDuration(entry.maximum, locale)})
                  </span>
                ),
              };
            })}
            unit={(minutes) => t('ops.reports.minutes', { count: minutes })}
          />
        )}
        <p className="field__hint">{t('ops.reports.durationHint')}</p>
      </Card>
    </>
  );
}
