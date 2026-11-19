/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { ExternalLink } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { getProcessDefinition } from '../api/engine';
import type { FormInfo } from '../api/types';
import { useI18n } from '../i18n';
import { ErrorState, Loading, Notice } from '../ui';
import { applicationResourcePath } from './resolve';
import type { FormRendererProps } from './types';

const APP = 'app:';

/**
 * The address of a form of another application, as the classic Tasklist
 * built it: the form key (`app:` resolved against the context path of the
 * process application) with `taskId` or `processDefinitionKey` and the
 * `callbackUrl` the application sends the user back to. Only http(s)
 * addresses are accepted; anything else, e.g. a `javascript:` key, is
 * rejected.
 */
export function externalFormUrl(
  info: FormInfo,
  params: { taskId: string } | { processDefinitionKey: string },
  callbackUrl: string,
  origin: string = window.location.origin,
): string | null {
  const key = info.key?.trim();
  if (!key) {
    return null;
  }
  let address: string | null = key;
  if (key.startsWith(APP)) {
    address = applicationResourcePath(info.contextPath, key.substring(APP.length));
  }
  if (!address) {
    return null;
  }
  let url: URL;
  try {
    url = new URL(address, origin);
  } catch {
    return null;
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    return null;
  }
  if ('taskId' in params) {
    url.searchParams.set('taskId', params.taskId);
  } else {
    url.searchParams.set('processDefinitionKey', params.processDefinitionKey);
  }
  url.searchParams.set('callbackUrl', callbackUrl);
  return url.href;
}

/** Where a form of another application sends the user back to. */
export function callbackUrl(location: Location = window.location): string {
  return `${location.origin}${location.pathname}#/tasklist`;
}

/**
 * A form of another application. Like in the classic Tasklist, it is
 * opened in a new tab; that application completes the task or starts the
 * process and sends the user back.
 */
export function ExternalForm({ target, form }: FormRendererProps) {
  const { t } = useI18n();
  const definition = useQuery({
    queryKey: ['process-definition', target.type === 'start' ? target.processDefinitionId : ''],
    queryFn: () =>
      getProcessDefinition(target.type === 'start' ? target.processDefinitionId : ''),
    enabled: target.type === 'start',
    staleTime: 60_000,
  });

  if (target.type === 'start' && definition.isPending) {
    return <Loading label={t('form.loading')} />;
  }
  if (target.type === 'start' && definition.isError) {
    return <ErrorState error={definition.error} onRetry={() => void definition.refetch()} />;
  }

  const params =
    target.type === 'task'
      ? { taskId: target.taskId }
      : { processDefinitionKey: definition.data?.key ?? '' };
  const href = externalFormUrl(form.info, params, callbackUrl());

  if (!href) {
    return <Notice tone="danger">{t('form.external.invalid')}</Notice>;
  }
  return (
    <Notice title={t('form.external.title')}>
      <p>{target.type === 'task' ? t('form.external.task') : t('form.external.start')}</p>
      <p>
        <a className="btn btn--primary btn--md" href={href} target="_blank" rel="noopener noreferrer">
          {t('form.external.open')}
          <ExternalLink aria-hidden size={14} />
        </a>
      </p>
    </Notice>
  );
}
