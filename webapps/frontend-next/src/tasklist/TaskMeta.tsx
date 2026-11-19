/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Popover } from 'radix-ui';
import { Bell, CalendarClock, User, Users, X } from 'lucide-react';
import { getCandidateGroups } from '../api/engine';
import type { Task } from '../api/types';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { dueState, fromDateTimeInput, parseEngineDate, toDateTimeInput, toEngineDate } from '../lib/dates';
import { Button, Field, cx } from '../ui';
import type { TaskActions } from './actions';
import { IdentityInput } from './IdentityInput';

function MetaPopover({
  icon,
  label,
  value,
  tone,
  children,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  tone?: 'overdue' | 'soon' | 'muted';
  children: (close: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <Popover.Trigger className={cx('meta', tone && `meta--${tone}`)}>
        <span className="meta__label">
          {icon}
          {label}
        </span>
        <span className="meta__value">{value}</span>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content className="popover" align="start" sideOffset={6} aria-label={label}>
          {children(() => setOpen(false))}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}

function AssigneeEditor({ task, actions, close }: { task: Task; actions: TaskActions; close: () => void }) {
  const { t } = useI18n();
  const session = useSession();
  const [userId, setUserId] = useState(task.assignee ?? '');
  const next = userId.trim();
  // the assignee may hand the task to someone who resolves it and gives it back
  const canDelegate = task.assignee === session.userId && !!next && next !== task.assignee;
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (next && next !== task.assignee) {
      void actions.assign(task.id, next);
    }
    close();
  };
  return (
    <form className="popover__form" onSubmit={submit}>
      <Field label={t('task.assignee.userId')} hint={t('task.assignee.hint')}>
        {(props) => <IdentityInput {...props} kind="user" autoFocus value={userId} onChange={setUserId} />}
      </Field>
      <div className="popover__actions">
        {task.assignee && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              void actions.assign(task.id, null);
              close();
            }}
          >
            {t('task.assignee.remove')}
          </Button>
        )}
        {canDelegate && (
          <Button
            size="sm"
            onClick={() => {
              void actions.delegate(task.id, next);
              close();
            }}
          >
            {t('task.delegate')}
          </Button>
        )}
        <Button type="submit" variant="primary" size="sm" disabled={!next}>
          {t('action.save')}
        </Button>
      </div>
    </form>
  );
}

function DateEditor({
  task,
  property,
  actions,
  close,
}: {
  task: Task;
  property: 'due' | 'followUp';
  actions: TaskActions;
  close: () => void;
}) {
  const { t } = useI18n();
  const [value, setValue] = useState(toDateTimeInput(parseEngineDate(task[property])));
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const date = fromDateTimeInput(value);
    if (date) {
      void actions.setDate(task.id, property, toEngineDate(date));
      close();
    }
  };
  return (
    <form className="popover__form" onSubmit={submit}>
      <Field label={property === 'due' ? t('task.dueDate') : t('task.followUpDate')}>
        {(props) => (
          <input
            {...props}
            className="input"
            type="datetime-local"
            autoFocus
            value={value}
            onChange={(event) => setValue(event.target.value)}
          />
        )}
      </Field>
      <div className="popover__actions">
        {task[property] && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              void actions.setDate(task.id, property, null);
              close();
            }}
          >
            {t('action.clear')}
          </Button>
        )}
        <Button type="submit" variant="primary" size="sm" disabled={!fromDateTimeInput(value)}>
          {t('action.save')}
        </Button>
      </div>
    </form>
  );
}

function GroupsEditor({
  taskId,
  groups,
  actions,
}: {
  taskId: string;
  groups: string[];
  actions: TaskActions;
}) {
  const { t } = useI18n();
  const [groupId, setGroupId] = useState('');
  const duplicate = groups.includes(groupId.trim());
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const next = groupId.trim();
    if (next && !duplicate) {
      void actions.addGroup(taskId, next);
      setGroupId('');
    }
  };
  return (
    <form className="popover__form" onSubmit={submit}>
      {groups.length === 0 ? (
        <p className="popover__empty">{t('task.groups.none')}</p>
      ) : (
        <ul className="chips">
          {groups.map((group) => (
            <li key={group} className="chip">
              {group}
              <button
                type="button"
                className="chip__remove"
                aria-label={t('task.groups.remove', { group })}
                onClick={() => void actions.removeGroup(taskId, group)}
              >
                <X aria-hidden size={13} />
              </button>
            </li>
          ))}
        </ul>
      )}
      <Field label={t('task.groups.add')} error={duplicate ? t('validation.duplicate') : null}>
        {(props) => <IdentityInput {...props} kind="group" value={groupId} onChange={setGroupId} />}
      </Field>
      <div className="popover__actions">
        <Button type="submit" variant="primary" size="sm" disabled={!groupId.trim() || duplicate}>
          {t('action.add')}
        </Button>
      </div>
    </form>
  );
}

/** Assignee, dates and candidate groups of a task; each one can be changed in place. */
export function TaskMeta({ task, actions }: { task: Task; actions: TaskActions }) {
  const { t, relative, dateTime } = useI18n();
  const session = useSession();
  const groups = useQuery({
    queryKey: ['task-groups', task.id],
    queryFn: () => getCandidateGroups(task.id),
  });

  const due = parseEngineDate(task.due);
  const followUp = parseEngineDate(task.followUp);
  const dueTone = due ? dueState(due) : undefined;
  const assigneeLabel = task.assignee
    ? task.assignee === session.userId
      ? t('task.assignee.me', { user: session.displayName })
      : task.assignee
    : t('task.unassigned');
  const delegatedBy = task.delegationState === 'PENDING' && task.owner ? task.owner : null;

  return (
    <div className="meta-bar">
      <MetaPopover
        icon={<User aria-hidden size={14} />}
        label={t('task.assignee')}
        value={
          delegatedBy ? t('task.delegatedBy', { assignee: assigneeLabel, owner: delegatedBy }) : assigneeLabel
        }
        tone={task.assignee ? undefined : 'muted'}
      >
        {(close) => <AssigneeEditor task={task} actions={actions} close={close} />}
      </MetaPopover>
      <MetaPopover
        icon={<CalendarClock aria-hidden size={14} />}
        label={t('task.dueDate')}
        value={due ? `${relative(due)} · ${dateTime(due)}` : t('task.notSet')}
        tone={dueTone === 'later' ? undefined : (dueTone ?? 'muted')}
      >
        {(close) => <DateEditor task={task} property="due" actions={actions} close={close} />}
      </MetaPopover>
      <MetaPopover
        icon={<Bell aria-hidden size={14} />}
        label={t('task.followUpDate')}
        value={followUp ? `${relative(followUp)} · ${dateTime(followUp)}` : t('task.notSet')}
        tone={followUp ? undefined : 'muted'}
      >
        {(close) => <DateEditor task={task} property="followUp" actions={actions} close={close} />}
      </MetaPopover>
      <MetaPopover
        icon={<Users aria-hidden size={14} />}
        label={t('task.groups')}
        value={
          groups.data ? (groups.data.length ? groups.data.join(', ') : t('task.groups.none')) : '…'
        }
        tone={groups.data?.length ? undefined : 'muted'}
      >
        {() => <GroupsEditor taskId={task.id} groups={groups.data ?? []} actions={actions} />}
      </MetaPopover>
    </div>
  );
}
