/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Download, FileJson, ShieldCheck, ShieldOff } from 'lucide-react';
import {
  AUDIT_TYPES,
  auditDetails,
  exportAudit,
  getAuditEvents,
  getAuditStatus,
  verifyAudit,
  type AuditEvent,
  type AuditQuery,
  type AuditStatus,
  type AuditType,
  type AuditVerification,
} from '../api/audit';
import { useI18n, type MessageKey } from '../i18n';
import { downloadBlob } from '../lib/csv';
import { fromDateTimeInput, toEngineDate } from '../lib/dates';
import { Button, EmptyState, ErrorState, Loading, NoMatch, Notice } from '../ui';
import { TextFilter } from '../ui/controls';
import { Id, Time } from '../ui/format';
import { pageOf, useUrlState } from '../ui/hooks';
import { Card, PageHeader } from '../ui/page';
import { DataTable, PAGE_SIZE, Pager, useClampedPage, type Column } from '../ui/table';
import { useToast } from '../ui/toast';
import { COCKPIT } from './common';
import { MoreFilters } from './MoreFilters';
import { historyPath } from './paths';

/** The label of each category; literal keys, so that the catalogue check sees them. */
const TYPE_LABELS: Record<AuditType, MessageKey> = {
  process: 'audit.type.process',
  task: 'audit.type.task',
  variable: 'audit.type.variable',
  deployment: 'audit.type.deployment',
  operation: 'audit.type.operation',
  incident: 'audit.type.incident',
  decision: 'audit.type.decision',
  activity: 'audit.type.activity',
  audit: 'audit.type.audit',
};

const REASON_LABELS: Record<string, MessageKey> = {
  'hash-mismatch': 'audit.reason.hash-mismatch',
  'previous-hash-mismatch': 'audit.reason.previous-hash-mismatch',
  'sequence-gap': 'audit.reason.sequence-gap',
  'missing-start': 'audit.reason.missing-start',
  'head-mismatch': 'audit.reason.head-mismatch',
  'head-missing': 'audit.reason.head-missing',
  'checkpoint-mismatch': 'audit.reason.checkpoint-mismatch',
};

export function auditTypeLabel(type: string, t: ReturnType<typeof useI18n>['t']): string {
  const key = TYPE_LABELS[type as AuditType];
  return key ? t(key) : type;
}

/** The settings of the audit trail; shared by the page and the tab of an instance. */
export function useAuditStatus() {
  return useQuery({
    queryKey: [COCKPIT, 'audit-status'],
    queryFn: ({ signal }) => getAuditStatus(signal),
    staleTime: 300_000,
  });
}

