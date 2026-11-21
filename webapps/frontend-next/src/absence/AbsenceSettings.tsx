/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarOff, Send } from 'lucide-react';
import {
  clearAbsence,
  countAssignedTasks,
  getAbsence,
  handOverTasks,
  setAbsence,
  type Absence,
} from '../api/absence';
import { useI18n } from '../i18n';
import { fromDateTimeInput, parseEngineDate, toDateTimeInput, toEngineDate } from '../lib/dates';
import { refreshTasks } from '../tasklist/actions';
import { IdentityInput } from '../tasklist/IdentityInput';
import { Button, ErrorState, Field, Notice, Skeleton, cx } from '../ui';
import { ConfirmDialog } from '../ui/controls';
import { useToast } from '../ui/toast';
import { useUserName } from './names';
import './absence.css';

export const NOTE_LIMIT = 255;

export const absenceKey = (userId: string) => ['absence', userId] as const;

export interface AbsenceDraft {
  from: string;
  until: string;
  substituteId: string;
  note: string;
}

export type DraftProblem = 'until' | 'order' | 'past' | 'substitute' | 'self' | 'note';

/** What is wrong with a draft, field by field; empty if it can be saved. */
export function draftProblems(draft: AbsenceDraft, userId: string, now: Date = new Date()): Partial<Record<keyof AbsenceDraft, DraftProblem>> {
  const problems: Partial<Record<keyof AbsenceDraft, DraftProblem>> = {};
  const from = fromDateTimeInput(draft.from) ?? now;
  const until = fromDateTimeInput(draft.until);
  if (!until) {
    problems.until = 'until';
  } else if (until.getTime() <= from.getTime()) {
    problems.until = 'order';
  } else if (until.getTime() <= now.getTime()) {
    problems.until = 'past';
  }
  const substitute = draft.substituteId.trim();
  if (!substitute) {
    problems.substituteId = 'substitute';
  } else if (substitute === userId) {
    problems.substituteId = 'self';
  }
  if (draft.note.trim().length > NOTE_LIMIT) {
    problems.note = 'note';
  }
  return problems;
}

function draftOf(absence: Absence | undefined): AbsenceDraft {
  const live = absence && (absence.status === 'active' || absence.status === 'upcoming');
  return {
    from: live && absence.status === 'upcoming' ? toDateTimeInput(parseEngineDate(absence.from)) : '',
    until: live ? toDateTimeInput(parseEngineDate(absence.until)) : '',
    substituteId: live ? (absence.substituteId ?? '') : '',
    note: live ? (absence.note ?? '') : '',
  };
}

/**
 * The absence of a user with their substitute: set, change or end it, and hand the open tasks
 * over right away. In the preferences for oneself (`self`), in the administration for others.
 */
export function AbsenceSettings({ userId, self }: { userId: string; self: boolean }) {
  const { t } = useI18n();
  const query = useQuery({
    queryKey: absenceKey(userId),
    queryFn: ({ signal }) => getAbsence(userId, signal),
  });
  const headingId = useId();

  return (
    <section className="absence" aria-labelledby={headingId}>
      <h2 className="absence__title" id={headingId}>
        {t('absence.title')}
      </h2>
      {query.isError ? (
        <ErrorState error={query.error} onRetry={() => void query.refetch()} />
      ) : !query.data ? (
        <Skeleton lines={3} />
      ) : !query.data.enabled ? (
        <Notice tone="info">{t('absence.unavailable')}</Notice>
      ) : (
        <AbsenceForm
          key={`${query.data.status}|${query.data.from}|${query.data.until}|${query.data.substituteId}|${query.data.note}`}
          absence={query.data}
          userId={userId}
          self={self}
        />
      )}
    </section>
  );
}

/** The time zone the date fields are in, with its offset now: `Asia/Ho_Chi_Minh, GMT+7`. */
export function browserTimeZone(now: Date = new Date()): string {
  const format = new Intl.DateTimeFormat('en', { timeZoneName: 'shortOffset' });
  const offset = format.formatToParts(now).find((part) => part.type === 'timeZoneName')?.value;
  const zone = format.resolvedOptions().timeZone;
  return offset && offset !== zone ? `${zone}, ${offset}` : zone;
}

function AbsenceStatusLine({ absence }: { absence: Absence }) {
  const { t, dateTime } = useI18n();
  const substitute = useUserName(absence.substituteId);
  const effective = useUserName(absence.effectiveSubstituteId);
  const from = parseEngineDate(absence.from);
  const until = parseEngineDate(absence.until);
  if (!until || !from) {
    return null;
  }
  if (absence.status === 'active') {
    const via = absence.effectiveSubstituteId && absence.effectiveSubstituteId !== absence.substituteId;
    return (
      <Notice tone="warning">
        {absence.effectiveSubstituteId
          ? via
            ? t('absence.status.activeVia', { until: dateTime(until), substitute, name: effective })
            : t('absence.status.active', { until: dateTime(until), name: substitute })
          : t('absence.status.activeNobody', { until: dateTime(until), substitute })}
      </Notice>
    );
  }
  if (absence.status === 'upcoming') {
    return (
      <Notice tone="info">
        {t('absence.status.upcoming', { from: dateTime(from), until: dateTime(until), name: substitute })}
      </Notice>
    );
  }
  if (absence.status === 'ended') {
    return <p className="field__hint">{t('absence.status.ended', { until: dateTime(until) })}</p>;
  }
  return null;
}

