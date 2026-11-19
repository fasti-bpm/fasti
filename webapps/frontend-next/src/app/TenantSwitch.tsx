/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useState } from 'react';
import { DropdownMenu } from 'radix-ui';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Building2, Check } from 'lucide-react';
import { env } from '../env';
import { request, seg } from '../api/http';
import { useI18n } from '../i18n';
import { setTenantScope, tenantScope } from '../lib/userPrefs';
import { cx } from '../ui';
import { useSession } from './session';
import { MenuContent } from '../ui/menu';

interface TenantDto {
  id: string;
  name: string | null;
}

/** The tenants the user belongs to, directly or through a group. */
const MY_TENANTS_LIMIT = 200;

export function getMyTenants(userId: string): Promise<TenantDto[]> {
  return request<TenantDto[]>(`/engine/engine/${seg(env.engine)}/tenant`, {
    query: { userMember: userId, includingGroupsOfUser: true, sortBy: 'name', sortOrder: 'asc', maxResults: MY_TENANTS_LIMIT },
  });
}

/**
 * Narrows the lists to one tenant. Only shown if the user belongs to a
 * tenant, i.e. where multi-tenancy is in use.
 */
export function TenantSwitch() {
  const { t } = useI18n();
  const session = useSession();
  const queryClient = useQueryClient();
  const [current, setCurrent] = useState(tenantScope);
  const tenants = useQuery({
    queryKey: ['my-tenants', session.userId],
    queryFn: () => getMyTenants(session.userId),
    staleTime: 5 * 60_000,
    retry: false,
  });
  const choose = (tenantId: string | null) => {
    setTenantScope(tenantId);
    setCurrent(tenantId);
    // every list is read again for the new scope
    void queryClient.resetQueries({ predicate: (query) => query.queryKey[0] !== 'my-tenants' });
  };
  // a tenant the user no longer belongs to cannot stay the scope of the lists; a full page of
  // memberships may miss the chosen one, so only a complete list decides
  const lost =
    !!current &&
    !!tenants.data &&
    tenants.data.length < MY_TENANTS_LIMIT &&
    !tenants.data.some((tenant) => tenant.id === current);
  useEffect(() => {
    if (lost) choose(null);
  });
  if (!tenants.data?.length) return null;
  const label = current
    ? (tenants.data.find((tenant) => tenant.id === current)?.name ?? current)
    : t('tenant.all');
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className={cx('chip-btn', 'tenant-btn', current && 'is-on')} aria-label={t('tenant.choose', { tenant: label })}>
        <Building2 aria-hidden size={14} />
        <span className="tenant-btn__name">{label}</span>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <MenuContent>
          <DropdownMenu.Label className="menu__label">{t('tenant.title')}</DropdownMenu.Label>
          <DropdownMenu.RadioGroup value={current ?? ''} onValueChange={(value) => choose(value || null)}>
            {[{ id: '', name: t('tenant.all') }, ...tenants.data].map((tenant) => (
              <DropdownMenu.RadioItem key={tenant.id} value={tenant.id} className="menu__item">
                <span className="menu__check">
                  <DropdownMenu.ItemIndicator>
                    <Check aria-hidden size={16} />
                  </DropdownMenu.ItemIndicator>
                </span>
                {tenant.name ?? tenant.id}
              </DropdownMenu.RadioItem>
            ))}
          </DropdownMenu.RadioGroup>
        </MenuContent>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
