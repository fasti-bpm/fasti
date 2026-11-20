/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { settleLimited } from '../lib/limit';
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CircleCheck, Copy, FileWarning, MessageSquarePlus, Pencil, RotateCcw, SearchX } from 'lucide-react';
import {
  failureKind,
  getFailureDetails,
  setIncidentAnnotation,
  setRetries,
  type FailureKind,
  type Incident,
  type Page,
} from '../api/cockpit';
import { useI18n, type Translate } from '../i18n';
import { Button, Dialog, EmptyState, ErrorState, Field, IconButton, Loading, Skeleton } from '../ui';
import { useToast } from '../ui/toast';
import { COCKPIT, useCockpitAction } from './common';
import { ConfirmDialog } from '../ui/controls';
import { Id, Time } from '../ui/format';
import { DataTable, type Column, type TableSorting } from '../ui/table';
import { IncidentRunbook } from './Runbook';

export function instancePath(id: string): string {
  return `/cockpit/processes/instance/${encodeURIComponent(id)}`;
}

export function incidentTypeLabel(type: string, t: Translate): string {
  if (type === 'failedJob') return t('cockpit.incident.type.failedJob');
  if (type === 'failedExternalTask') return t('cockpit.incident.type.failedExternalTask');
  return type;
}

/** What can be retried for an incident: the job or external task that failed. */
export function failure(incident: Incident): { kind: FailureKind; id: string } | null {
  const kind = failureKind(incident.incidentType);
  const id = incident.rootCauseIncidentConfiguration ?? incident.configuration;
  return kind && id ? { kind, id } : null;
}

/**
 * The distinct jobs and external tasks that failed behind the incidents. An
 * incident of a call activity points to the failure in the called instance,
 * so retrying from the parent retries the root cause.
 */
/**
 * Where an error was thrown and where the instance stands since. They differ
 * when the transaction rolled back to the last wait state, e.g. an
 * asynchronous continuation before the activity that failed. One pair per
 * distinct place.
 */
export function rollbacks(incidents: Incident[]): { failed: string; stopped: string }[] {
  const pairs = new Map<string, { failed: string; stopped: string }>();
  for (const incident of incidents) {
    const { failedActivityId: failed, activityId: stopped } = incident;
    if (failed && stopped && failed !== stopped) pairs.set(`${failed} ${stopped}`, { failed, stopped });
  }
  return [...pairs.values()];
}

export function rootFailures(incidents: Incident[]): { kind: FailureKind; id: string }[] {
  const seen = new Map<string, { kind: FailureKind; id: string }>();
  for (const incident of incidents) {
    const target = failure(incident);
    if (target) seen.set(`${target.kind}:${target.id}`, target);
  }
  return [...seen.values()];
}

/** Resets the retries of many failures; resolves to the number that could not be reset. */
export async function retryAll(targets: { kind: FailureKind; id: string }[], retries: number): Promise<number> {
  const results = await settleLimited(targets, (target) => setRetries(target.kind, target.id, retries));
  return results.filter((result) => result.status === 'rejected').length;
}

/** Says that only part of the matching incidents is handled at once. */
function Capped({ read, total }: { read: number; total: number }) {
  const { t } = useI18n();
  return total > read ? <strong> {t('incidents.bulk.capped', { read, total })}</strong> : null;
}

/** Retries every failure behind a set of incidents, after saying how many there are. */
export function RetryAllDialog({
  scope,
  load,
  onClose,
}: {
  /** names the incidents, e.g. the instance they belong to */
  scope: string;
  /** the incidents whose failures are retried, with how many there are in all; read when the dialog opens */
  load: () => Promise<Page<Incident>>;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const toast = useToast();
  const queryClient = useQueryClient();
  // read fresh on every opening: a cached list could differ from what the confirmation says
  const targets = useQuery({
    queryKey: [COCKPIT, 'retry-all-targets', scope],
    queryFn: async () => {
      const page = await load();
      return { failures: rootFailures(page.items), read: page.items.length, total: page.count };
    },
    gcTime: 0,
    staleTime: 0,
  });
  const [busy, setBusy] = useState(false);
  const count = targets.data?.failures.length ?? 0;
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('incidents.retryAll.title')}
      text={
        targets.isPending ? (
          t('state.loading')
        ) : (
          <>
            {t('incidents.retryAll.text', { count })}
            <Capped read={targets.data?.read ?? 0} total={targets.data?.total ?? 0} />
          </>
        )
      }
      confirmLabel={t('incidents.retryAll.confirm', { count })}
      busy={busy}
      disabled={!count || targets.isFetching}
      onConfirm={() => {
        setBusy(true);
        void retryAll(targets.data?.failures ?? [], 1)
          .then((failed) => {
            if (failed) toast.show({ tone: 'error', title: t('incidents.retryAll.partly', { failed, count }) });
            else toast.show({ title: t('incidents.retryAll.done', { count }) });
            onClose();
          })
          .finally(() => {
            setBusy(false);
            void queryClient.invalidateQueries({ queryKey: [COCKPIT] });
          });
      }}
    />
  );
}

