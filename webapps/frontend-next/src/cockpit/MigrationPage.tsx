/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { CheckCheck, ShieldCheck, Wand2 } from 'lucide-react';
import { getActivityStatistics, getDefinition, getDefinitionVersions, type InstanceQuery } from '../api/cockpit';
import { getProcessXml } from '../api/engine';
import {
  countInstancesOf,
  executeMigration,
  executeMigrationAsync,
  generateMigrationPlan,
  validateMigrationPlan,
  type InstanceSelection,
  type MigrationInstruction,
  type MigrationPlan,
  type MigrationValidation,
} from '../api/operations';
import { useI18n } from '../i18n';
import { Button, EmptyState, ErrorState, Field, Loading, Notice } from '../ui';
import { COCKPIT, useCockpitAction } from './common';
import { Checkbox, ConfirmDialog } from '../ui/controls';
import { useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { definitionPath } from './DefinitionsPage';
import { flowNodes, type FlowNode } from './model';
import { withReceipt } from './receipts';

import { keyOfDefinitionId } from '../bpmn/diagrams';
const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));

/** Source activity → target activity, as the table edits it. */
export type Mapping = Record<string, { target: string; updateEventTrigger: boolean }>;

export function planToMapping(plan: Pick<MigrationPlan, 'instructions'>): Mapping {
  const mapping: Mapping = {};
  for (const instruction of plan.instructions) {
    const source = instruction.sourceActivityIds[0];
    const target = instruction.targetActivityIds[0];
    if (source && target) {
      mapping[source] = { target, updateEventTrigger: !!instruction.updateEventTrigger };
    }
  }
  return mapping;
}

export function mappingToInstructions(mapping: Mapping): MigrationInstruction[] {
  return Object.entries(mapping)
    .filter(([, entry]) => entry.target)
    .map(([source, entry]) => ({
      sourceActivityIds: [source],
      targetActivityIds: [entry.target],
      updateEventTrigger: entry.updateEventTrigger || undefined,
    }));
}

/** How two versions differ for a migration: what is mapped, what is left behind and what is new. */
export interface MigrationDiff {
  mapped: FlowNode[];
  unmapped: FlowNode[];
  /** not mapped, and instances wait there now: they cannot be migrated */
  blocked: FlowNode[];
  /** in the new version only: nothing is mapped to them */
  added: FlowNode[];
}

/** Compares the versions under a mapping; exported for tests. */
export function migrationDiff(
  sourceNodes: FlowNode[],
  targetNodes: FlowNode[],
  mapping: Mapping,
  waiting: ReadonlyMap<string, number>,
): MigrationDiff {
  const targeted = new Set(Object.values(mapping).map((entry) => entry.target));
  const existing = new Set(sourceNodes.map((node) => node.id));
  const mapped = sourceNodes.filter((node) => mapping[node.id]?.target);
  const unmapped = sourceNodes.filter((node) => !mapping[node.id]?.target);
  return {
    mapped,
    unmapped,
    blocked: unmapped.filter((node) => (waiting.get(node.id) ?? 0) > 0),
    added: targetNodes.filter((node) => !targeted.has(node.id) && !existing.has(node.id)),
  };
}

/** The filter of the instance list carried in the address; `null` if there is none. */
export function parseFilter(value: string | null): Omit<InstanceQuery, 'processDefinitionId'> | null {
  if (!value) return null;
  try {
    const parsed: unknown = JSON.parse(value);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
    const { businessKey, activityIdIn, withIncident, variables, orQueries } = parsed as InstanceQuery;
    return {
      businessKey: typeof businessKey === 'string' && businessKey ? businessKey : undefined,
      activityIdIn: Array.isArray(activityIdIn) ? activityIdIn.filter((id) => typeof id === 'string') : undefined,
      withIncident: withIncident === true || undefined,
      variables: Array.isArray(variables) ? variables : undefined,
      orQueries: Array.isArray(orQueries) ? orQueries.filter((group) => Array.isArray(group?.variables)) : undefined,
    };
  } catch {
    return null;
  }
}

/**
 * Moves running instances from one version of a process to another. The
 * engine proposes a mapping of activities; it can be changed, checked and
 * then applied to chosen instances or to every instance of the version.
 */
