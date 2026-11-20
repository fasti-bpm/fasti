/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useQuery } from '@tanstack/react-query';
import { fullName, getUserProfile } from '../api/engine';
import { useI18n } from '../i18n';

/** The name of a user, or the id while it loads or if the user may not be read. */
export function useUserName(userId: string | null | undefined): string {
  const { locale } = useI18n();
  const profile = useQuery({
    queryKey: ['user-name', userId],
    queryFn: () => getUserProfile(userId ?? ''),
    enabled: !!userId,
    staleTime: 5 * 60_000,
  });
  if (!userId) {
    return '';
  }
  return profile.data ? fullName(profile.data, locale) : userId;
}