/** Writes one annotation on many incidents, after saying how many there are. */
export function AnnotateAllDialog({
  scope,
  load,
  onClose,
}: {
  scope: string;
  load: () => Promise<Page<Incident>>;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const toast = useToast();
  const queryClient = useQueryClient();
  const incidents = useQuery({ queryKey: [COCKPIT, 'annotate-all-targets', scope], queryFn: load, gcTime: 0, staleTime: 0 });
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const count = incidents.data?.items.length ?? 0;
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('incidents.annotateAll.title')}
      text={
        incidents.isPending ? (
          t('state.loading')
        ) : (
          <>
            {t('incidents.annotateAll.text', { count })}
            <Capped read={count} total={incidents.data?.count ?? 0} />
          </>
        )
      }
      confirmLabel={t('incidents.annotateAll.confirm', { count })}
      busy={busy}
      disabled={!count || incidents.isFetching}
      onConfirm={() => {
        setBusy(true);
        const annotation = text.trim() || null;
        void settleLimited(incidents.data?.items ?? [], (incident) => setIncidentAnnotation(incident.id, annotation))
          .then((results) => {
            const failed = results.filter((result) => result.status === 'rejected').length;
            toast.show({
              title: failed
                ? t('incidents.annotateAll.partly', { failed, count })
                : t('incidents.annotateAll.done', { count }),
            });
            onClose();
          })
          .finally(() => {
            setBusy(false);
            void queryClient.invalidateQueries({ queryKey: [COCKPIT] });
          });
      }}
    >
      <Field label={t('cockpit.annotation.label')} hint={t('incidents.annotateAll.hint')}>
        {(props) => (
          <textarea
            {...props}
            className="input"
            rows={3}
            maxLength={4000}
            value={text}
            onChange={(event) => setText(event.target.value)}
          />
        )}
      </Field>
    </ConfirmDialog>
  );
}

/**
 * The details of a failure: guidance on what to check, then the stack trace
 * or error details as plain text. With an incident the guidance also uses its
 * message and the runbook of its activity; an incident without a failed job or
 * external task has no trace, only the guidance.
 */
export function FailureDetailsDialog({
  target,
  incident,
  onClose,
}: {
  target: { kind: FailureKind; id: string } | null;
  incident?: Incident | null;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const toast = useToast();
  const open = !!target || !!incident;
  const details = useQuery({
    queryKey: [COCKPIT, 'failure-details', target?.kind, target?.id],
    queryFn: () => getFailureDetails(target?.kind ?? 'job', target?.id ?? ''),
    enabled: !!target,
  });
  const type = incident?.incidentType ?? (target?.kind === 'externalTask' ? 'failedExternalTask' : 'failedJob');
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()} title={t('cockpit.incident.details')} wide>
      {open && (
        <IncidentRunbook
          type={type}
          message={incident ? (incident.rootCauseIncidentMessage ?? incident.incidentMessage) : null}
          trace={details.data}
          processDefinitionId={incident?.processDefinitionId}
          activityId={incident ? (incident.failedActivityId ?? incident.activityId) : null}
        />
      )}
      {!target ? null : details.isPending ? (
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      ) : details.isError ? (
        <ErrorState error={details.error} onRetry={() => void details.refetch()} />
      ) : details.data.trim() === '' ? (
        <EmptyState icon={FileWarning} title={t('cockpit.incident.noDetails')} />
      ) : (
        <div className="trace">
          <div className="trace__tools">
            <h3 className="trace__title">{t('cockpit.incident.trace')}</h3>
            <Button
              size="sm"
              icon={Copy}
              onClick={() => {
                void navigator.clipboard
                  ?.writeText(details.data)
                  .then(() => toast.show({ title: t('cockpit.copied') }))
                  .catch(() => toast.show({ tone: 'error', title: t('cockpit.copyFailed') }));
              }}
            >
              {t('cockpit.copy')}
            </Button>
          </div>
          <pre className="code" tabIndex={0}>
            {details.data}
          </pre>
        </div>
      )}
    </Dialog>
  );
}

