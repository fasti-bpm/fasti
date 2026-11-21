/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// What the monitoring pages share among themselves; what every area shares is in ../ui.
import { createContext, useCallback, useContext, useMemo, useState, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';
import { useIsFetching, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CircleCheck, Pause, Play, RefreshCw } from 'lucide-react';
import type { IncidentCount } from '../api/cockpit';
import { useI18n } from '../i18n';
import { usePacedInterval } from '../lib/activity';
import { readPreference, writePreference } from '../lib/storage';
import { tenantScope } from '../lib/userPrefs';
import { IconButton, Tooltip, cx } from '../ui';
import { useToast } from '../ui/toast';

// --- automatic refresh ------------------------------------------------------

/** Every query of the monitoring pages starts with this key. */
export const COCKPIT = 'cockpit';

const REFRESH_INTERVAL = 30_000;
const SLOW_REFRESH_INTERVAL = 5 * 60_000;
const FOLLOW_UP = 2_000;

interface Refresh {
  paused: boolean;
  setPaused: (paused: boolean) => void;
}

const RefreshContext = createContext<Refresh>({ paused: false, setPaused: () => undefined });

export function RefreshProvider({ children }: { children: ReactNode }) {
  const [paused, setPausedState] = useState(() => readPreference('cockpit.refreshPaused', false));
  const setPaused = useCallback((next: boolean) => {
    writePreference('cockpit.refreshPaused', next);
    setPausedState(next);
  }, []);
  const value = useMemo(() => ({ paused, setPaused }), [paused, setPaused]);
  return <RefreshContext.Provider value={value}>{children}</RefreshContext.Provider>;
}

/**
 * The `refetchInterval` of a query that follows the automatic refresh: every
 * 30 s, `slow` every 5 minutes for numbers that hardly move (counts of
 * definitions, trends over days). Both stretch while nobody is at the screen
 * (see `lib/activity`); a hidden tab does not refresh at all.
 */
export function useRefreshInterval(pace: 'live' | 'slow' = 'live'): number | false {
  const paused = useContext(RefreshContext).paused;
  return usePacedInterval(paused ? false : pace === 'slow' ? SLOW_REFRESH_INTERVAL : REFRESH_INTERVAL);
}

/**
 * When the oldest data on screen was read, if a refresh of it failed: the page
 * keeps showing what it has, and says how old it is. `null` while all is fresh.
 */
export function useStaleSince(): number | null {
  const queryClient = useQueryClient();
  const read = useCallback(() => {
    let oldest: number | null = null;
    for (const query of queryClient.getQueryCache().findAll({ queryKey: [COCKPIT] })) {
      const { status, data, dataUpdatedAt } = query.state;
      if (status === 'error' && data !== undefined && query.isActive()) {
        oldest = oldest === null ? dataUpdatedAt : Math.min(oldest, dataUpdatedAt);
      }
    }
    return oldest;
  }, [queryClient]);
  return useSyncExternalStore(
    useCallback((notify) => queryClient.getQueryCache().subscribe(notify), [queryClient]),
    read,
    () => null,
  );
}

/** Shows whether the pages refresh themselves and lets the user pause that. */
export function RefreshControl() {
  const { t, dateTime } = useI18n();
  const { paused, setPaused } = useContext(RefreshContext);
  const queryClient = useQueryClient();
  const fetching = useIsFetching({ queryKey: [COCKPIT] }) > 0;
  // the interval in use: longer while nobody is at the screen
  const current = usePacedInterval(REFRESH_INTERVAL);
  const staleSince = useStaleSince();
  return (
    <div className="refresh" role="group" aria-label={t('cockpit.refresh.title')}>
      <span className="refresh__stale" role="status">
        {staleSince !== null && (
          <>
            <AlertTriangle aria-hidden size={14} />
            {t('cockpit.refresh.stale', { time: dateTime(new Date(staleSince)) })}
          </>
        )}
      </span>
      <Tooltip label={paused ? t('cockpit.refresh.resume') : t('cockpit.refresh.pause')}>
        <button
          type="button"
          className={cx('refresh__toggle', paused && 'is-paused')}
          aria-pressed={!paused}
          onClick={() => setPaused(!paused)}
        >
          <span className={cx('refresh__dot', fetching && 'is-fetching')} aria-hidden />
          <span className="refresh__text">
            {paused
              ? t('cockpit.refresh.paused')
              : t('cockpit.refresh.on', { seconds: (current || REFRESH_INTERVAL) / 1000 })}
          </span>
          {paused ? <Play aria-hidden size={14} /> : <Pause aria-hidden size={14} />}
        </button>
      </Tooltip>
      <IconButton
        icon={RefreshCw}
        label={t('action.refresh')}
        className={cx(fetching && 'is-spinning')}
        onClick={() => void queryClient.invalidateQueries({ queryKey: [COCKPIT] })}
      />
    </div>
  );
}

// --- small pieces -----------------------------------------------------------

export function totalIncidents(incidents: IncidentCount[] | null | undefined): number {
  return (incidents ?? []).reduce((sum, entry) => sum + entry.incidentCount, 0);
}

/** State is never shown by colour alone: every badge has an icon and a text. */
export function IncidentBadge({ count }: { count: number }) {
  const { t } = useI18n();
  if (count <= 0) {
    return null;
  }
  return (
    <span className="badge badge--danger">
      <AlertTriangle aria-hidden size={12} />
      {t('cockpit.incidents.count', { count })}
    </span>
  );
}

export function StateBadge({ suspended }: { suspended: boolean }) {
  const { t } = useI18n();
  return suspended ? (
    <span className="badge badge--warning">
      <Pause aria-hidden size={12} />
      {t('cockpit.state.suspended')}
    </span>
  ) : (
    <span className="badge badge--success">
      <CircleCheck aria-hidden size={12} />
      {t('cockpit.state.active')}
    </span>
  );
}

// --- actions ----------------------------------------------------------------

/**
 * Runs a change, reports the outcome and reloads what the pages show. The
 * result tells the caller whether the change went through.
 */
export function useCockpitAction(): (
  call: () => Promise<unknown>,
  messages: { success: string; failure: string },
) => Promise<boolean> {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useCallback(
    async (call, messages) => {
      try {
        await call();
        toast.show({ title: messages.success });
        return true;
      } catch (error) {
        toast.error(error, messages.failure);
        return false;
      } finally {
        void queryClient.invalidateQueries({ queryKey: [COCKPIT] });
        // what the engine does in the background, e.g. a retried job, shows up a moment later
        window.setTimeout(() => void queryClient.invalidateQueries({ queryKey: [COCKPIT] }), FOLLOW_UP);
      }
    },
    [queryClient, toast],
  );
}

/** Names of the elements of a model by their id; the model is only read, never shown. */
export function elementNames(xml: string | undefined): Map<string, string> {
  const names = new Map<string, string>();
  if (!xml) {
    return names;
  }
  try {
    const model = new DOMParser().parseFromString(xml, 'application/xml');
    for (const element of Array.from(model.querySelectorAll('[id][name]'))) {
      const name = element.getAttribute('name')?.replace(/\s+/g, ' ').trim();
      const id = element.getAttribute('id');
      if (id && name) {
        names.set(id, name);
      }
    }
  } catch {
    // ids are shown instead of names
  }
  return names;
}

/**
 * Says that a view covers every tenant although the lists are narrowed to
 * one: the engine offers no tenant filter for it.
 */
export function EngineWideNote() {
  const { t } = useI18n();
  const tenant = tenantScope();
  return tenant ? <p className="field__hint engine-wide">{t('tenant.engineWide', { tenant })}</p> : null;
}
