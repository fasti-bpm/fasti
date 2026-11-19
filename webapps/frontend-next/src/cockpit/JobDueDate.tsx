/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { recalculateJobDueDate, setJobDueDate } from '../api/maintenance';
import { useI18n } from '../i18n';
import { fromDateTimeInput, parseEngineDate, toDateTimeInput, toEngineDate } from '../lib/dates';
import { Field } from '../ui';
import { Checkbox, ConfirmDialog, useCockpitAction } from './common';

type Mode = 'date' | 'now' | 'fromCreation' | 'fromNow';

/** Changes when a job is due: a chosen time, now, or computed again from its timer. */
export function JobDueDateDialog({
  job,
  onClose,
}: {
  job: { id: string; dueDate: string | null };
  onClose: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [mode, setMode] = useState<Mode>('date');
  const [value, setValue] = useState(() => toDateTimeInput(parseEngineDate(job.dueDate) ?? new Date()));
  const [cascade, setCascade] = useState(false);
  const [busy, setBusy] = useState(false);
  const date = mode === 'date' ? fromDateTimeInput(value) : null;
  const invalid = mode === 'date' && !date;
  const modes: { value: Mode; label: string }[] = [
    { value: 'date', label: t('jobDue.date') },
    { value: 'now', label: t('jobDue.now') },
    { value: 'fromCreation', label: t('jobDue.fromCreation') },
    { value: 'fromNow', label: t('jobDue.fromNow') },
  ];
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('jobDue.title')}
      text={t('jobDue.text', { id: job.id })}
      confirmLabel={t('action.save')}
      busy={busy}
      disabled={invalid}
      onConfirm={() => {
        setBusy(true);
        const call = () => {
          switch (mode) {
            case 'date':
              return setJobDueDate(job.id, date ? toEngineDate(date) : null, cascade);
            case 'now':
              return setJobDueDate(job.id, null, cascade);
            case 'fromCreation':
              return recalculateJobDueDate(job.id, true);
            case 'fromNow':
              return recalculateJobDueDate(job.id, false);
          }
        };
        void run(call, { success: t('jobDue.done'), failure: t('jobDue.failed') }).then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    >
      <fieldset className="prefs__group">
        <legend className="sr-only">{t('jobDue.title')}</legend>
        {modes.map((option) => (
          <label key={option.value} className="option">
            <input
              type="radio"
              name="job-due-mode"
              checked={mode === option.value}
              onChange={() => setMode(option.value)}
            />
            {option.label}
          </label>
        ))}
      </fieldset>
      {mode === 'date' && (
        <Field label={t('jobDue.when')} error={invalid ? t('validation.date') : null}>
          {(props) => (
            <input
              {...props}
              className="input"
              type="datetime-local"
              step={1}
              value={value}
              onChange={(event) => setValue(event.target.value)}
            />
          )}
        </Field>
      )}
      {(mode === 'date' || mode === 'now') && (
        <Checkbox label={t('jobDue.cascade')} hint={t('jobDue.cascadeHint')} checked={cascade} onChange={setCascade} />
      )}
    </ConfirmDialog>
  );
}