function detailText(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="audit-trail__fact">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

/** The facts of one record: its details and where it sits in its chain. */
function RecordDetails({ event }: { event: AuditEvent }) {
  const { t } = useI18n();
  const details = auditDetails(event);
  return (
    <details className="audit-trail__details">
      <summary>{t('audit.details.show')}</summary>
      <dl className="audit-trail__facts">
        {event.entityType && (
          <Fact label={t('audit.details.object')}>
            {event.entityType}
            {event.entityId && <> · <Id value={event.entityId} copy /></>}
          </Fact>
        )}
        {details &&
          Object.entries(details).map(([key, value]) => (
            <Fact key={key} label={key}>
              <span className="value__text">{detailText(value)}</span>
            </Fact>
          ))}
        {event.tenantId && <Fact label={t('audit.details.tenant')}>{event.tenantId}</Fact>}
        <Fact label={t('audit.details.engine')}>{event.engineName ?? '—'}</Fact>
        <Fact label={t('audit.chain')}>{t('audit.details.record', { sequence: event.sequence, chain: event.chainId })}</Fact>
        <Fact label={t('audit.details.hash')}>
          <Id value={event.hash} copy />
        </Fact>
        <Fact label={t('audit.details.previous')}>
          <Id value={event.previousHash} copy />
        </Fact>
      </dl>
    </details>
  );
}

/** Records of the audit trail, a page at a time. */
export function AuditTrailTable({
  query,
  pageParam = 'apage',
  onClearFilters,
  showCase = true,
}: {
  query: AuditQuery;
  pageParam?: string;
  onClearFilters?: () => void;
  showCase?: boolean;
}) {
  const { t } = useI18n();
  const [params, setParams] = useUrlState();
  const page = pageOf(params, pageParam);
  const records = useQuery({
    queryKey: [COCKPIT, 'audit-trail', query, page],
    queryFn: ({ signal }) => getAuditEvents(query, { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  });
  const toPage = useCallback(
    (next: number, push = false) => setParams({ [pageParam]: next ? String(next + 1) : null }, { push }),
    [setParams, pageParam],
  );
  // a page past the end, from a link or after a purge shortened the trail; the count shown
  // meanwhile for other filters says nothing about these
  useClampedPage(page, records.isPlaceholderData ? undefined : records.data?.count, toPage);
  const columns = useMemo<Column<AuditEvent>[]>(() => {
    const all: Column<AuditEvent>[] = [
      { id: 'time', header: t('audit.col.time'), cell: (row) => <Time value={row.timestamp} /> },
      {
        id: 'type',
        header: t('audit.col.type'),
        cell: (row) => <span className={`audit-trail__type audit-trail__type--${row.type}`}>{auditTypeLabel(row.type, t)}</span>,
      },
      {
        id: 'what',
        header: t('audit.col.what'),
        cell: (row) => (
          <span className="audit-trail__what">
            <strong>{row.action ?? '—'}</strong>
            {row.entityName && <span className="muted">{row.entityName}</span>}
          </span>
        ),
      },
      {
        id: 'actor',
        header: t('audit.col.actor'),
        cell: (row) => row.actor ?? <span className="muted">{t('audit.system')}</span>,
      },
    ];
    if (showCase) {
      all.push({
        id: 'case',
        header: t('audit.col.case'),
        secondary: true,
        cell: (row) =>
          row.processInstanceId ? (
            <Link className="cell-link" to={historyPath(row.processInstanceId)}>
              {row.businessKey ?? row.processDefinitionKey ?? row.processInstanceId}
            </Link>
          ) : (
            <span className="muted">—</span>
          ),
      });
    }
    all.push({ id: 'details', header: t('audit.col.details'), cell: (row) => <RecordDetails event={row} /> });
    return all;
  }, [t, showCase]);
  return (
    <>
      <DataTable
        label={t('audit.table')}
        columns={columns}
        updating={records.isPlaceholderData}
        rows={records.data?.items}
        rowKey={(row) => row.id}
        exportable={false}
        error={records.error}
        onRetry={() => void records.refetch()}
        empty={onClearFilters ? <NoMatch onClear={onClearFilters} /> : <EmptyState icon={ShieldCheck} title={t('audit.empty')} />}
      />
      <Pager
        page={page}
        count={records.data?.count ?? 0}
        onPage={(next) => toPage(next, true)}
      />
    </>
  );
}

/** What an intact trail is reported with: "12 records in 1 chain", each count in its own plural form. */
function intactMessage(result: AuditVerification, t: ReturnType<typeof useI18n>['t']): string {
  return t('audit.intact', {
    records: t('audit.recordCount', { count: result.records }),
    chains: t('audit.chainCount', { count: result.chains.length }),
  });
}

/**
 * The outcome of a verification, for every chain. A break is an alert; an intact trail is
 * announced by the live region of the page, which is there before the result.
 */
export function VerificationResult({ result }: { result: AuditVerification }) {
  const { t, number } = useI18n();
  const found = result.firstBreak;
  return (
    <div className="audit-trail__result">
      {found ? (
        <Notice tone="danger" title={t('audit.broken')}>
          <p>
            <strong>{REASON_LABELS[found.reason] ? t(REASON_LABELS[found.reason] as MessageKey) : found.reason}</strong>
          </p>
          <p>{t('audit.brokenAt', { chain: found.chainId, sequence: found.sequence, message: found.message })}</p>
          <p>{t('audit.brokenHint')}</p>
        </Notice>
      ) : (
        <Notice tone="info" title={intactMessage(result, t)}>
          <p>{t('audit.intactHint')}</p>
        </Notice>
      )}
      <div className="table-wrap">
        <table className="table audit-trail__chains">
          <thead>
            <tr>
              <th scope="col">{t('audit.chain')}</th>
              <th scope="col">{t('audit.chainRecords')}</th>
              <th scope="col">{t('audit.chainCheckpoints')}</th>
              <th scope="col">{t('audit.chainLastHash')}</th>
            </tr>
          </thead>
          <tbody>
            {result.chains.map((chain) => (
              <tr key={chain.chainId} className={chain.intact ? undefined : 'audit-trail__broken'}>
                <th scope="row">
                  {chain.intact ? <ShieldCheck aria-hidden size={14} /> : <ShieldOff aria-hidden size={14} />} {chain.chainId}
                  {chain.purgedThrough > 0 && <div className="muted">{t('audit.chainPurged', { sequence: chain.purgedThrough })}</div>}
                </th>
                <td>{number(chain.records)}</td>
                <td>{t('audit.checkpointsVerified', { verified: chain.checkpointsVerified, total: chain.checkpoints })}</td>
                <td>{chain.lastHash ? <Id value={chain.lastHash} copy /> : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function SettingsLine({ status }: { status: AuditStatus }) {
  const { t } = useI18n();
  return (
    <p className="muted audit-trail__settings">
      {t('audit.settings', {
        node: status.nodeId ?? '—',
        chains: t('audit.chainCount', { count: status.chains }),
        values: status.variableValues ?? '—',
        signed: status.signed ? t('audit.signed') : t('audit.unsigned'),
      })}
      {status.historyLevel && status.historyLevel !== 'full' && <> {t('audit.historyLevel', { level: status.historyLevel })}</>}
    </p>
  );
}

const FILTERS = ['pi', 'bk', 'user', 'type', 'action', 'from', 'to'] as const;

/** The audit trail of the engine: filters, export and verification. */
export function AuditTrailPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [params, setParams] = useUrlState();
  const status = useAuditStatus();
  const [exporting, setExporting] = useState<'csv' | 'json' | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [verification, setVerification] = useState<AuditVerification | null>(null);

  const date = (name: string) => {
    const parsed = fromDateTimeInput(params.get(name) ?? '');
    return parsed ? toEngineDate(parsed) : undefined;
  };
  const query: AuditQuery = {
    processInstanceId: params.get('pi') || undefined,
    businessKey: params.get('bk') || undefined,
    actor: params.get('user') || undefined,
    type: params.get('type') || undefined,
    action: params.get('action') || undefined,
    from: date('from'),
    to: date('to'),
  };

  const runExport = async (format: 'csv' | 'json') => {
    setExporting(format);
    try {
      const { blob, filename } = await exportAudit(query, format);
      downloadBlob(blob, filename);
      toast.show({ title: t('audit.exported') });
    } catch (error) {
      toast.error(error, t('audit.exportFailed'));
    } finally {
      setExporting(null);
    }
  };
  const runVerify = async () => {
    setVerifying(true);
    try {
      setVerification(await verifyAudit());
    } catch (error) {
      toast.error(error, t('audit.verifyFailed'));
    } finally {
      setVerifying(false);
    }
  };

  const change = (name: string, value: string) => {
    const next = value.trim() || null;
    if (next === (params.get(name) || null)) return;
    setParams({ [name]: next, apage: null });
  };
  const text = (name: string, label: string) => (
    <TextFilter key={name} label={label} value={params.get(name) ?? ''} onApply={(value) => change(name, value)} />
  );

  if (status.isPending) {
    return (
      <div className="page">
        <Loading label={t('audit.title')} />
      </div>
    );
  }
  if (status.isError) {
    return (
      <div className="page">
        <PageHeader title={t('audit.title')} subtitle={t('audit.subtitle')} />
        <ErrorState error={status.error} onRetry={() => void status.refetch()} />
      </div>
    );
  }
  if (!status.data.enabled) {
    return (
      <div className="page">
        <PageHeader title={t('audit.title')} subtitle={t('audit.subtitle')} />
        <Card>
          <EmptyState icon={ShieldOff} title={t('audit.off')}>
            {t('audit.offHint')}
          </EmptyState>
        </Card>
      </div>
    );
  }
  return (
    <div className="page">
      <PageHeader
        title={t('audit.title')}
        subtitle={t('audit.subtitle')}
        actions={
          <>
            <Button icon={Download} busy={exporting === 'csv'} disabled={exporting !== null} onClick={() => void runExport('csv')}>
              {t('audit.export.csv')}
            </Button>
            <Button
              icon={FileJson}
              busy={exporting === 'json'}
              disabled={exporting !== null}
              title={t('audit.export.hint')}
              onClick={() => void runExport('json')}
            >
              {t('audit.export.json')}
            </Button>
            <Button variant="primary" icon={ShieldCheck} busy={verifying} onClick={() => void runVerify()}>
              {verifying ? t('audit.verifying') : t('audit.verify')}
            </Button>
          </>
        }
      />
      <SettingsLine status={status.data} />
      {/* mounted before there is a result, so that screen readers announce it; emptied while verifying, so that the same outcome is announced again */}
      <p className="sr-only" role="status">
        {verification && !verification.firstBreak && !verifying ? intactMessage(verification, t) : ''}
      </p>
      {verification && (
        <Card>
          <VerificationResult result={verification} />
        </Card>
      )}
      <Card flush>
        <div className="toolbar toolbar--filters">
          {text('pi', t('audit.filter.instance'))}
          {text('bk', t('audit.filter.businessKey'))}
          {text('user', t('audit.filter.user'))}
          <select
            className="input toolbar__select"
            aria-label={t('audit.col.type')}
            value={params.get('type') ?? ''}
            onChange={(event) => setParams({ type: event.target.value || null, apage: null })}
          >
            <option value="">{t('audit.type.all')}</option>
            {AUDIT_TYPES.map((type) => (
              <option key={type} value={type}>
                {auditTypeLabel(type, t)}
              </option>
            ))}
          </select>
          {text('action', t('audit.filter.action'))}
          <MoreFilters active={['from', 'to'].filter((name) => params.get(name)).length}>
            <div className="date-range">
              {(['from', 'to'] as const).map((name) => (
                <label key={name} className="date-filter">
                  <span className="field__label">{name === 'from' ? t('audit.filter.from') : t('audit.filter.to')}</span>
                  <input
                    className="input"
                    type="datetime-local"
                    value={params.get(name) ?? ''}
                    onChange={(event) => setParams({ [name]: event.target.value || null, apage: null })}
                  />
                </label>
              ))}
            </div>
          </MoreFilters>
        </div>
        <AuditTrailTable
          query={query}
          onClearFilters={
            FILTERS.some((name) => params.get(name))
              ? () => setParams({ ...Object.fromEntries(FILTERS.map((name) => [name, null])), apage: null })
              : undefined
          }
        />
      </Card>
    </div>
  );
}
