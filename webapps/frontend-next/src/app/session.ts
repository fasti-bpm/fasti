/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { createContext, useContext, useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import * as api from '../api/engine';
import { getSsoConfig, ssoLogout } from '../api/sso';
import type { SsoConfig } from '../api/sso';
import { invalidateCsrfToken, onUnauthorized } from '../api/http';
import { env } from '../env';
import { clearDrafts } from '../forms/embedded/broker';
import {
  connectPreferences,
  disconnectPreferences,
  flushPreferences,
  onOwnerChanged,
  onPreferencesRejected,
} from '../lib/storage';
import { clearReceipts } from '../cockpit/receipts';
import { useI18n } from '../i18n';
import { useToast } from '../ui/toast';

export interface Session {
  userId: string;
  displayName: string;
  authorizedApps: string[];
}

const sessionKey = ['session', env.engine] as const;
const ssoKey = ['session', 'sso'] as const;

async function loadSession(): Promise<Session | null> {
  const authentication = await api.getAuthentication();
  if (!authentication) {
    return null;
  }
  const [profile] = await Promise.all([
    api.getUserProfile(authentication.userId).catch(() => null),
    loadPreferences(authentication.userId),
  ]);
  return {
    userId: authentication.userId,
    displayName: profile ? api.fullName(profile) : authentication.userId,
    authorizedApps: authentication.authorizedApps,
  };
}

/** How long the start waits for the preferences stored on the server. */
const PREFERENCES_WAIT = 3000;

/**
 * Connects the preferences to the server copy of the user. The start waits a
 * moment for it, so the first page already shows the user's choices; a slow
 * answer is applied when it arrives.
 */
function loadPreferences(userId: string): Promise<void> {
  const connected = connectPreferences(userId, {
    load: api.getPreferences,
    save: api.savePreferences,
  }, env.engine);
  return Promise.race([connected, new Promise<void>((resolve) => window.setTimeout(resolve, PREFERENCES_WAIT))]);
}

/** Forgets everything that was loaded for the user. */
function endSession(queryClient: QueryClient): void {
  disconnectPreferences();
  // read with the permissions of this user: the next one asks for them anew
  api.resetBusinessKeys();
  queryClient.setQueryData(sessionKey, null);
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'session' });
}

/** Whether users sign in at an identity provider instead of with a password. */
export function useSsoConfig() {
  return useQuery({ queryKey: ssoKey, queryFn: getSsoConfig, staleTime: Infinity, retry: false });
}

export function useSessionQuery() {
  return useQuery({ queryKey: sessionKey, queryFn: loadSession, staleTime: Infinity, retry: false });
}

/**
 * The session of the user who is logged in. It is provided by the component
 * that guards the application shell: components below it never see a state
 * without a user, they are removed as soon as the session ends.
 */
export const SessionContext = createContext<Session | null>(null);

/** The user who is logged in; only for components below the application shell. */
export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error('No user is logged in');
  }
  return session;
}

/** Reloads the session, e.g. after the user changed the own name. */
export function useRefreshSession(): () => Promise<void> {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: sessionKey });
}

/** Sends the user to the login page as soon as the API reports a missing session. */
export function useUnauthorizedHandler(): void {
  const queryClient = useQueryClient();
  useEffect(() => onUnauthorized(() => endSession(queryClient)), [queryClient]);
}

/** A focus of the window checks the session at most this often; a login in another tab at once. */
const FOCUS_CHECK_INTERVAL = 10_000;
/** In the storage of the tab: the page was reloaded because another tab logged in as someone else. */
const SWITCHED_KEY = 'fasti.next-switched';

export interface SessionWatch {
  userId: string;
  /** who is logged in now, `null` for nobody */
  current: () => Promise<{ userId: string } | null>;
  /** nobody is logged in any more */
  onEnded: () => void;
  /** someone else is logged in now */
  onSwitched: () => void;
}

/**
 * Notices that the session of the browser changed under this tab: the tabs of a browser share it,
 * so a logout and a login as someone else in another tab hand this tab to that user, while it
 * still shows (and would act as) the user before. Checked when another tab logged in, and when
 * the window gets the focus again. Answers the function that stops watching.
 */