export function MigrationPage() {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [params, setParams] = useUrlState();
  const sourceId = params.get('source') ?? '';
  const ids = useMemo(() => (params.get('ids') ?? '').split(',').filter(Boolean), [params]);
  const filterParam = params.get('q');
  const filter = useMemo(() => parseFilter(filterParam), [filterParam]);
  const instanceQuery = useMemo<InstanceQuery>(() => ({ ...filter, processDefinitionId: sourceId }), [filter, sourceId]);
  const [targetId, setTargetId] = useState('');
  const [mapping, setMapping] = useState<Mapping>({});
  const [updateTriggers, setUpdateTriggers] = useState(false);
  const [focus, setFocus] = useState<string | null>(null);
  // a result belongs to the plan that was sent; any change of the plan makes it obsolete
  const [checked, setValidation] = useState<{ plan: string; result: MigrationValidation } | null>(null);
  const [skipListeners, setSkipListeners] = useState(false);
  const [skipMappings, setSkipMappings] = useState(false);
  const [async, setAsync] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<string | null>(null);

  const source = useQuery({ queryKey: [COCKPIT, 'definition', sourceId], queryFn: () => getDefinition(sourceId), enabled: !!sourceId });
  const versions = useQuery({
    queryKey: [COCKPIT, 'definition-versions', source.data?.key, source.data?.tenantId ?? null],
    queryFn: () => getDefinitionVersions(source.data?.key ?? '', source.data?.tenantId ?? null),
    enabled: !!source.data,
  });
  const sourceXml = useQuery({ queryKey: ['process-xml', sourceId], queryFn: () => getProcessXml(sourceId), enabled: !!sourceId, staleTime: Infinity });
  const targetXml = useQuery({ queryKey: ['process-xml', targetId], queryFn: () => getProcessXml(targetId), enabled: !!targetId, staleTime: Infinity });
  const total = useQuery({
    queryKey: [COCKPIT, 'migration-count', instanceQuery],
    queryFn: () => countInstancesOf(instanceQuery),
    enabled: !!sourceId && ids.length === 0,
  });
  // where the instances of the old version wait now: those activities need a target
  const waitingNow = useQuery({
    queryKey: [COCKPIT, 'activity-statistics', sourceId],
    queryFn: ({ signal }) => getActivityStatistics(sourceId, signal),
    enabled: !!sourceId,
  });
  const plan = useQuery({
    queryKey: [COCKPIT, 'migration-plan', sourceId, targetId, updateTriggers],
    queryFn: () => generateMigrationPlan(sourceId, targetId, updateTriggers),
    enabled: !!sourceId && !!targetId,
    staleTime: Infinity,
  });

  // the newest other version is the usual target
  useEffect(() => {
    if (!targetId && versions.data) {
      const other = versions.data.find((version) => version.id !== sourceId);
      if (other) setTargetId(other.id);
    }
  }, [versions.data, targetId, sourceId]);
  useEffect(() => {
    if (plan.data) {
      setMapping(planToMapping(plan.data));
      setValidation(null);
    }
  }, [plan.data]);

  const sourceNodes = useMemo(() => flowNodes(sourceXml.data), [sourceXml.data]);
  const targetNodes = useMemo(() => flowNodes(targetXml.data), [targetXml.data]);
  const instructions = mappingToInstructions(mapping);
  const migrationPlan: MigrationPlan = { sourceProcessDefinitionId: sourceId, targetProcessDefinitionId: targetId, instructions };
  const selection: InstanceSelection =
    ids.length > 0
      ? { kind: 'ids', ids }
      : { kind: 'query', query: instanceQuery, count: total.data ?? 0 };
  const count = ids.length > 0 ? ids.length : (total.data ?? 0);
  const validation = checked?.plan === JSON.stringify(migrationPlan) ? checked.result : null;
  const failures = (validation?.instructionReports ?? []).flatMap((report) =>
    report.failures.map((failure) => `${report.instruction.sourceActivityIds.join(', ')}: ${failure}`),
  );
  const waiting = useMemo(
    () => new Map((waitingNow.data ?? []).map((entry) => [entry.id, entry.instances])),
    [waitingNow.data],
  );
  const diff = migrationDiff(sourceNodes, targetNodes, mapping, waiting);
  const blockedIds = new Set(diff.blocked.map((node) => node.id));
  const sourceMarkers = sourceNodes.map((node) => ({
    activityId: node.id,
    marker: mapping[node.id]?.target ? 'is-mapped' : blockedIds.has(node.id) ? 'is-blocked' : 'is-unmapped',
  }));
  const targetMarkers = [
    ...Object.values(mapping).map((entry) => ({ activityId: entry.target, marker: 'is-mapped' })),
    ...diff.added.map((node) => ({ activityId: node.id, marker: 'is-added' })),
  ];
  const sourceBadges = (waitingNow.data ?? [])
    .filter((entry) => entry.instances > 0)
    .map((entry) => ({ activityId: entry.id, instances: entry.instances, incidents: 0 }));
  const focusTarget = focus ? mapping[focus]?.target : undefined;

  // a mapping belongs to one pair of versions; the plan of the new target replaces it
  const chooseTarget = (id: string) => {
    setTargetId(id);
    setMapping({});
    setValidation(null);
  };
  const planReady = !!plan.data && !plan.isFetching && !plan.isError;

  const setTarget = (activityId: string, target: string) => {
    setValidation(null);
    setMapping((current) => {
      const next = Object.fromEntries(Object.entries(current).filter(([source]) => source !== activityId));
      if (target) next[activityId] = { target, updateEventTrigger: current[activityId]?.updateEventTrigger ?? false };
      return next;
    });
  };

  if (!sourceId) {
    return (
      <div className="page">
        <PageHeader title={t('ops.migrate.title')} back={{ to: '/cockpit/processes', label: t('cockpit.definitions.title') }} />
        <Card>
          <EmptyState title={t('ops.migrate.noSource')}>{t('ops.migrate.noSourceHint')}</EmptyState>
        </Card>
      </div>
    );
  }
  if (source.isError) {
    return <ErrorState error={source.error} onRetry={() => void source.refetch()} />;
  }

  return (
    <div className="page">
      <PageHeader
        title={t('ops.migrate.title')}
        back={{ to: definitionPath(sourceId), label: t('cockpit.instance.backToDefinition') }}
        subtitle={
          <span>
            {ids.length > 0
              ? t('ops.migrate.selected', { count: ids.length })
              : filter
                ? t('ops.migrate.matching', { count: count })
                : t('ops.migrate.allOfVersion', { count: count })}
          </span>
        }
      />
      {done && (
        <Notice title={t('ops.migrate.doneTitle')}>
          {done}{' '}
          <Link className="link" to={async ? '/cockpit/batches' : definitionPath(targetId)}>
            {async ? t('ops.bulk.batches') : t('ops.migrate.openTarget')}
          </Link>
        </Notice>
      )}
      <Card title={t('ops.migrate.versions')}>
        <div className="ops-form__add">
          <Field label={t('ops.migrate.source')}>
            {(props) => (
              <select
                {...props}
                className="input"
                value={sourceId}
                onChange={(event) => {
                  setParams({ source: event.target.value, ids: null, q: null });
                  setTargetId('');
                  setMapping({});
                  setValidation(null);
                }}
              >
                {(versions.data ?? []).map((version) => (
                  <option key={version.id} value={version.id}>
                    {`${version.name ?? version.key} · ${t('process.version', { version: version.version })}`}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label={t('ops.migrate.target')}>
            {(props) => (
              <select {...props} className="input" value={targetId} onChange={(event) => chooseTarget(event.target.value)}>
                <option value="">{t('ops.migrate.chooseTarget')}</option>
                {(versions.data ?? [])
                  .filter((version) => version.id !== sourceId)
                  .map((version) => (
                    <option key={version.id} value={version.id}>
                      {`${version.name ?? version.key} · ${t('process.version', { version: version.version })}`}
                    </option>
                  ))}
              </select>
            )}
          </Field>
          <Checkbox label={t('ops.migrate.updateTriggers')} hint={t('ops.migrate.updateTriggersHint')} checked={updateTriggers} onChange={setUpdateTriggers} />
          <Button icon={Wand2} disabled={!targetId} busy={plan.isFetching} onClick={() => void plan.refetch()}>
            {t('ops.migrate.generate')}
          </Button>
        </div>
        {versions.data && versions.data.length < 2 && <Notice tone="warning">{t('ops.migrate.oneVersion')}</Notice>}
        {plan.isError && <ErrorState error={plan.error} />}
      </Card>

      {targetId && (
        <>
          <div className="migration-diagrams">
            <Card flush className="model" title={t('ops.migrate.source')}>
              {sourceXml.isError ? (
                <ErrorState error={sourceXml.error} onRetry={() => void sourceXml.refetch()} />
              ) : sourceXml.data ? (
                <Suspense fallback={<Loading label={t('diagram.loading')} />}>
                  <BpmnDiagram
                    xml={sourceXml.data}
                    processKey={source.data?.key}
                    markers={sourceMarkers}
                    badges={sourceBadges}
                    selected={focus ? [focus] : []}
                    onSelect={setFocus}
                    label={t('ops.migrate.sourceDiagram')}
                  />
                </Suspense>
              ) : (
                <Loading label={t('diagram.loading')} />
              )}
            </Card>
            <Card flush className="model" title={t('ops.migrate.target')}>
              {targetXml.isError ? (
                <ErrorState error={targetXml.error} onRetry={() => void targetXml.refetch()} />
              ) : targetXml.data ? (
                <Suspense fallback={<Loading label={t('diagram.loading')} />}>
                  <BpmnDiagram
                    xml={targetXml.data}
                    processKey={keyOfDefinitionId(targetId) ?? source.data?.key}
                    markers={targetMarkers}
                    selected={focusTarget ? [focusTarget] : []}
                    onSelect={(target) => focus && target && setTarget(focus, target)}
                    label={t('ops.migrate.targetDiagram')}
                  />
                </Suspense>
              ) : (
                <Loading label={t('diagram.loading')} />
              )}
            </Card>
          </div>
          <p className="field__hint">{t('ops.migrate.diagramHint')}</p>
          {targetXml.data && sourceXml.data && (
            <section className="migration-diff" aria-label={t('ops.migrate.diff')}>
              <ul className="migration-diff__legend">
                <li>
                  <span className="migration-diff__swatch migration-diff__swatch--mapped" aria-hidden />
                  {t('ops.migrate.diff.mapped', { count: diff.mapped.length })}
                </li>
                <li>
                  <span className="migration-diff__swatch migration-diff__swatch--unmapped" aria-hidden />
                  {t('ops.migrate.diff.unmapped', { count: diff.unmapped.length })}
                </li>
                <li>
                  <span className="migration-diff__swatch migration-diff__swatch--blocked" aria-hidden />
                  {t('ops.migrate.diff.blocked', { count: diff.blocked.length })}
                </li>
                <li>
                  <span className="migration-diff__swatch migration-diff__swatch--added" aria-hidden />
                  {t('ops.migrate.diff.added', { count: diff.added.length })}
                </li>
              </ul>
              {waitingNow.isError && <ErrorState error={waitingNow.error} onRetry={() => void waitingNow.refetch()} />}
              {diff.blocked.length > 0 && (
                <Notice tone="warning" title={t('ops.migrate.diff.blockedTitle')}>
                  <ul className="migration-diff__list">
                    {diff.blocked.map((node) => (
                      <li key={node.id}>
                        <button type="button" className="link" onClick={() => setFocus(node.id)}>
                          {node.name}
                        </button>{' '}
                        <span className="muted">{t('ops.migrate.diff.waiting', { count: waiting.get(node.id) ?? 0 })}</span>
                      </li>
                    ))}
                  </ul>
                </Notice>
              )}
            </section>
          )}

          <Card flush title={t('ops.migrate.mapping', { mapped: instructions.length, total: sourceNodes.length })}>
            <div className="table-wrap">
              <table className="table table--cards" aria-label={t('ops.migrate.mappingTable')}>
                <thead>
                  <tr>
                    <th scope="col">{t('ops.migrate.sourceActivity')}</th>
                    <th scope="col">{t('ops.migrate.targetActivity')}</th>
                    <th scope="col">{t('ops.migrate.updateTrigger')}</th>
                  </tr>
                </thead>
                <tbody>
                  {sourceNodes.map((node) => (
                    <tr key={node.id} className={focus === node.id ? 'is-selected' : undefined}>
                      <td data-label={t('ops.migrate.sourceActivity')}>
                        <button type="button" className="link" onClick={() => setFocus(node.id)}>
                          {node.name}
                        </button>{' '}
                        <span className="muted mono">{node.type}</span>
                      </td>
                      <td data-label={t('ops.migrate.targetActivity')}>
                        <select
                          className="input"
                          aria-label={t('ops.migrate.targetFor', { name: node.name })}
                          value={mapping[node.id]?.target ?? ''}
                          onChange={(event) => setTarget(node.id, event.target.value)}
                        >
                          <option value="">{t('ops.migrate.unmapped')}</option>
                          {targetNodes.map((candidate) => (
                            <option key={candidate.id} value={candidate.id}>
                              {candidate.name === candidate.id ? candidate.id : `${candidate.name} (${candidate.id})`}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td data-label={t('ops.migrate.updateTrigger')}>
                        <input
                          type="checkbox"
                          aria-label={t('ops.migrate.updateTriggerFor', { name: node.name })}
                          disabled={!mapping[node.id]}
                          checked={mapping[node.id]?.updateEventTrigger ?? false}
                          onChange={(event) =>
                            setMapping((current) => {
                              const entry = current[node.id];
                              return entry ? { ...current, [node.id]: { ...entry, updateEventTrigger: event.target.checked } } : current;
                            })
                          }
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card title={t('ops.migrate.execute')}>
            <div className="ops-form__options">
              <Checkbox label={t('cockpit.delete.skipListeners')} hint={t('ops.skipListenersHint')} checked={skipListeners} onChange={setSkipListeners} />
              <Checkbox label={t('cockpit.delete.skipMappings')} hint={t('ops.skipMappingsHint')} checked={skipMappings} onChange={setSkipMappings} />
              <Checkbox label={t('ops.async')} hint={t('ops.asyncHint')} checked={async} onChange={setAsync} />
            </div>
            {validation &&
              (failures.length === 0 ? (
                <Notice title={t('ops.migrate.valid')}>{t('ops.migrate.validHint')}</Notice>
              ) : (
                <Notice tone="danger" title={t('ops.migrate.invalid', { count: failures.length })}>
                  <ul>
                    {failures.map((failure) => (
                      <li key={failure}>{failure}</li>
                    ))}
                  </ul>
                </Notice>
              ))}
            <div className="confirm__actions">
              <Button
                icon={ShieldCheck}
                disabled={instructions.length === 0 || !planReady}
                onClick={() => {
                  const plan = JSON.stringify(migrationPlan);
                  void validateMigrationPlan(migrationPlan)
                    .then((result) => setValidation({ plan, result }))
                    .catch(() =>
                      setValidation({
                        plan,
                        result: { instructionReports: [{ instruction: { sourceActivityIds: ['—'], targetActivityIds: [] }, failures: [t('ops.migrate.validateFailed')] }] },
                      }),
                    );
                }}
              >
                {t('ops.migrate.validate')}
              </Button>
              <Button variant="primary" icon={CheckCheck} disabled={instructions.length === 0 || count === 0 || !planReady || (!!validation && failures.length > 0)} onClick={() => setConfirming(true)}>
                {t('ops.review')}
              </Button>
            </div>
          </Card>
        </>
      )}
      {confirming && (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setConfirming(false)}
          title={t('ops.migrate.confirmTitle', { count: count })}
          text={t('ops.migrate.confirmText', {
            count: count,
            mapped: instructions.length,
            mode: async ? t('ops.async') : t('ops.sync'),
          })}
          confirmLabel={t('ops.migrate.submit', { count: count })}
          busy={busy}
          onConfirm={() => {
            setBusy(true);
            const options = { skipCustomListeners: skipListeners, skipIoMappings: skipMappings };
            void run(
              withReceipt(
                () => (async ? executeMigrationAsync(migrationPlan, selection, options) : executeMigration(migrationPlan, selection, options)),
                t('ops.migrate.confirmTitle', { count }),
              ),
              { success: async ? t('ops.batchStarted') : t('ops.migrate.done'), failure: t('ops.migrate.failed') },
            ).then((ok) => {
              setBusy(false);
              setConfirming(false);
              if (ok) setDone(async ? t('ops.batchStarted') : t('ops.migrate.done'));
            });
          }}
        />
      )}
    </div>
  );
}
