/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useQuery } from '@tanstack/react-query';
import { UsersRound } from 'lucide-react';
import { getFourEyesStatus, stepNames, type FourEyesStatus } from '../api/fourEyes';
import type { Task } from '../api/types';
import { useUserName } from '../absence/names';
import { useI18n } from '../i18n';
import { Notice } from '../ui';

/**
 * The four-eyes status of a task for the signed-in user. Read again when the assignee changes,
 * since the assignee counts as well. An assignment is shown before the server confirmed it, so a
 * status read meanwhile may still be the one of the former assignee: such a status is not used,
 * and the status is read again once the change is through (`refreshTasks`).
 */
export function useFourEyes(task: Pick<Task, 'id' | 'assignee' | 'processInstanceId'> | undefined) {
  const assignee = task?.assignee ?? null;
  return useQuery({
    queryKey: ['four-eyes', task?.id, assignee],
    queryFn: ({ signal }) => getFourEyesStatus(task?.id ?? '', signal),
    select: (status) => (status && (status.assignee ?? null) !== assignee ? null : status),
    enabled: !!task?.processInstanceId,
    staleTime: 30_000,
  });
}

function useStepList(status: FourEyesStatus, ids: string[]): string {
  const { locale } = useI18n();
  return new Intl.ListFormat(locale, { type: 'conjunction' }).format(stepNames(status, ids));
}

/**
 * On a task with a four-eyes rule: who may not complete it. When the signed-in user or the
 * assignee completed one of the earlier steps it checks, the engine refuses the completion, and
 * the notice says why and what to do.
 */
export function FourEyesNotice({ status }: { status: FourEyesStatus }) {
  const { t } = useI18n();
  const assigneeName = useUserName(status.assignee);
  const all = useStepList(status, status.earlierSteps.map((step) => step.id));
  const mine = useStepList(status, status.userSteps);
  const theirs = useStepList(status, status.assigneeSteps);
  if (!status.enabled || !status.guarded) {
    return null;
  }
  if (status.userExcluded) {
    return (
      <div className="four-eyes">
        <Notice tone="warning" title={t('fourEyes.title')}>
          {t('fourEyes.you', { steps: mine })}
        </Notice>
      </div>
    );
  }
  if (status.assigneeExcluded) {
    return (
      <div className="four-eyes">
        <Notice tone="warning" title={t('fourEyes.title')}>
          {t('fourEyes.assignee', { name: assigneeName, steps: theirs })}
        </Notice>
      </div>
    );
  }
  return (
    <p className="four-eyes four-eyes--info" role="note">
      <span className="badge badge--four-eyes">
        <UsersRound aria-hidden size={13} />
        {t('fourEyes.badge')}
      </span>
      <span>{t('fourEyes.info', { steps: all })}</span>
    </p>
  );
}