export function watchSession({ userId, current, onEnded, onSwitched }: SessionWatch): () => void {
  let stopped = false;
  let checking = false;
  /** a login elsewhere while a check was under way: that check may have asked before it */
  let recheck = false;
  let checkedAt = Date.now();
  const check = async (force: boolean): Promise<void> => {
    if (checking) {
      recheck ||= force;
      return;
    }
    if (stopped || (!force && Date.now() - checkedAt < FOCUS_CHECK_INTERVAL)) return;
    checking = true;
    checkedAt = Date.now();
    try {
      const now = await current();
      if (stopped) return;
      if (!now) {
        stopped = true;
        onEnded();
      } else if (now.userId !== userId) {
        stopped = true;
        onSwitched();
      }
    } catch {
      // not reachable now: checked again with the next occasion
    } finally {
      checking = false;
    }
    if (recheck) {
      recheck = false;
      await check(true);
    }
  };
  const onFocus = () => void check(false);
  const onVisible = () => {
    if (document.visibilityState === 'visible') void check(false);
  };
  const offOwner = onOwnerChanged(() => void check(true));
  window.addEventListener('focus', onFocus);
  document.addEventListener('visibilitychange', onVisible);
  return () => {
    stopped = true;
    offOwner();
    window.removeEventListener('focus', onFocus);
    document.removeEventListener('visibilitychange', onVisible);
  };
}

/** Taken once per page: whether it was reloaded for another user. */
let switchedNotice = takeSwitchedNotice();

function takeSwitchedNotice(): boolean {
  try {
    const switched = window.sessionStorage.getItem(SWITCHED_KEY) !== null;
    window.sessionStorage.removeItem(SWITCHED_KEY);
    return switched;
  } catch {
    return false;
  }
}

/**
 * Starts anew as the user who is logged in now: nothing of the user before survives a reload. The
 * address stays, so the page shows the same place for that user (or what it may see of it).
 */
function reloadForOtherUser(): void {
  // the shared local storage belongs to the other user now: this tab sends nothing more
  disconnectPreferences();
  try {
    window.sessionStorage.setItem(SWITCHED_KEY, '1');
  } catch {
    // reloaded without the notice
  }
  window.location.reload();
}

/**
 * Keeps the shown session that of the browser (see {@link watchSession}) and says so after a
 * reload for another user. For the application shell.
 */
export function useSessionWatch(): void {
  const queryClient = useQueryClient();
  const session = useSession();
  const toast = useToast();
  const { t } = useI18n();
  const userId = session.userId;
  useEffect(() => {
    if (!switchedNotice) return;
    switchedNotice = false;
    toast.show({ tone: 'error', title: t('session.switched', { user: session.displayName }) });
  }, [toast, t, session.displayName]);
  useEffect(
    () =>
      watchSession({
        userId,
        current: api.getAuthentication,
        onEnded: () => endSession(queryClient),
        onSwitched: reloadForOtherUser,
      }),
    [queryClient, userId],
  );
}

/** Tells the user once when the server refuses the preferences; they stay in this browser. */
export function usePreferenceNotices(): void {
  const toast = useToast();
  const { t } = useI18n();
  useEffect(
    () => onPreferencesRejected(() => toast.show({ tone: 'error', title: t('prefs.syncRejected') })),
    [toast, t],
  );
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ username, password }: { username: string; password: string }) => {
      await api.login(username, password);
      // the server replaced the session: the next request fetches a new token
      invalidateCsrfToken();
      // a reload for another user that ended at the login page is not worth a notice any more
      switchedNotice = false;
      const session = await loadSession();
      queryClient.setQueryData(sessionKey, session);
      return session;
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await flushPreferences();
      const userId = queryClient.getQueryData<Session | null>(sessionKey)?.userId;
      if (userId) {
        // entered values of forms must not outlive the session of the user
        try {
          clearDrafts(env.engine, userId);
        } catch {
          // storage unavailable: nothing is kept either
        }
      }
      clearReceipts();
      const sso: SsoConfig =
        queryClient.getQueryData<SsoConfig>(ssoKey) ??
        (await queryClient.fetchQuery<SsoConfig>({ queryKey: ssoKey, queryFn: getSsoConfig, staleTime: Infinity }));
      if (!sso.enabled) {
        await api.logout();
        return;
      }
      // ends the session of the server, then the one of the identity provider
      const target = await ssoLogout(sso);
      window.location.assign(target);
    },
    onSettled: () => endSession(queryClient),
  });
}
