/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { onPreferencesReplaced, readPreference, writePreference } from '../lib/storage';

export type ThemeChoice = 'system' | 'light' | 'dark';
export type ResolvedTheme = 'light' | 'dark';

interface ThemeState {
  choice: ThemeChoice;
  resolved: ResolvedTheme;
  setChoice: (choice: ThemeChoice) => void;
}

const ThemeContext = createContext<ThemeState | null>(null);

const DARK_QUERY = '(prefers-color-scheme: dark)';

function systemTheme(): ResolvedTheme {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches
    ? 'dark'
    : 'light';
}

function storedChoice(): ThemeChoice {
  const stored = readPreference<string>('theme', 'system');
  return stored === 'light' || stored === 'dark' ? stored : 'system';
}

/** Applies the stored choice before the first render to avoid a flash. */
export function applyStoredTheme(): void {
  const choice = storedChoice();
  if (choice === 'system') {
    delete document.documentElement.dataset.theme;
  } else {
    document.documentElement.dataset.theme = choice;
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<ThemeChoice>(storedChoice);
  const [system, setSystem] = useState<ResolvedTheme>(systemTheme);

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      return;
    }
    const query = window.matchMedia(DARK_QUERY);
    const listener = () => setSystem(query.matches ? 'dark' : 'light');
    query.addEventListener('change', listener);
    return () => query.removeEventListener('change', listener);
  }, []);

  useEffect(() => {
    if (choice === 'system') {
      delete document.documentElement.dataset.theme;
    } else {
      document.documentElement.dataset.theme = choice;
    }
  }, [choice]);

  useEffect(() => onPreferencesReplaced(() => setChoiceState(storedChoice())), []);

  const setChoice = useCallback((next: ThemeChoice) => {
    writePreference('theme', next);
    setChoiceState(next);
  }, []);

  const value = useMemo<ThemeState>(
    () => ({ choice, resolved: choice === 'system' ? system : choice, setChoice }),
    [choice, system, setChoice],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeState {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used inside ThemeProvider');
  }
  return context;
}
