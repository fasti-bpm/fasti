/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ComponentType } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Tabs } from 'radix-ui';
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  ExternalLink,
  ListChecks,
  Lock,
  PackageOpen,
  PenTool,
  Play,
  ShieldCheck,
  Table2,
  UserRound,
  type LucideProps,
} from 'lucide-react';
import { getUser } from '../api/admin';
import { countDefinitions } from '../api/cockpit';
import { tenantScope } from '../lib/userPrefs';
import { useRefreshSession, useSession } from '../app/session';
import { ADMIN, ReadOnlyNotice, useAccess } from '../admin/common';
import { PasswordTab, ProfileTab } from '../admin/UsersPage';
import { Card, PageHeader } from '../cockpit/common';
import { classicAppUrl, classicWebapps, env, type ClassicApp } from '../env';
import { useI18n, type MessageKey } from '../i18n';
import { EmptyState, ErrorState, Loading, Skeleton } from '../ui';
import '../cockpit/cockpit.css';
import '../admin/admin.css';
import './welcome.css';

interface AppEntry {
  /** the application whose permission the server checks */
  app: string;
  to: string;
  title: MessageKey;
  text: MessageKey;
  icon: ComponentType<LucideProps>;
}

const APPS: AppEntry[] = [
  { app: 'tasklist', to: '/tasklist', title: 'nav.tasklist', text: 'welcome.app.tasklist', icon: ListChecks },
  { app: 'cockpit', to: '/cockpit', title: 'nav.cockpit', text: 'welcome.app.cockpit', icon: Activity },
  { app: 'admin', to: '/admin', title: 'nav.admin', text: 'welcome.app.admin', icon: ShieldCheck },
];

interface QuickLink {
  app: string;
  to: string;
  label: MessageKey;
  icon: ComponentType<LucideProps>;
}

const QUICK_LINKS: QuickLink[] = [
  { app: 'tasklist', to: '/processes', label: 'nav.processes', icon: Play },
  { app: 'cockpit', to: '/cockpit/incidents', label: 'welcome.link.incidents', icon: AlertTriangle },
  { app: 'cockpit', to: '/cockpit/decisions', label: 'welcome.link.decisions', icon: Table2 },
  { app: 'admin', to: '/admin/users', label: 'welcome.link.users', icon: UserRound },
];

const CLASSIC_APPS: { app: ClassicApp; label: MessageKey }[] = [
  { app: 'tasklist', label: 'welcome.classic.tasklist' },
  { app: 'cockpit', label: 'welcome.classic.cockpit' },
  { app: 'admin', label: 'welcome.classic.admin' },
];