function AbsenceForm({ absence, userId, self }: { absence: Absence; userId: string; self: boolean }) {
  const { t } = useI18n();
  const toast = useToast();
  const queryClient = useQueryClient();
  const userName = useUserName(userId);
  const [draft, setDraft] = useState<AbsenceDraft>(() => draftOf(absence));
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState<'save' | 'clear' | 'handover' | null>(null);
  const [confirming, setConfirming] = useState(false);
  const live = absence.status === 'active' || absence.status === 'upcoming';
  const assigned = useQuery({
    queryKey: ['absence-assigned', userId],
    queryFn: ({ signal }) => countAssignedTasks(userId, signal),
    enabled: live,
  });
  const handoverTarget = useUserName(absence.effectiveSubstituteId ?? absence.substituteId);
  const problems = draftProblems(draft, userId);
  const message = (field: keyof AbsenceDraft) => {
    const problem = problems[field];
    return touched && problem ? t(`absence.validation.${problem}`) : null;
  };
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['absence'] });
    void queryClient.invalidateQueries({ queryKey: ['absence-assigned'] });
    void queryClient.invalidateQueries({ queryKey: ['absence-substituting'] });
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    if (Object.keys(problems).length > 0) return;
    setBusy('save');
    try {
      const from = fromDateTimeInput(draft.from);
      const until = fromDateTimeInput(draft.until) as Date;
      await setAbsence(userId, {
        from: from ? toEngineDate(from) : null,
        until: toEngineDate(until),
        substituteId: draft.substituteId.trim(),
        note: draft.note.trim() || null,
      });
      toast.show({ title: t('absence.saved') });
      refresh();
    } catch (error) {
      toast.error(error, t('absence.saveFailed'));
    } finally {
      setBusy(null);
    }
  };

  const clear = async () => {
    setBusy('clear');
    try {
      await clearAbsence(userId);
      toast.show({ title: t('absence.cleared') });
      refresh();
    } catch (error) {
      toast.error(error, t('absence.clearFailed'));
    } finally {
      setBusy(null);
    }
  };

  const handOver = async () => {
    setBusy('handover');
    try {
      const result = await handOverTasks(userId);
      setConfirming(false);
      toast.show({
        title: t('absence.handedOver', { count: result.handedOver, name: handoverTarget }),
        detail: result.remaining > 0 ? t('absence.handoverRemaining', { count: result.remaining }) : undefined,
      });
      refresh();
      // the tasks have another assignee now, and are no longer held for the absent user
      refreshTasks(queryClient);
    } catch (error) {
      toast.error(error, t('absence.handoverFailed'));
    } finally {
      setBusy(null);
    }
  };

  const count = assigned.data ?? 0;
  return (
    <form className="absence__form" noValidate onSubmit={(event) => void save(event)}>
      <p className="field__hint absence__hint">
        {self ? t('absence.hint') : t('absence.hintOther', { name: userName })}
      </p>
      <AbsenceStatusLine absence={absence} />
      <div className="absence__dates">
        <Field label={t('absence.from')} hint={t('absence.fromHint')}>
          {(props) => (
            <input
              {...props}
              className="input"
              type="datetime-local"
              value={draft.from}
              onChange={(event) => setDraft({ ...draft, from: event.target.value })}
            />
          )}
        </Field>
        <Field label={t('absence.until')} required error={message('until')}>
          {(props) => (
            <input
              {...props}
              className="input"
              type="datetime-local"
              required
              value={draft.until}
              onChange={(event) => setDraft({ ...draft, until: event.target.value })}
            />
          )}
        </Field>
      </div>
      <p className="field__hint absence__zone">{t('absence.timeZone', { zone: browserTimeZone() })}</p>
      <Field label={t('absence.substitute')} required hint={t('absence.substituteHint')} error={message('substituteId')}>
        {(props) => (
          <IdentityInput
            {...props}
            kind="user"
            required
            value={draft.substituteId}
            onChange={(substituteId) => setDraft({ ...draft, substituteId })}
          />
        )}
      </Field>
      <Field label={self ? t('absence.note') : t('absence.noteOther')} hint={t('absence.noteHint')} error={message('note')}>
        {(props) => (
          <textarea
            {...props}
            className="input"
            rows={2}
            maxLength={NOTE_LIMIT}
            value={draft.note}
            onChange={(event) => setDraft({ ...draft, note: event.target.value })}
          />
        )}
      </Field>
      <div className={cx('absence__actions', live && 'absence__actions--live')}>
        {live && (
          <Button variant="danger-ghost" icon={CalendarOff} busy={busy === 'clear'} disabled={!!busy} onClick={() => void clear()}>
            {t('absence.clear')}
          </Button>
        )}
        {live && count > 0 && (
          <Button icon={Send} disabled={!!busy} onClick={() => setConfirming(true)}>
            {self ? t('absence.handover', { count }) : t('absence.handoverOther', { count })}
          </Button>
        )}
        <Button type="submit" variant="primary" busy={busy === 'save'} disabled={!!busy}>
          {t('absence.save')}
        </Button>
      </div>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t('absence.handoverTitle')}
        text={t('absence.handoverText', { count, user: userName, name: handoverTarget })}
        confirmLabel={t('absence.handoverConfirm')}
        busy={busy === 'handover'}
        onConfirm={() => void handOver()}
      />
    </form>
  );
}
