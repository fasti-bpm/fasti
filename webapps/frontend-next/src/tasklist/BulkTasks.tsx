/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Hand, Undo2, UserPlus, X } from 'lucide-react';
import { assignTasks, type TaskAssignment } from '../api/operations';
import type { TaskListItem } from '../api/types';
import { useSession } from '../app/session';
import { ConfirmDialog, SampleList } from '../ui/controls';
import { useI18n } from '../i18n';
import { Button, Field, IconButton } from '../ui';
import { useToast } from '../ui/toast';
import { refreshTasks } from './actions';
import { IdentityInput } from './IdentityInput';

type Change = 'claim' | 'unclaim' | 'assign';

/** Which of the checked tasks a change applies to. */
export function tasksFor(change: Change, tasks: TaskListItem[]): TaskListItem[] {
  switch (change) {
    case 'claim':
      return tasks.filter((task) => !task.assignee);
    case 'unclaim':
      return tasks.filter((task) => !!task.assignee);
    default:
      return tasks;
  }
}

/**
 * The actions for the tasks checked in the list: claim the free ones, give
 * them back, or hand all of them to a user. Every change is confirmed with the
 * number of tasks it touches; tasks the user may not change are reported.
 */
export function BulkTaskBar({
  tasks,
  onClear,
  onKeep,
}: {
  tasks: TaskListItem[];
  onClear: () => void;
  /** after a partial failure: the tasks that failed stay checked for another try */
  onKeep?: (taskIds: string[]) => void;
}) {
  const { t } = useI18n();
  const session = useSession();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [change, setChange] = useState<Change | null>(null);
  const [userId, setUserId] = useState('');
  const [busy, setBusy] = useState(false);
  /** the tasks the last change could not be applied to, named for the user */
  const [failures, setFailures] = useState<string[]>([]);

  const affected = change ? tasksFor(change, tasks) : [];
  const claimable = tasksFor('claim', tasks).length;
  const assigned = tasksFor('unclaim', tasks).length;

  const run = async () => {
    if (!change || affected.length === 0) {
      return;
    }
    const target: TaskAssignment =
      change === 'claim'
        ? { kind: 'claim', userId: session.userId }
        : change === 'unclaim'
          ? { kind: 'unclaim' }
          : { kind: 'assign', userId: userId.trim() };
    setBusy(true);
    try {
      const failed = await assignTasks(
        affected.map((task) => task.id),
        target,
      );
      const done = affected.length - failed.length;
      setChange(null);
      if (failed.length === 0) {
        toast.show({ title: t(`ops.tasks.${change}.done`, { count: done }) });
        setUserId('');
        setFailures([]);
        onClear();
      } else {
        toast.show({ tone: 'error', title: t('ops.tasks.partial', { done, failed: failed.length }) });
        setFailures(affected.filter((task) => failed.includes(task.id)).map((task) => task.name ?? task.id));
        if (onKeep) onKeep(failed);
        else onClear();
      }
    } finally {
      setBusy(false);
      refreshTasks(queryClient);
    }
  };

  return (
    <div className="bulk-tasks" role="region" aria-label={t('ops.tasks.title')}>
      <span className="bulk-tasks__count" aria-live="polite">
        {t('ops.tasks.selected', { count: tasks.length })}
      </span>
      <div className="bulk-tasks__actions">
        <Button size="sm" icon={Hand} disabled={claimable === 0} onClick={() => setChange('claim')}>
          {t('ops.tasks.claim')}
        </Button>
        <Button size="sm" icon={Undo2} disabled={assigned === 0} onClick={() => setChange('unclaim')}>
          {t('ops.tasks.unclaim')}
        </Button>
        <Button size="sm" icon={UserPlus} onClick={() => setChange('assign')}>
          {t('ops.tasks.assign')}
        </Button>
        <IconButton
          icon={X}
          label={t('ops.tasks.clear')}
          onClick={() => {
            setFailures([]);
            onClear();
          }}
        />
      </div>
      {failures.length > 0 && (
        <div className="bulk-tasks__receipt" role="status">
          {t('ops.tasks.kept', { count: failures.length, names: failures.slice(0, 3).join(', ') })}
        </div>
      )}
      {change && (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setChange(null)}
          title={t(`ops.tasks.${change}.title`)}
          text={t(`ops.tasks.${change}.text`, { count: affected.length, skipped: tasks.length - affected.length })}
          confirmLabel={t(`ops.tasks.${change}`)}
          busy={busy}
          disabled={affected.length === 0 || (change === 'assign' && !userId.trim())}
          onConfirm={() => void run()}
        >
          <SampleList items={affected.map((task) => task.name ?? task.taskDefinitionKey ?? task.id)} total={affected.length} />
          {change === 'assign' && (
            <Field label={t('ops.tasks.assignee')}>
              {(props) => (
                <IdentityInput {...props} kind="user" autoFocus value={userId} onChange={setUserId} />
              )}
            </Field>
          )}
        </ConfirmDialog>
      )}
    </div>
  );
}
