/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// What the pages of cases (CMMN) share: names of states, types and actions.
import { Ban, CircleCheck, CircleDashed, CircleDot, CirclePause, Lock, OctagonX, Play, RotateCcw } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { CaseInstanceState, ExecutionAction, ExecutionState, HistoricActivityState } from '../api/cases';
import { useI18n, type MessageKey } from '../i18n';
import { cx } from '../ui';
import '../cmmn/cmmn.css';

export type AnyCaseState = CaseInstanceState | ExecutionState | HistoricActivityState;

const STATE_LABELS: Record<AnyCaseState, MessageKey> = {
  active: 'cmmn.state.active',
  enabled: 'cmmn.state.enabled',
  disabled: 'cmmn.state.disabled',
  available: 'cmmn.state.available',
  completed: 'cmmn.state.completed',
  terminated: 'cmmn.state.terminated',
  closed: 'cmmn.state.closed',
};

const STATE_LOOKS: Record<AnyCaseState, { tone: string; icon: LucideIcon }> = {
  active: { tone: 'badge--info', icon: CircleDot },
  enabled: { tone: 'badge--info', icon: Play },
  disabled: { tone: 'badge--muted', icon: Ban },
  available: { tone: 'badge--muted', icon: CircleDashed },
  completed: { tone: 'badge--success', icon: CircleCheck },
  terminated: { tone: 'badge--warning', icon: OctagonX },
  closed: { tone: 'badge--muted', icon: Lock },
};

export function caseStateLabel(state: AnyCaseState): MessageKey {
  return STATE_LABELS[state];
}

/** A state is never shown by colour alone: every badge has an icon and a text. */
export function CaseStateBadge({ state }: { state: AnyCaseState }) {
  const { t } = useI18n();
  const look = STATE_LOOKS[state];
  const Icon = look.icon;
  return (
    <span className={cx('badge', look.tone)}>
      <Icon aria-hidden size={12} />
      {t(STATE_LABELS[state])}
    </span>
  );
}

/** The class of a plan item on the diagram for its state. */
export function stateMarker(state: AnyCaseState): string {
  switch (state) {
    case 'completed':
    case 'closed':
      return 'is-done';
    default:
      return `is-${state}`;
  }
}

const TYPE_LABELS: Record<string, MessageKey> = {
  casePlanModel: 'cmmn.type.casePlanModel',
  stage: 'cmmn.type.stage',
  humanTask: 'cmmn.type.humanTask',
  processTask: 'cmmn.type.processTask',
  caseTask: 'cmmn.type.caseTask',
  decisionTask: 'cmmn.type.decisionTask',
  task: 'cmmn.type.task',
  milestone: 'cmmn.type.milestone',
};

/** The type of a plan item in words; a type without a name is shown as the engine reports it. */
export function useTypeLabel(): (type: string | null | undefined) => string {
  const { t } = useI18n();
  return (type) => {
    const key = type ? TYPE_LABELS[type] : undefined;
    return key ? t(key) : (type ?? '—');
  };
}

export const ACTION_LABELS: Record<ExecutionAction, MessageKey> = {
  'manual-start': 'cmmn.action.manualStart',
  complete: 'cmmn.action.complete',
  disable: 'cmmn.action.disable',
  reenable: 'cmmn.action.reenable',
  terminate: 'cmmn.action.terminate',
};

export const ACTION_DONE: Record<ExecutionAction, MessageKey> = {
  'manual-start': 'cmmn.item.done.manualStart',
  complete: 'cmmn.item.done.complete',
  disable: 'cmmn.item.done.disable',
  reenable: 'cmmn.item.done.reenable',
  terminate: 'cmmn.item.done.terminate',
};

export const ACTION_ICONS: Record<ExecutionAction, LucideIcon> = {
  'manual-start': Play,
  complete: CircleCheck,
  disable: CirclePause,
  reenable: RotateCcw,
  terminate: OctagonX,
};

/** Actions that cannot be taken back are confirmed first. */
export const CONFIRMED_ACTIONS = new Set<ExecutionAction>(['complete', 'terminate']);

/** The states the diagram legend explains, in the order of the life of a plan item. */
export const LEGEND_STATES: AnyCaseState[] = ['available', 'enabled', 'active', 'disabled', 'completed', 'terminated'];

/** Explains the line patterns of the states on a case diagram. */
export function CaseLegend({ states = LEGEND_STATES }: { states?: AnyCaseState[] }) {
  const { t } = useI18n();
  return (
    <ul className="cmmn-legend" aria-label={t('cmmn.legend')}>
      {states.map((state) => (
        <li key={state} className="cmmn-legend__item">
          <svg className="cmmn-legend__swatch" viewBox="0 0 22 12" aria-hidden>
            <g className={cx('cmmn-shape', stateMarker(state))}>
              <rect className="cmmn-shape__border" x={1.5} y={1.5} width={19} height={9} rx={3} />
            </g>
          </svg>
          {t(STATE_LABELS[state])}
        </li>
      ))}
    </ul>
  );
}
