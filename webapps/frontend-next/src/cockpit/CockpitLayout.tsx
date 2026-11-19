/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, useEffect, useRef } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useI18n } from '../i18n';
import { COCKPIT_GROUPS } from '../app/navigation';
import { BatchReceipts } from './BatchReceipts';
import { Loading, Skeleton } from '../ui';
import { COCKPIT, RefreshControl, RefreshProvider } from './common';
import './cockpit.css';
import { OverflowRow } from '../app/OverflowMenu';

/** Frame of the monitoring pages: their navigation and the refresh indicator. */
export function CockpitLayout() {
  const { t } = useI18n();
  const { pathname } = useLocation();
  const queryClient = useQueryClient();
  const content = useRef<HTMLDivElement>(null);

  // a new page starts at its top and with current numbers: what was loaded
  // for an earlier visit is shown at once and replaced as soon as it arrives
  useEffect(() => {
    content.current?.scrollTo?.(0, 0);
    void queryClient.invalidateQueries({ queryKey: [COCKPIT], refetchType: 'active' });
  }, [pathname, queryClient]);

  return (
    <RefreshProvider>
      <div className="cockpit">
        <div className="cockpit__bar">
          <OverflowRow className="cockpit__navrow" selector=".subnav-link" label={t('nav.more')} watch={pathname}>
          <nav className="cockpit__nav" aria-label={t('cockpit.nav.title')}>
            {COCKPIT_GROUPS.map((group) => (
              <div key={group.label} className="cockpit__group" role="group" aria-label={t(group.label)}>
                <span className="cockpit__group-label" aria-hidden>
                  {t(group.label)}
                </span>
                {group.sections.map((section) => (
                  <NavLink key={section.to} to={section.to} end={section.end} className="subnav-link">
                    {t(section.label)}
                  </NavLink>
                ))}
              </div>
            ))}
          </nav>
          </OverflowRow>
          <RefreshControl />
        </div>
        <div className="cockpit__content" ref={content}>
          <BatchReceipts />
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
    </RefreshProvider>
  );
}
