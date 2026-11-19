/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, useEffect, useRef } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { useI18n, type MessageKey } from '../i18n';
import { Loading, Skeleton } from '../ui';
import '../cockpit/cockpit.css';
import './admin.css';
import { OverflowRow } from '../app/OverflowMenu';

const SECTIONS: { to: string; label: MessageKey }[] = [
  { to: '/admin/users', label: 'admin.nav.users' },
  { to: '/admin/groups', label: 'admin.nav.groups' },
  { to: '/admin/tenants', label: 'admin.nav.tenants' },
  { to: '/admin/authorizations', label: 'admin.nav.authorizations' },
  { to: '/admin/system', label: 'admin.nav.system' },
];

/** The frame of the administration: its sections and the page that is open. */
export function AdminLayout() {
  const { t } = useI18n();
  const { pathname } = useLocation();
  const content = useRef<HTMLDivElement>(null);

  useEffect(() => {
    content.current?.scrollTo?.(0, 0);
  }, [pathname]);

  return (
    <div className="cockpit">
      <div className="cockpit__bar">
        <OverflowRow className="cockpit__navrow" selector=".subnav-link" label={t('nav.more')} watch={pathname}>
        <nav className="cockpit__nav" aria-label={t('admin.nav.title')}>
          {SECTIONS.map((section) => (
            <NavLink key={section.to} to={section.to} className="subnav-link">
              {t(section.label)}
            </NavLink>
          ))}
        </nav>
        </OverflowRow>
      </div>
      <div className="cockpit__content" ref={content}>
        <Suspense
          fallback={
            <Loading label={t('state.loading')}>
              <Skeleton lines={6} />
            </Loading>
          }
        >
          <Outlet />
        </Suspense>
      </div>
    </div>
  );
}