function Apps({ canUse }: { canUse: (app: string) => boolean }) {
  const { t } = useI18n();
  const apps = APPS.filter((entry) => canUse(entry.app));
  if (apps.length === 0) {
    return (
      <Card>
        <EmptyState title={t('welcome.noApps')}>{t('welcome.noAppsHint')}</EmptyState>
      </Card>
    );
  }
  return (
    <ul className="welcome-apps" aria-label={t('welcome.apps')}>
      {apps.map((entry) => {
        const Icon = entry.icon;
        return (
          <li key={entry.app}>
            <Link className="welcome-app" to={entry.to}>
              <span className="welcome-app__icon" aria-hidden>
                <Icon size={22} />
              </span>
              <span className="welcome-app__text">
                <span className="welcome-app__title">{t(entry.title)}</span>
                <span className="welcome-app__description">{t(entry.text)}</span>
              </span>
              <ArrowRight className="welcome-app__arrow" aria-hidden size={18} />
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

function QuickLinks({ canUse }: { canUse: (app: string) => boolean }) {
  const { t } = useI18n();
  const links = QUICK_LINKS.filter((link) => canUse(link.app));
  const classicEnabled = classicWebapps.enabled;
  const classic = classicEnabled ? CLASSIC_APPS.filter((entry) => canUse(entry.app)) : [];
  return (
    <Card title={t('welcome.links')}>
      <ul className="welcome-links">
        {links.map((link) => {
          const Icon = link.icon;
          return (
            <li key={link.to}>
              <Link className="welcome-link" to={link.to}>
                <Icon aria-hidden size={16} />
                {t(link.label)}
              </Link>
            </li>
          );
        })}
        {classic.map((entry) => (
          <li key={entry.app}>
            <a className="welcome-link" href={classicAppUrl(entry.app)}>
              <ExternalLink aria-hidden size={16} />
              {t(entry.label)}
            </a>
          </li>
        ))}
        {classicEnabled && (
          <li>
            <a className="welcome-link" href={classicAppUrl('welcome')}>
              <ExternalLink aria-hidden size={16} />
              {t('welcome.classic.welcome')}
            </a>
          </li>
        )}
      </ul>
      {classicEnabled && canUse('tasklist') && (
        <p className="welcome-note muted">{t('welcome.classic.formsHint')}</p>
      )}
    </Card>
  );
}

/**
 * What a new installation needs first: a process. Shown while nothing is
 * deployed that the user may see, with the steps that lead to a first one.
 */
function FirstSteps({ canUse }: { canUse: (app: string) => boolean }) {
  const { t } = useI18n();
  const cockpit = canUse('cockpit');
  const deployed = useQuery({
    queryKey: ['welcome', 'definitions', tenantScope()],
    queryFn: ({ signal }) => countDefinitions(signal),
    enabled: cockpit || canUse('tasklist'),
    staleTime: 60_000,
  });
  if (deployed.data !== 0) {
    return null;
  }
  return (
    <Card title={t('welcome.first.title')} className="welcome-first">
      <p className="muted">{t('welcome.first.text')}</p>
      <ol className="welcome-first__steps">
        <li>
          {cockpit ? (
            <Link className="welcome-link" to="/cockpit/modeler">
              <PenTool aria-hidden size={16} />
              {t('welcome.first.model')}
            </Link>
          ) : (
            t('welcome.first.model')
          )}
        </li>
        <li>
          {cockpit ? (
            <Link className="welcome-link" to="/cockpit/deployments">
              <PackageOpen aria-hidden size={16} />
              {t('welcome.first.deploy')}
            </Link>
          ) : (
            t('welcome.first.deploy')
          )}
        </li>
        <li>{t('welcome.first.start')}</li>
      </ol>
      {!cockpit && <p className="field__hint">{t('welcome.first.askAdmin')}</p>}
    </Card>
  );
}

/** The own profile: name and e-mail address, and the password if the engine stores it. */
function Profile() {
  const { t } = useI18n();
  const session = useSession();
  const refreshSession = useRefreshSession();
  const user = useQuery({ queryKey: [ADMIN, 'user', session.userId], queryFn: () => getUser(session.userId) });
  const { access, known } = useAccess(`/user/${encodeURIComponent(session.userId)}`);
  // the engine only offers changes if the identity provider is writable and the user may change the profile
  const canEdit = access.has('update');

  let body;
  if (user.isError && !user.data) {
    body = <ErrorState error={user.error} onRetry={() => void user.refetch()} />;
  } else if (!user.data) {
    body = (
      <Loading label={t('state.loading')}>
        <Skeleton lines={4} />
      </Loading>
    );
  } else {
    const current = user.data;
    body = (
      <>
        {known && !canEdit && (
          <div className="welcome-profile__notice">
            <ReadOnlyNotice show />
          </div>
        )}
        {canEdit ? (
          <Tabs.Root className="ctabs" defaultValue="profile">
            <Tabs.List className="ctabs__list" aria-label={t('welcome.profile')}>
              <Tabs.Trigger className="ctabs__tab" value="profile">
                {t('profile.tab.profile')}
              </Tabs.Trigger>
              <Tabs.Trigger className="ctabs__tab" value="password">
                <Lock aria-hidden size={14} />
                {t('profile.tab.password')}
              </Tabs.Trigger>
            </Tabs.List>
            <Tabs.Content value="profile" className="ctabs__content">
              <ProfileTab
                key={JSON.stringify(current)}
                user={current}
                canEdit
                onSaved={() => void refreshSession()}
              />
            </Tabs.Content>
            <Tabs.Content value="password" className="ctabs__content">
              <PasswordTab user={current} self />
            </Tabs.Content>
          </Tabs.Root>
        ) : (
          <ProfileTab key={JSON.stringify(current)} user={current} canEdit={false} />
        )}
      </>
    );
  }

  return (
    <Card
      flush
      title={t('welcome.profile')}
      action={
        <span className="welcome-profile__id mono" title={t('admin.user.id')}>
          {session.userId}
          {env.engine !== 'default' && ` · ${env.engine}`}
        </span>
      }
      className="welcome-profile"
    >
      {body}
    </Card>
  );
}

/** The landing page after the login: the applications of the user, the own profile and quick links. */
export function WelcomePage() {
  const { t } = useI18n();
  const session = useSession();
  const canUse = (app: string) => session.authorizedApps.includes(app);
  return (
    <div className="cockpit">
      <div className="cockpit__content">
        <div className="page welcome">
          <PageHeader
            title={t('welcome.title', { name: session.displayName })}
            subtitle={t('welcome.subtitle')}
          />
          <FirstSteps canUse={canUse} />
          <Apps canUse={canUse} />
          <div className="welcome-grid">
            <Profile />
            <QuickLinks canUse={canUse} />
          </div>
        </div>
      </div>
    </div>
  );
}
