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
import { connectPreferences, disconnectPreferences, flushPreferences } from '../lib/storage';
import { clearReceipts } from '../cockpit/receipts';

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

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ username, password }: { username: string; password: string }) => {
      await api.login(username, password);
      // the server replaced the session: the next request fetches a new token
      invalidateCsrfToken();
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
