/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Download, ExternalLink, FileText, Link2, Paperclip, Trash2, Upload } from 'lucide-react';
import {
  addTaskAttachment,
  attachmentDataUrl,
  deleteTaskAttachment,
  getTaskAttachments,
  type TaskAttachment,
} from '../api/engine';
import { ConfirmDialog } from '../cockpit/common';
import { useI18n } from '../i18n';
import { parseEngineDate } from '../lib/dates';
import { Button, EmptyState, ErrorState, Field, IconButton, Loading, cx } from '../ui';
import { useToast } from '../ui/toast';

/** Only links to web pages are opened; anything else is shown as text. */
export function safeLink(url: string | null | undefined): string | null {
  if (!url) {
    return null;
  }
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.href : null;
  } catch {
    return null;
  }
}

type Mode = 'file' | 'url';

function AddAttachment({ taskId, onAdded }: { taskId: string; onAdded: () => void }) {
  const { t } = useI18n();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [mode, setMode] = useState<Mode>('file');
  const [file, setFile] = useState<File | null>(null);
  const [url, setUrl] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);

  const urlError = mode === 'url' && !safeLink(url.trim()) ? t('attachments.urlInvalid') : null;
  const fileError = mode === 'file' && !file ? t('attachments.fileRequired') : null;
  const effectiveName = name.trim() || (mode === 'file' ? (file?.name ?? '') : url.trim());

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    if (urlError || fileError) {
      return;
    }
    setBusy(true);
    try {
      await addTaskAttachment(
        taskId,
        mode === 'file' && file
          ? { name: effectiveName, description: description.trim(), file }
          : { name: effectiveName, description: description.trim(), url: url.trim() },
      );
      toast.show({ title: t('attachments.added', { name: effectiveName }) });
      setFile(null);
      setUrl('');
      setName('');
      setDescription('');
      setSubmitted(false);
      if (fileRef.current) {
        fileRef.current.value = '';
      }
      onAdded();
    } catch (error) {
      toast.error(error, t('attachments.addFailed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="attachment-form" onSubmit={(event) => void submit(event)} noValidate>
      <fieldset className="attachment-form__mode">
        <legend className="field__label">{t('attachments.add')}</legend>
        {(['file', 'url'] as Mode[]).map((value) => (
          <label key={value} className={cx('chip-btn', mode === value && 'is-active')}>
            <input
              type="radio"
              name="attachment-mode"
              className="sr-only"
              value={value}
              checked={mode === value}
              onChange={() => {
                setMode(value);
                setSubmitted(false);
              }}
            />
            {value === 'file' ? <Upload aria-hidden size={14} /> : <Link2 aria-hidden size={14} />}
            {value === 'file' ? t('attachments.mode.file') : t('attachments.mode.url')}
          </label>
        ))}
      </fieldset>
      {mode === 'file' ? (
        <Field label={t('attachments.file')} required error={submitted ? fileError : null}>
          {(props) => (
            <input
              {...props}
              ref={fileRef}
              className="input"
              type="file"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          )}
        </Field>
      ) : (
        <Field label={t('attachments.url')} required error={submitted ? urlError : null}>
          {(props) => (
            <input
              {...props}
              className="input"
              type="url"
              inputMode="url"
              placeholder="https://"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
            />
          )}
        </Field>
      )}
      <div className="attachment-form__row">
        <Field label={t('attachments.name')} hint={t('attachments.nameHint')}>
          {(props) => (
            <input
              {...props}
              className="input"
              maxLength={255}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          )}
        </Field>
        <Field label={t('attachments.description')}>
          {(props) => (
            <input
              {...props}
              className="input"
              maxLength={4000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          )}
        </Field>
      </div>
      <div className="comment-form__actions">
        <Button type="submit" size="sm" icon={Paperclip} busy={busy}>
          {t('attachments.attach')}
        </Button>
      </div>
    </form>
  );
}

/** The files and links attached to a task: listed, downloaded, added and deleted. */
export function AttachmentsTab({ taskId }: { taskId: string }) {
  const { t, relative, dateTime } = useI18n();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [deleting, setDeleting] = useState<TaskAttachment | null>(null);
  const [busy, setBusy] = useState(false);
  const attachments = useQuery({
    queryKey: ['task-attachments', taskId],
    queryFn: () => getTaskAttachments(taskId),
    refetchOnMount: 'always',
  });
  const reload = () => void queryClient.invalidateQueries({ queryKey: ['task-attachments', taskId] });

  const remove = async () => {
    if (!deleting) {
      return;
    }
    setBusy(true);
    try {
      await deleteTaskAttachment(taskId, deleting.id);
      toast.show({ title: t('attachments.deleted', { name: deleting.name ?? deleting.id }) });
      setDeleting(null);
    } catch (error) {
      toast.error(error, t('attachments.deleteFailed'));
    } finally {
      setBusy(false);
      reload();
    }
  };

  return (
    <div className="attachments">
      <AddAttachment taskId={taskId} onAdded={reload} />
      {attachments.isPending ? (
        <Loading label={t('attachments.loading')} />
      ) : attachments.isError ? (
        <ErrorState error={attachments.error} onRetry={() => void attachments.refetch()} />
      ) : attachments.data.length === 0 ? (
        <EmptyState icon={Paperclip} title={t('attachments.empty')} />
      ) : (
        <ul className="attachment-list" aria-label={t('task.tab.attachments')}>
          {attachments.data.map((attachment) => {
            const name = attachment.name || attachment.id;
            const isLink = !!attachment.url;
            const link = safeLink(attachment.url);
            const created = parseEngineDate(attachment.createTime);
            return (
              <li key={attachment.id} className="attachment">
                <span className="attachment__icon" aria-hidden>
                  {isLink ? <Link2 size={16} /> : <FileText size={16} />}
                </span>
                <div className="attachment__text">
                  <span className="attachment__name">{name}</span>
                  {attachment.description && (
                    <span className="attachment__description">{attachment.description}</span>
                  )}
                  <span className="attachment__meta">
                    {isLink ? attachment.url : attachment.type}
                    {created && (
                      <>
                        {' · '}
                        <time dateTime={created.toISOString()} title={dateTime(created)}>
                          {relative(created)}
                        </time>
                      </>
                    )}
                  </span>
                </div>
                <div className="attachment__actions">
                  {isLink ? (
                    link && (
                      <a
                        className="icon-btn"
                        href={link}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label={t('attachments.open', { name })}
                        title={t('attachments.open', { name })}
                      >
                        <ExternalLink aria-hidden size={18} />
                      </a>
                    )
                  ) : (
                    <a
                      className="icon-btn"
                      href={attachmentDataUrl(taskId, attachment.id)}
                      download={name}
                      aria-label={t('attachments.download', { name })}
                      title={t('attachments.download', { name })}
                    >
                      <Download aria-hidden size={18} />
                    </a>
                  )}
                  <IconButton
                    icon={Trash2}
                    label={t('attachments.delete', { name })}
                    onClick={() => setDeleting(attachment)}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t('attachments.deleteTitle')}
        text={t('attachments.deleteText', { name: deleting?.name ?? deleting?.id ?? '' })}
        confirmLabel={t('attachments.deleteConfirm')}
        danger
        busy={busy}
        onConfirm={() => void remove()}
      />
    </div>
  );
}
