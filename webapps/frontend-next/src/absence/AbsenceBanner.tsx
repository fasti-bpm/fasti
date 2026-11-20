/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useQueries, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { fullName, getUserProfile } from '../api/engine';
import { getAbsence, getSubstitutedAbsences } from '../api/absence';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { absenceKey } from './AbsenceSettings';
import { useUserName } from './names';
import './absence.css';

const REFRESH = 5 * 60_000;

/** Whether absences can be set on the server, for the current user; `false` while unknown. */
export function useAbsenceEnabled(): boolean {
  const session = useSession();
  const absence = useQuery({
    queryKey: absenceKey(session.userId),
    queryFn: ({ signal }) => getAbsence(session.userId, signal),
    staleTime: REFRESH,
    retry: false,
  });
  return absence.data?.enabled ?? false;
}

/**
 * Above the task list: that you are away (or soon), and for whom you stand in. Nothing if
 * absences are not enabled or none concern you.
 */
export function AbsenceBanner() {
  const { t, dateTime, locale } = useI18n();
  const session = useSession();
  const absence = useQuery({
    queryKey: absenceKey(session.userId),
    queryFn: ({ signal }) => getAbsence(session.userId, signal),
    staleTime: REFRESH,
    refetchInterval: REFRESH,
    retry: false,
  });
  const enabled = absence.data?.enabled ?? false;
  const substituting = useQuery({
    queryKey: ['absence-substituting', session.userId],
    queryFn: ({ signal }) => getSubstitutedAbsences(session.userId, signal),
    enabled,
    staleTime: REFRESH,
    refetchInterval: REFRESH,
    retry: false,
  });
  const effective = useUserName(absence.data?.effectiveSubstituteId ?? absence.data?.substituteId);
  const covered = substituting.data ?? [];
  const names = useQueries({
    queries: covered.map((entry) => ({
      queryKey: ['user-name', entry.userId],
      queryFn: () => getUserProfile(entry.userId),
      staleTime: 5 * 60_000,
    })),
  }).map((profile, index) => (profile.data ? fullName(profile.data, locale) : covered[index]?.userId ?? ''));

  if (!enabled) {
    return null;
  }
  const own = absence.data;
  const until = parseEngineDate(own?.until);
  const from = parseEngineDate(own?.from);
  const list = new Intl.ListFormat(locale, { type: 'conjunction' }).format(names);
  const latest = covered
    .map((entry) => parseEngineDate(entry.until))
    .filter((date): date is Date => !!date)
    .sort((a, b) => b.getTime() - a.getTime())[0];

  return (
    <>
      {own && until && from && (own.status === 'active' || own.status === 'upcoming') && (
        <div className="absence-banner" role="status">
          <p className="absence-banner__text">
            {own.status === 'active'
              ? own.effectiveSubstituteId
                ? t('absence.banner.away', { until: dateTime(until), name: effective })
                : t('absence.banner.awayNobody', { until: dateTime(until) })
              : t('absence.banner.upcoming', { from: dateTime(from), until: dateTime(until), name: effective })}
          </p>
          <div className="absence-banner__actions">
            <Link className="btn btn--secondary btn--sm" to="/preferences#absence">
              {t('absence.banner.manage')}
            </Link>
          </div>
        </div>
      )}
      {covered.length > 0 && (
        <div className="absence-banner absence-banner--substitute" role="status">
          <p className="absence-banner__text">
            {covered.length === 1 && latest
              ? t('absence.banner.substituting', { name: list, until: dateTime(latest) })
              : t('absence.banner.substitutingMany', { names: list })}
          </p>
        </div>
      )}
    </>
  );
}