/** Asks for the number of retries of a failed job or external task. */
export function RetryDialog({
  target,
  what,
  onClose,
}: {
  target: { kind: FailureKind; id: string } | null;
  /** names the activity or job that is retried */
  what: string;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [retries, setRetriesValue] = useState('1');
  const [busy, setBusy] = useState(false);
  const count = Number(retries);
  const valid = Number.isInteger(count) && count >= 0 && retries.trim() !== '';
  return (
    <ConfirmDialog
      open={!!target}
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.retry.title')}
      text={t(target?.kind === 'externalTask' ? 'cockpit.retry.textExternal' : 'cockpit.retry.text', {
        what,
      })}
      confirmLabel={t('cockpit.retry.confirm')}
      busy={busy}
      disabled={!valid}
      onConfirm={() => {
        if (!target) return;
        setBusy(true);
        void run(() => setRetries(target.kind, target.id, count), {
          success: t('cockpit.retry.done'),
          failure: t('cockpit.retry.failed'),
        }).then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    >
      <Field
        label={t('cockpit.retry.retries')}
        hint={t('cockpit.retry.hint')}
        error={valid ? null : t('validation.integer')}
      >
        {(props) => (
          <input
            {...props}
            className="input"
            type="number"
            min={0}
            step={1}
            inputMode="numeric"
            value={retries}
            onChange={(event) => setRetriesValue(event.target.value)}
          />
        )}
      </Field>
    </ConfirmDialog>
  );
}

function AnnotationDialog({ incident, onClose }: { incident: Incident; onClose: () => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [text, setText] = useState(incident.annotation ?? '');
  const [busy, setBusy] = useState(false);
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.annotation.title')}
      text={t('cockpit.annotation.text')}
      confirmLabel={t('action.save')}
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void run(() => setIncidentAnnotation(incident.id, text.trim() || null), {
          success: t('cockpit.annotation.saved'),
          failure: t('cockpit.annotation.failed'),
        }).then((ok) => {
          setBusy(false);
          if (ok) onClose();
        });
      }}
    >
      <Field label={t('cockpit.annotation.label')}>
        {(props) => (
          <textarea
            {...props}
            className="input"
            rows={3}
            maxLength={4000}
            value={text}
            onChange={(event) => setText(event.target.value)}
          />
        )}
      </Field>
    </ConfirmDialog>
  );
}

/** Incidents as a table, with error details, retry and annotation. */
export function IncidentTable({
  page,
  error,
  onRetry,
  sorting,
  onSort,
  names,
  showInstance,
  onClearFilters,
  updating,
}: {
  /** the rows are of an earlier filter or page while the new ones load */
  updating?: boolean;
  /** set while filters narrow the list: an empty list then says so and offers to clear them */
  onClearFilters?: () => void;
  page: Page<Incident> | undefined;
  error: unknown;
  onRetry: () => void;
  sorting: TableSorting;
  onSort: (sorting: TableSorting) => void;
  /** names of the activities by id */
  names?: Map<string, string>;
  /** the incidents belong to different instances */
  showInstance?: boolean;
}) {
  const { t } = useI18n();
  const [details, setDetails] = useState<Incident | null>(null);
  const [retry, setRetry] = useState<{ kind: FailureKind; id: string; what: string } | null>(null);
  const [annotated, setAnnotated] = useState<Incident | null>(null);

  const columns = useMemo<Column<Incident>[]>(() => {
    const activityLabel = (incident: Incident): string => {
      const id = incident.failedActivityId ?? incident.activityId;
      return id ? (names?.get(id) ?? id) : '—';
    };
    const activityName = (incident: Incident) => {
      const id = incident.failedActivityId ?? incident.activityId;
      const name = (activity: string) => names?.get(activity) ?? activity;
      if (!id) return '—';
      // the error happened in one activity, the instance waits in another since the rollback
      return incident.activityId && incident.activityId !== id ? (
        <div className="incident">
          <span>{name(id)}</span>
          <span className="incident__stopped">{t('cockpit.incident.stoppedAt', { activity: name(incident.activityId) })}</span>
        </div>
      ) : (
        name(id)
      );
    };
    const list: Column<Incident>[] = [
      {
        id: 'message',
        header: t('cockpit.incident.message'),
        sortBy: 'incidentMessage',
        cell: (incident) => {
          const target = failure(incident);
          const message = incident.rootCauseIncidentMessage ?? incident.incidentMessage;
          const elsewhere =
            incident.rootCauseIncidentProcessInstanceId &&
            incident.rootCauseIncidentProcessInstanceId !== incident.processInstanceId
              ? incident.rootCauseIncidentProcessInstanceId
              : null;
          return (
            <div className="incident">
              <span className="incident__message">{message || t('cockpit.incident.noMessage')}</span>
              <span className="incident__links">
                <button type="button" className="link link--subtle" onClick={() => setDetails(incident)}>
                  {t(target ? 'cockpit.incident.details' : 'runbook.title')}
                </button>
                {elsewhere && (
                  <Link className="link link--subtle" to={instancePath(elsewhere)}>
                    {t('cockpit.incident.rootCause')}
                  </Link>
                )}
              </span>
            </div>
          );
        },
      },
      {
        id: 'type',
        header: t('cockpit.incident.type'),
        sortBy: 'incidentType',
        secondary: true,
        cell: (incident) => <span className="nowrap">{incidentTypeLabel(incident.incidentType, t)}</span>,
      },
      {
        id: 'activity',
        header: t('cockpit.activity'),
        sortBy: 'activityId',
        cell: activityName,
      },
    ];
    if (showInstance) {
      list.push({
        id: 'instance',
        header: t('cockpit.instance'),
        cell: (incident) =>
          incident.processInstanceId ? (
            <div className="incident">
              <Link className="cell-link" to={instancePath(incident.processInstanceId)}>
                <Id value={incident.processInstanceId} />
              </Link>
              {incident.processDefinitionId && (
                <Link
                  className="link link--subtle"
                  to={`/cockpit/processes/definition/${encodeURIComponent(incident.processDefinitionId)}`}
                >
                  {incident.processDefinitionId.split(':')[0]}
                </Link>
              )}
            </div>
          ) : (
            '—'
          ),
      });
    }
    list.push(
      {
        id: 'time',
        header: t('cockpit.incident.time'),
        sortBy: 'incidentTimestamp',
        secondary: true,
        cell: (incident) => <Time value={incident.incidentTimestamp} />,
      },
      {
        id: 'annotation',
        header: t('cockpit.annotation.label'),
        secondary: true,
        cell: (incident) => (
          <span className="annotation">
            {incident.annotation && <span className="annotation__text">{incident.annotation}</span>}
            <IconButton
              className="icon-btn--sm"
              icon={incident.annotation ? Pencil : MessageSquarePlus}
              label={t('a11y.rowAction', { action: t('cockpit.annotation.title'), row: incident.activityId ?? incident.id })}
              onClick={() => setAnnotated(incident)}
            />
          </span>
        ),
      },
      {
        id: 'actions',
        header: t('cockpit.actions'),
        align: 'end',
        cell: (incident) => {
          const target = failure(incident);
          return target ? (
            <Button
              size="sm"
              icon={RotateCcw}
              onClick={() => setRetry({ ...target, what: activityLabel(incident) })}
            >
              {t('cockpit.retry.action')}
            </Button>
          ) : null;
        },
      },
    );
    return list;
  }, [t, names, showInstance]);

  return (
    <>
      <DataTable
        updating={updating}
        label={t('cockpit.incidents.title')}
        columnsKey="incidents"
        columns={columns}
        rows={page?.items}
        rowKey={(incident) => incident.id}
        error={error}
        onRetry={onRetry}
        sorting={sorting}
        onSort={onSort}
        empty={
          onClearFilters ? (
            <EmptyState
              icon={SearchX}
              title={t('empty.noMatch')}
              action={
                <Button size="sm" onClick={onClearFilters}>
                  {t('empty.clearFilters')}
                </Button>
              }
            >
              {t('empty.noMatchHint')}
            </EmptyState>
          ) : (
            <EmptyState icon={CircleCheck} title={t('cockpit.incidents.empty')}>
              {t('cockpit.incidents.emptyHint')}
            </EmptyState>
          )
        }
      />
      <FailureDetailsDialog
        target={details ? failure(details) : null}
        incident={details}
        onClose={() => setDetails(null)}
      />
      <RetryDialog
        key={retry?.id ?? 'none'}
        target={retry}
        what={retry?.what ?? ''}
        onClose={() => setRetry(null)}
      />
      {annotated && (
        <AnnotationDialog key={annotated.id} incident={annotated} onClose={() => setAnnotated(null)} />
      )}
    </>
  );
}
