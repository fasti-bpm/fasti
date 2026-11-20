/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { CircleCheck } from 'lucide-react';
import { createInitialUser } from '../api/admin';
import { Wordmark } from '../app/Logo';
import { LanguageSwitch, ThemeSwitch } from '../app/preferences';
import { enginePageUrl, env } from '../env';
import { useI18n } from '../i18n';
import { Notice } from '../ui';
import { NewUserForm } from './UsersPage';
import './admin.css';

/**
 * The first start of an engine without users: whoever opens the application
 * creates the first administrator. The server sends every visitor here as
 * long as no administrator exists, and refuses the request afterwards.
 */
export function SetupPage() {
  const { t } = useI18n();
  const [created, setCreated] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  return (
    <main className="login">
      <div className="login__tools">
        <LanguageSwitch />
        <ThemeSwitch />
      </div>
      <div className="login__card admin-setup">
        <Wordmark size={36} />
        <h1 className="login__title">{t('admin.setup.title')}</h1>
        {env.engine !== 'default' && <p className="login__engine">{t('login.engine', { engine: env.engine })}</p>}
        {created ? (
          <>
            <Notice title={t('admin.setup.done', { id: created })}>{t('admin.setup.doneText')}</Notice>
            <a className="btn btn--primary btn--md login__submit" href={`${enginePageUrl(env.engine)}#/login`}>
              <CircleCheck className="btn__icon" aria-hidden size={16} />
              {t('admin.setup.signIn')}
            </a>
          </>
        ) : (
          <>
            <p className="muted">{t('admin.setup.text')}</p>
            {failed && <Notice tone="danger">{t('admin.setup.failed')}</Notice>}
            <NewUserForm
              submitLabel={t('admin.setup.submit')}
              onSubmit={async (user) => {
                try {
                  await createInitialUser(user);
                  setCreated(user.id);
                  return true;
                } catch {
                  setFailed(true);
                  return false;
                }
              }}
            />
          </>
        )}
      </div>
      <p className="login__footer">{t('about.builtOn')}</p>
    </main>
  );
}
