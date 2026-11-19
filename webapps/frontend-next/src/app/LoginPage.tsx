/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router';
import { isApiError } from '../api/http';
import { ssoLoginHref } from '../api/sso';
import { enginePageUrl, env } from '../env';
import { useI18n } from '../i18n';
import { Button, Field, Loading, Notice, describeError } from '../ui';
import { Wordmark } from './Logo';
import { LanguageSuggestion, LanguageSwitch, ThemeSwitch } from './preferences';
import { useLogin, useSessionQuery, useSsoConfig } from './session';

export function LoginPage() {
  const { t } = useI18n();
  const location = useLocation();
  const session = useSessionQuery();
  const sso = useSsoConfig();
  const login = useLogin();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);

  if (session.isPending || sso.isPending) {
    return <Loading label={t('state.loading')} />;
  }
  if (session.data) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from && from !== '/login' ? from : '/'} replace />;
  }

  if (sso.data?.enabled) {
    const config = sso.data;
    // the identity provider sends the user back to the page that was asked for
    const from = (location.state as { from?: string } | null)?.from;
    const route = from && from !== '/login' && from.startsWith('/') && !from.startsWith('//') ? `#${from}` : '';
    return (
      <main className="login">
        <div className="login__tools">
          <LanguageSuggestion />
          <LanguageSwitch />
          <ThemeSwitch />
        </div>
        <div className="login__card">
          <Wordmark size={36} />
          <h1 className="login__title">{t('login.title')}</h1>
          {env.engine !== 'default' && (
            <p className="login__engine">{t('login.engine', { engine: env.engine })}</p>
          )}
          <p className="login__hint">{t('login.ssoHint')}</p>
          <a
            className="btn btn--primary btn--md login__submit"
            href={ssoLoginHref(config, `${enginePageUrl(env.engine)}${route}`)}
          >
            {config.label ? t('login.ssoWith', { provider: config.label }) : t('login.sso')}
          </a>
        </div>
        <p className="login__footer">{t('about.builtOn')}</p>
      </main>
    );
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    if (username.trim() && password) {
      login.mutate({ username: username.trim(), password });
    }
  };

  let failure: string | null = null;
  if (login.error) {
    if (isApiError(login.error, 'unauthorized')) {
      failure = t('login.failed');
    } else if (isApiError(login.error, 'forbidden')) {
      failure = t('login.forbidden');
    } else {
      failure = describeError(login.error, t).title;
    }
  }

  return (
    <main className="login">
      <div className="login__tools">
        <LanguageSuggestion />
        <LanguageSwitch />
        <ThemeSwitch />
      </div>
      <form className="login__card" onSubmit={submit} noValidate>
        <Wordmark size={36} />
        <h1 className="login__title">{t('login.title')}</h1>
        {env.engine !== 'default' && (
          <p className="login__engine">{t('login.engine', { engine: env.engine })}</p>
        )}
        {failure && <Notice tone="danger">{failure}</Notice>}
        <Field
          label={t('login.username')}
          error={touched && !username.trim() ? t('validation.required') : null}
        >
          {(props) => (
            <input
              {...props}
              className="input"
              name="username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              autoFocus
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          )}
        </Field>
        <Field
          label={t('login.password')}
          error={touched && !password ? t('validation.required') : null}
        >
          {(props) => (
            <input
              {...props}
              className="input"
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          )}
        </Field>
        <Button type="submit" variant="primary" busy={login.isPending} className="login__submit">
          {t('login.submit')}
        </Button>
      </form>
      <p className="login__footer">{t('about.builtOn')}</p>
    </main>
  );
}
