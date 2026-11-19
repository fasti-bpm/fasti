/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, CircleSlash, FolderOpen, MessageSquare, PlayCircle } from 'lucide-react';
import { addInstanceComment } from '../api/engine';
import { useI18n, type MessageKey } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { Button, EmptyState, ErrorState, Loading, Notice, cx } from '../ui';
import { useToast } from '../ui/toast';
import { caseEntries, getCaseData, type CaseEntryKind } from './caseData';
import './caseTimeline.css';

const WHAT: Record<CaseEntryKind, MessageKey> = {
  comment: 'case.commented',
  created: 'case.created',
  completed: 'case.completed',
  canceled: 'case.canceled',
};

const ICON = { comment: MessageSquare, created: PlayCircle, completed: CheckCircle2, canceled: CircleSlash };

export const caseQueryKey = (processInstanceId: string) => ['case', processInstanceId] as const;

/**
 * The story of a case: comments and steps of every task of the instance and
 * of the instances of its call chain, newest first, with who and when.
 * Comments on the whole case can be added while the instance runs.
 */
export function CaseTimeline({
  processInstanceId,
  currentTaskId,
  canComment = true,
}: {
  processInstanceId: string;
  currentTaskId?: string;
  canComment?: boolean;
}) {
  const { t, relative, dateTime } = useI18n();
  const id = useId();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [onlyComments, setOnlyComments] = useState(false);
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const data = useQuery({
    queryKey: caseQueryKey(processInstanceId),
    queryFn: ({ signal }) => getCaseData(processInstanceId, signal),
  });
  const entries = useMemo(
    () => (data.data ? caseEntries(data.data, processInstanceId) : []),
    [data.data, processInstanceId],
  );
  const shown = onlyComments ? entries.filter((entry) => entry.kind === 'comment') : entries;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const text = message.trim();
    if (!text) return;
    setSaving(true);
    try {
      await addInstanceComment(processInstanceId, text);
      setMessage('');
      toast.show({ title: t('case.commentAdded') });
    } catch (error) {
      toast.error(error, t('comment.failed'));
    } finally {
      setSaving(false);
      // a first load still running may have read the comments before this one was written;
      // invalidating would hand back its result, so it is cancelled and the case read again
      const queryKey = caseQueryKey(processInstanceId);
      await queryClient.cancelQueries({ queryKey });
      void queryClient.invalidateQueries({ queryKey });
    }
  };

  return (
    <div className="case">
      {canComment && (
        <form className="comment-form case__form" onSubmit={(event) => void submit(event)}>
          <label className="field__label" htmlFor={`${id}-comment`}>
            {t('case.commentLabel')}
          </label>
          <textarea
            id={`${id}-comment`}
            className="input"
            rows={2}
            maxLength={4000}
            value={message}
            aria-describedby={`${id}-hint`}
            onChange={(event) => setMessage(event.target.value)}
          />
          <p id={`${id}-hint`} className="field__hint">
            {t('case.commentPlaceholder')}
          </p>
          <div className="comment-form__actions">
            <Button type="submit" size="sm" icon={MessageSquare} busy={saving} disabled={!message.trim()}>
              {t('case.commentAdd')}
            </Button>
          </div>
        </form>
      )}
      {data.isPending ? (
        <Loading label={t('case.loading')} />
      ) : data.isError ? (
        <ErrorState error={data.error} onRetry={() => void data.refetch()} />
      ) : data.data === null ? (
        <EmptyState icon={FolderOpen} title={t('case.unavailable')} />
      ) : (
        <>
          <div className="case__toolbar">
            <label className="option option--inline">
              <input type="checkbox" checked={onlyComments} onChange={(event) => setOnlyComments(event.target.checked)} />
              <span className="option__label">{t('case.onlyComments')}</span>
            </label>
          </div>
          {data.data.truncated && <Notice tone="info">{t('case.truncated')}</Notice>}
          {shown.length === 0 ? (
            <EmptyState icon={MessageSquare} title={t(onlyComments ? 'case.noComments' : 'history.empty')} />
          ) : (
            <ol className="case__list" aria-label={t('case.title')}>
              {shown.map((entry) => {
                const date = parseEngineDate(entry.time);
                const Icon = ICON[entry.kind];
                const current = !!currentTaskId && entry.taskId === currentTaskId;
                return (
                  <li key={entry.id} className={cx('case__item', `case__item--${entry.kind}`, current && 'is-current')}>
                    <Icon className="case__icon" aria-hidden size={16} />
                    <div className="case__body">
                      <div className="case__head">
                        <span className="case__user">
                          {entry.userId ?? (entry.kind === 'created' ? '' : t('history.system'))}
                        </span>
                        <span className="case__what">{t(WHAT[entry.kind])}</span>
                        {entry.taskName ? (
                          <span className="case__step">
                            {entry.taskName}
                            {current && <span className="case__here">{t('case.thisTask')}</span>}
                          </span>
                        ) : (
                          entry.kind === 'comment' && <span className="case__step case__step--case">{t('case.wholeCase')}</span>
                        )}
                        {entry.processName && <span className="case__process muted">{entry.processName}</span>}
                        {date && (
                          <time className="case__time" dateTime={date.toISOString()} title={relative(date)}>
                            {dateTime(date)}
                          </time>
                        )}
                      </div>
                      {entry.message && (
                        <p className={entry.kind === 'comment' ? 'case__comment' : 'case__reason'}>{entry.message}</p>
                      )}
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </>
      )}
    </div>
  );
}
