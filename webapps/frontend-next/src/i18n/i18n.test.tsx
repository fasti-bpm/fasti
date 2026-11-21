/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';
import { render, screen, waitFor } from '@testing-library/react';
import { act } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { config, loadConfig } from '../config';
import {
  I18nProvider,
  LOCALES,
  LOCALE_MANIFESTS,
  browserLocale,
  manifestOf,
  translate,
  useI18n,
  type MessageKey,
} from './index';

describe('message catalogues', () => {
  it('pass the catalogue check (missing, extra keys, placeholders, plural forms)', () => {
    const script = join(__dirname, '..', '..', 'scripts', 'i18n.mjs');
    expect(() => execFileSync(process.execPath, [script, 'check'], { stdio: 'pipe' })).not.toThrow();
  });

  it('discovers every language folder, English first', () => {
    expect(LOCALES[0]).toBe('en');
    expect(LOCALES).toEqual(expect.arrayContaining(['vi', 'de', 'ru', 'zh-CN', 'ja', 'ko', 'th', 'hi', 'id', 'pt-BR', 'es', 'fr']));
    for (const manifest of LOCALE_MANIFESTS) {
      expect(['ltr', 'rtl']).toContain(manifest.dir);
      expect(manifest.nativeName).not.toBe('');
    }
  });
});

describe('translate', () => {
  it('replaces placeholders', () => {
    expect(translate('en', 'task.assigned', { user: 'mary' })).toBe('Assigned to mary');
    expect(translate('vi', 'task.assigned', { user: 'mary' })).toBe('Đã giao cho mary');
  });

  it('chooses the plural form with the rules of the language', () => {
    expect(translate('en', 'tasks.count', { count: 1 })).toBe('1 task');
    expect(translate('en', 'tasks.count', { count: 3 })).toBe('3 tasks');
    expect(translate('vi', 'tasks.count', { count: 1 })).toBe('1 công việc');
    expect(translate('ru', 'tasks.count', { count: 1 })).toBe('1 задача');
    expect(translate('ru', 'tasks.count', { count: 3 })).toBe('3 задачи');
    expect(translate('ru', 'tasks.count', { count: 5 })).toBe('5 задач');
    expect(translate('ru', 'tasks.count', { count: 21 })).toBe('21 задача');
  });

  it('formats a numeric count itself, so that the plural form still applies', () => {
    expect(translate('en', 'ops.restart.title', { count: 1 })).toBe('Restart 1 instance');
    expect(translate('en', 'ops.restart.title', { count: 1234 })).toBe('Restart 1,234 instances');
    // a count that is already text is shown as it is
    expect(translate('en', 'tasks.count', { count: '7' })).toBe('7 tasks');
  });

  it('falls back to English for a message a language lacks, and to the key for an unknown one', () => {
    expect(translate('xx', 'login.submit')).toBe('Sign in');
    expect(translate('vi', 'no.such.key' as MessageKey)).toBe('no.such.key');
  });
});

describe('browser language', () => {
  it('matches exactly, then by primary language', () => {
    expect(browserLocale(['de-AT', 'en'])).toBe('de');
    expect(browserLocale(['pt-br'])).toBe('pt-BR');
    expect(browserLocale(['zh'])).toBe('zh-CN');
    expect(browserLocale(['xx'])).toBeNull();
  });

  it('defaults to left to right', () => {
    expect(manifestOf('unknown').dir).toBe('ltr');
  });
});

function CurrentLocale() {
  const { locale, t } = useI18n();
  return <p>{`${locale}: ${t('login.submit')}`}</p>;
}

describe('language of the user interface', () => {
  it('is English unless the user chose another one', () => {
    Object.defineProperty(window.navigator, 'language', { value: 'vi-VN', configurable: true });
    render(
      <I18nProvider>
        <CurrentLocale />
      </I18nProvider>,
    );
    expect(screen.getByText('en: Sign in')).toBeVisible();
    expect(document.documentElement.lang).toBe('en');
    expect(document.documentElement.dir).toBe('ltr');
  });

  it('is the language the user chose earlier', () => {
    window.localStorage.setItem('fasti.next.locale', '"vi"');
    render(
      <I18nProvider>
        <CurrentLocale />
      </I18nProvider>,
    );
    expect(screen.getByText('vi: Đăng nhập')).toBeVisible();
  });

  it('follows the default of the installation', async () => {
    config.defaultLocale = 'de';
    render(
      <I18nProvider>
        <CurrentLocale />
      </I18nProvider>,
    );
    await waitFor(() => expect(screen.getByText(/^de: /)).toBeVisible());
    expect(document.documentElement.lang).toBe('de');
  });

  it('follows a default of the installation that arrives after the page started', async () => {
    let answer: (response: Response) => void = () => undefined;
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>((resolve) => (answer = resolve))));
    // the page gives up waiting and starts in English
    await loadConfig(1);
    render(
      <I18nProvider>
        <CurrentLocale />
      </I18nProvider>,
    );
    expect(screen.getByText('en: Sign in')).toBeVisible();

    await act(async () => answer(new Response(JSON.stringify({ defaultLocale: 'vi' }))));

    await waitFor(() => expect(screen.getByText('vi: Đăng nhập')).toBeVisible());
  });

  it('keeps the language the user chose over a default that arrives late', async () => {
    let answer: (response: Response) => void = () => undefined;
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>((resolve) => (answer = resolve))));
    await loadConfig(1);
    function Choose() {
      const { setLocale } = useI18n();
      return (
        <button type="button" onClick={() => setLocale('de')}>
          choose
        </button>
      );
    }
    render(
      <I18nProvider>
        <CurrentLocale />
        <Choose />
      </I18nProvider>,
    );
    await act(async () => screen.getByRole('button', { name: 'choose' }).click());
    await waitFor(() => expect(screen.getByText(/^de: /)).toBeVisible());

    await act(async () => answer(new Response(JSON.stringify({ defaultLocale: 'vi' }))));

    expect(screen.getByText(/^de: /)).toBeVisible();
  });
});
