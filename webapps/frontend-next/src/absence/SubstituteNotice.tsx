/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useQuery } from '@tanstack/react-query';
import { UserRoundCheck } from 'lucide-react';
import { getSubstitutionMark } from '../api/absence';
import type { Task } from '../api/types';
import { useSession } from '../app/session';
import { useI18n } from '../i18n';
import { useAbsenceEnabled } from './AbsenceBanner';
import { useUserName } from './names';
import './absence.css';

/** On a task that went to a substitute: for whom it is held. */
export function SubstituteNotice({ task }: { task: Pick<Task, 'id' | 'assignee'> }) {
  const { t } = useI18n();
  const session = useSession();
  const enabled = useAbsenceEnabled();
  const mark = useQuery({
    queryKey: ['substitution-mark', task.id, task.assignee],
    queryFn: ({ signal }) => getSubstitutionMark(task.id, signal),
    enabled: enabled && !!task.assignee,
    staleTime: 60_000,
  });
  const absent = useUserName(mark.data);
  const assignee = useUserName(task.assignee);
  if (!mark.data || !task.assignee) {
    return null;
  }
  return (
    <p className="absence-task" role="note">
      <span className="badge badge--substitute">
        <UserRoundCheck aria-hidden size={13} />
        {t('absence.task.badge')}
      </span>
      <span>
        {task.assignee === session.userId
          ? t('absence.task.mine', { name: absent })
          : t('absence.task.other', { assignee, name: absent })}
      </span>
    </p>
  );
}

/** On a task card: the task is held for an absent user. */
export function SubstituteBadge({ absentUserId }: { absentUserId: string }) {
  const { t } = useI18n();
  const name = useUserName(absentUserId);
  return (
    <span className="badge badge--substitute" title={t('absence.card.title', { name })}>
      <UserRoundCheck aria-hidden size={13} />
      {t('absence.card', { name })}
    </span>
  );
}
