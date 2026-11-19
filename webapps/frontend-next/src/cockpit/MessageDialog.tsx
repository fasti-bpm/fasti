/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Send } from 'lucide-react';
import { deliverMessage, getAwaitedMessages, type MessageReceiver } from '../api/messages';
import { useI18n } from '../i18n';
import { Button, Dialog, Field, Notice, describeError } from '../ui';
import { COCKPIT, Checkbox, Id } from './common';
import { instancePath } from './Incidents';
import { VariableRows, emptyRow, rowsToVariables, type VariableRow } from './VariableRows';

/**
 * Sends a message to waiting instances or message start events. Opened for
 * one instance, the instance is preset and its awaited messages are offered.
 */
export function MessageDialog({
  processInstanceId,
  businessKey,
  onClose,
}: {
  processInstanceId?: string;
  businessKey?: string | null;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const listId = useId();
  const [name, setName] = useState('');
  const [key, setKey] = useState(processInstanceId ? '' : (businessKey ?? ''));
  const [instance, setInstance] = useState(processInstanceId ?? '');
  const [tenant, setTenant] = useState('');
  const [noTenant, setNoTenant] = useState(false);
  const byInstance = instance.trim() !== '';
  const [all, setAll] = useState(false);
  const [keys, setKeys] = useState<VariableRow[]>([]);
  const [local, setLocal] = useState<VariableRow[]>([]);
  const [variables, setVariables] = useState<VariableRow[]>([emptyRow()]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const [receivers, setReceivers] = useState<MessageReceiver[] | null>(null);

  const awaited = useQuery({
    queryKey: [COCKPIT, 'awaited-messages', processInstanceId ?? null],
    queryFn: () => getAwaitedMessages(processInstanceId),
  });

  const k = rowsToVariables(keys);
  const l = rowsToVariables(local);
  const v = rowsToVariables(variables);
  const valid = name.trim() !== '' && k.ok && l.ok && v.ok;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !k.ok || !l.ok || !v.ok) return;
    setBusy(true);
    setFailure(null);
    deliverMessage({
      messageName: name,
      businessKey: key,
      processInstanceId: instance,
      tenantId: tenant,
      withoutTenantId: noTenant,
      all,
      correlationKeys: k.variables,
      localCorrelationKeys: l.variables,
      processVariables: v.variables,
    })
      .then((result) => {
        setReceivers(result);
        void queryClient.invalidateQueries({ queryKey: [COCKPIT] });
      })
      .catch((error: unknown) => setFailure(error))
      .finally(() => setBusy(false));
  };

  const problem = failure ? describeError(failure, t) : null;

  return (
    <Dialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('message.title')}
      description={processInstanceId ? t('message.textInstance') : t('message.text')}
      wide
    >
      {receivers ? (
        <div className="confirm">
          <Notice tone={receivers.length ? 'info' : 'warning'} title={t('message.result', { count: receivers.length })}>
            {receivers.length === 0 && t('message.noReceiver')}
          </Notice>
          {receivers.length > 0 && (
            <ul className="message-result" aria-label={t('message.receivers')}>
              {receivers.map((receiver, index) => (
                <li key={`${receiver.processInstanceId}-${index}`}>
                  <span className="badge">
                    {receiver.kind === 'started' ? t('message.started') : t('message.continued')}
                  </span>{' '}
                  <Link className="link" to={instancePath(receiver.processInstanceId)} onClick={onClose}>
                    <Id value={receiver.processInstanceId} />
                  </Link>
                  {receiver.businessKey && (
                    <span className="muted">
                      {' · '}
                      {t('process.businessKey')}: {receiver.businessKey}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
          <div className="confirm__actions">
            <Button onClick={() => setReceivers(null)}>{t('message.again')}</Button>
            <Button variant="primary" onClick={onClose}>
              {t('action.close')}
            </Button>
          </div>
        </div>
      ) : (
        <form className="confirm" onSubmit={submit} noValidate>
          <Field label={t('message.name')} required hint={t('message.nameHint')}>
            {(props) => (
              <input
                {...props}
                className="input"
                list={listId}
                autoComplete="off"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            )}
          </Field>
          <datalist id={listId}>
            {awaited.data?.map((message) => <option key={message.name} value={message.name} />)}
          </datalist>
          {awaited.data && awaited.data.length > 0 && (
            <div className="chips" role="group" aria-label={t('message.awaited')}>
              {awaited.data.slice(0, 12).map((message) => (
                <button key={message.name} type="button" className="chip-btn" onClick={() => setName(message.name)}>
                  {message.name}
                </button>
              ))}
            </div>
          )}
          <div className="form-grid">
            <Field label={t('message.instance')} hint={t('message.instanceHint')}>
              {(props) => (
                <input {...props} className="input" value={instance} onChange={(event) => setInstance(event.target.value)} />
              )}
            </Field>
            <Field label={t('process.businessKey')}>
              {(props) => (
                <input {...props} className="input" value={key} onChange={(event) => setKey(event.target.value)} />
              )}
            </Field>
            <Field label={t('message.tenant')} hint={byInstance ? t('message.tenantByInstance') : undefined}>
              {(props) => (
                <input
                  {...props}
                  className="input"
                  value={byInstance || noTenant ? '' : tenant}
                  disabled={byInstance || noTenant}
                  onChange={(event) => setTenant(event.target.value)}
                />
              )}
            </Field>
            <Checkbox label={t('message.withoutTenant')} checked={noTenant && !byInstance} disabled={byInstance} onChange={setNoTenant} />
          </div>
          <VariableRows rows={keys} onChange={setKeys} label={t('message.correlationKeys')} />
          <VariableRows rows={local} onChange={setLocal} label={t('message.localKeys')} />
          <VariableRows rows={variables} onChange={setVariables} label={t('message.variables')} />
          <Checkbox label={t('message.all')} hint={t('message.allHint')} checked={all} onChange={setAll} />
          {problem && (
            <Notice tone="danger" title={problem.title}>
              {problem.detail}
            </Notice>
          )}
          <div className="confirm__actions">
            <Button onClick={onClose}>{t('action.cancel')}</Button>
            <Button type="submit" variant="primary" icon={Send} busy={busy} disabled={!valid}>
              {t('message.send')}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
