/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Download, File, FileCode2, FileImage, PackageOpen, PencilRuler, SearchX, Trash2, Upload, Workflow } from 'lucide-react';
import { kindOfFile } from '../modeler/kinds';
import {
  createDeployment,
  deleteDeployment,
  getDeployment,
  getDeploymentDefinitions,
  getDeploymentResources,
  getDeployments,
  getResourceText,
  resourceDataUrl,
  type Deployment,
  type DeploymentResource,
  type DeploymentSortBy,
  type Sorting,
} from '../api/cockpit';
import { isApiError } from '../api/http';
import { useI18n } from '../i18n';
import { Button, Dialog, EmptyState, ErrorState, Field, Loading, NoMatch, Skeleton, cx } from '../ui';
import {
  COCKPIT,
  Card,
  Checkbox,
  ConfirmDialog,
  DataTable,
  Id,
  PAGE_SIZE,
  PageHeader,
  Pager,
  SearchInput,
  Time,
  pageOf,
  useCockpitAction,
  useUrlSearch,
  useRemembered,
  useUrlState,
  type Column,
} from './common';
import { definitionPath } from './DefinitionsPage';

const BpmnDiagram = lazy(() => import('../bpmn/BpmnDiagram'));
const DmnViewer = lazy(() => import('../dmn/DmnViewer'));

function deploymentPath(id: string): string {
  return `/cockpit/deployments/${encodeURIComponent(id)}`;
}

export type ResourceKind = 'bpmn' | 'dmn' | 'image' | 'text' | 'other';

const TEXT = /\.(form|json|xml|cmmn|txt|md|js|groovy|py|rb|ftl|html?|css|properties|ya?ml|sql|csv|feel|java)$/i;

/** How a resource is shown, judged by its file name. */
export function resourceKind(name: string): ResourceKind {
  if (/\.bpmn(20\.xml)?$/i.test(name)) return 'bpmn';
  if (/\.dmn(11\.xml)?$/i.test(name)) return 'dmn';
  if (/\.(png|jpe?g|gif|svg)$/i.test(name)) return 'image';
  if (TEXT.test(name)) return 'text';
  return 'other';
}

function CreateDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const input = useRef<HTMLInputElement>(null);
  const [name, setName] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [duplicateFiltering, setDuplicateFiltering] = useState(true);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);
  const nameError = name.trim() ? null : t('validation.required');
  const filesError = files.length > 0 ? null : t('cockpit.deployments.create.noFiles');
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t('cockpit.deployments.create.title')}>
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmitted(true);
          if (nameError || filesError) return;
          setBusy(true);
          let created: Deployment | undefined;
          void run(
            async () => {
              created = await createDeployment({
                name: name.trim(),
                files,
                tenantId: tenantId.trim() || undefined,
                duplicateFiltering,
              });
            },
            {
              success: t('cockpit.deployments.create.done', { name: name.trim() }),
              failure: t('cockpit.deployments.create.failed'),
            },
          ).then((ok) => {
            setBusy(false);
            if (ok && created) onCreated(created.id);
          });
        }}
      >
        <p className="confirm__text">{t('cockpit.deployments.create.text')}</p>
        <Field label={t('cockpit.deployments.name')} required error={submitted ? nameError : null}>
          {(props) => (
            <input
              {...props}
              className="input"
              autoFocus
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          )}
        </Field>
        <Field
          label={t('cockpit.deployments.create.files')}
          required
          hint={t('cockpit.deployments.create.filesHint')}
          error={submitted ? filesError : null}
        >
          {(props) => (
            <input
              {...props}
              ref={input}
              className="input input--file"
              type="file"
              multiple
              onChange={(event) => {
                const chosen = Array.from(event.target.files ?? []);
                setFiles(chosen);
                const first = chosen[0];
                if (first && !name.trim()) {
                  setName(first.name.replace(/\.[^.]+$/, ''));
                }
              }}
            />
          )}
        </Field>
        {files.length > 0 && (
          <ul className="file-list">
            {files.map((file) => (
              <li key={file.name}>
                <File aria-hidden size={14} />
                {file.name}
                <span className="muted">{Math.max(1, Math.round(file.size / 1024))} KB</span>
              </li>
            ))}
          </ul>
        )}
        <Field label={t('cockpit.tenant')} hint={t('cockpit.deployments.create.tenantHint')}>
          {(props) => (
            <input
              {...props}
              className="input"
              value={tenantId}
              onChange={(event) => setTenantId(event.target.value)}
            />
          )}
        </Field>
        <Checkbox
          label={t('cockpit.deployments.create.changedOnly')}
          hint={t('cockpit.deployments.create.changedOnlyHint')}
          checked={duplicateFiltering}
          onChange={setDuplicateFiltering}
        />
        <div className="confirm__actions">
          <Button onClick={onClose}>{t('action.cancel')}</Button>
          <Button type="submit" variant="primary" icon={Upload} busy={busy}>
            {t('cockpit.deployments.create.confirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** What was deployed to the engine, newest first. */
export function DeploymentsPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useUrlState();
  const [sorting, setSorting] = useRemembered<Sorting<DeploymentSortBy>>('deployments.sort', {
    sortBy: 'deploymentTime',
    sortOrder: 'desc',
  });
  const term = params.get('q') ?? '';
  const [search, setSearch] = useUrlSearch(term, setParams);
  const page = pageOf(params);
  const [creating, setCreating] = useState(false);


  const deployments = useQuery({
    queryKey: [COCKPIT, 'deployments', term, sorting, page],
    queryFn: ({ signal }) =>
      getDeployments({
        search: term || undefined,
        sorting,
        paging: { firstResult: page * PAGE_SIZE, maxResults: PAGE_SIZE },
        signal,
      }),
    placeholderData: keepPreviousData,
  });

  const columns = useMemo<Column<Deployment>[]>(
    () => [
      {
        id: 'name',
        header: t('cockpit.deployments.name'),
        sortBy: 'name',
        cell: (deployment) => (
          <Link className="cell-link" to={deploymentPath(deployment.id)}>
            {deployment.name || t('cockpit.deployments.unnamed')}
          </Link>
        ),
      },
      {
        id: 'time',
        header: t('cockpit.deployments.time'),
        sortBy: 'deploymentTime',
        cell: (deployment) => <Time value={deployment.deploymentTime} />,
      },
      {
        id: 'source',
        header: t('cockpit.deployments.source'),
        secondary: true,
        cell: (deployment) => deployment.source ?? <span className="muted">—</span>,
      },
      {
        id: 'tenant',
        header: t('cockpit.tenant'),
        secondary: true,
        cell: (deployment) => deployment.tenantId ?? <span className="muted">—</span>,
      },
      {
        id: 'id',
        header: t('cockpit.id'),
        secondary: true,
        cell: (deployment) => <Id value={deployment.id} />,
      },
    ],
    [t],
  );

  return (
    <div className="page">
      <PageHeader
        title={t('cockpit.deployments.title')}
        subtitle={
          deployments.data ? t('cockpit.deployments.count', { count: deployments.data.count }) : undefined
        }
        actions={
          <Button variant="primary" icon={Upload} onClick={() => setCreating(true)}>
            {t('cockpit.deployments.create.action')}
          </Button>
        }
      />
      <Card flush>
        <div className="toolbar">
          <SearchInput value={search} onChange={setSearch} label={t('cockpit.deployments.search')} />
        </div>
        <DataTable
          label={t('cockpit.deployments.title')}
          columns={columns}
          updating={deployments.isPlaceholderData}
          rows={deployments.data?.items}
          rowKey={(deployment) => deployment.id}
          error={deployments.error}
          onRetry={() => void deployments.refetch()}
          sorting={sorting}
          onSort={(next) => {
            setSorting(next as Sorting<DeploymentSortBy>);
            setParams({ page: null });
          }}
          empty={
            term ? (
              <NoMatch search title={t('cockpit.deployments.noMatch')} onClear={() => setSearch('')} />
            ) : (
              <EmptyState icon={PackageOpen} title={t('cockpit.deployments.empty')}>
                {t('cockpit.deployments.emptyHint')}
              </EmptyState>
            )
          }
        />
        <Pager
          page={page}
          count={deployments.data?.count ?? 0}
          onPage={(next) => setParams({ page: next ? String(next + 1) : null }, { push: true })}
        />
      </Card>
      {creating && (
        <CreateDialog
          onClose={() => setCreating(false)}
          onCreated={(id) => void navigate(deploymentPath(id))}
        />
      )}
    </div>
  );
}

function ResourceIcon({ kind }: { kind: ResourceKind }) {
  const Icon = kind === 'bpmn' ? Workflow : kind === 'image' ? FileImage : kind === 'other' ? File : FileCode2;
  return <Icon aria-hidden size={15} />;
}

function ResourceView({ deploymentId, resource }: { deploymentId: string; resource: DeploymentResource }) {
  const { t } = useI18n();
  const kind = resourceKind(resource.name);
  const [source, setSource] = useState(false);
  const textual = kind === 'bpmn' || kind === 'dmn' || kind === 'text';
  const content = useQuery({
    queryKey: ['deployment-resource', deploymentId, resource.id],
    queryFn: () => getResourceText(deploymentId, resource.id),
    enabled: textual,
    staleTime: Infinity,
  });
  const url = resourceDataUrl(deploymentId, resource.id);
  const model = (kind === 'bpmn' || kind === 'dmn') && !source;

  return (
    <section className="resource" aria-label={resource.name}>
      <header className="resource__header">
        <h2 className="resource__name">{resource.name}</h2>
        {(kind === 'bpmn' || kind === 'dmn') && (
          <button
            type="button"
            className={cx('toggle', source && 'is-on')}
            aria-pressed={source}
            onClick={() => setSource(!source)}
          >
            <FileCode2 aria-hidden size={14} />
            {t('cockpit.resource.source')}
          </button>
        )}
        {kindOfFile(resource.name) && (
          <Link
            className="btn btn--secondary btn--sm"
            to={`/cockpit/modeler?${new URLSearchParams({ deployment: deploymentId, resource: resource.id, name: resource.name }).toString()}`}
          >
            <PencilRuler className="btn__icon" aria-hidden size={15} />
            {t('modeler.editResource')}
          </Link>
        )}
        <a className="btn btn--secondary btn--sm" href={url} download={resource.name.split('/').pop()}>
          <Download className="btn__icon" aria-hidden size={15} />
          {t('cockpit.download')}
        </a>
      </header>
      {kind === 'image' ? (
        <div className="resource__image">
          <img src={url} alt={resource.name} />
        </div>
      ) : kind === 'other' ? (
        <EmptyState icon={File} title={t('cockpit.resource.noPreview')}>
          {t('cockpit.resource.noPreviewHint')}
        </EmptyState>
      ) : content.isError ? (
        <ErrorState error={content.error} onRetry={() => void content.refetch()} />
      ) : content.data === undefined ? (
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      ) : model ? (
        <div className="resource__model">
          <Suspense fallback={<Loading label={t('state.loading')} />}>
            {kind === 'bpmn' ? <BpmnDiagram xml={content.data} /> : <DmnViewer xml={content.data} />}
          </Suspense>
        </div>
      ) : (
        <pre className="code code--resource" tabIndex={0}>
          {content.data}
        </pre>
      )}
    </section>
  );
}

function DeleteDeploymentDialog({
  deployment,
  onClose,
  onDeleted,
}: {
  deployment: Deployment;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const { t } = useI18n();
  const run = useCockpitAction();
  const [cascade, setCascade] = useState(false);
  const [skipListeners, setSkipListeners] = useState(false);
  const [skipMappings, setSkipMappings] = useState(false);
  const [busy, setBusy] = useState(false);
  const name = deployment.name || deployment.id;
  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => !open && onClose()}
      title={t('cockpit.deployments.delete.title')}
      text={t('cockpit.deployments.delete.text', { name })}
      confirmLabel={t('cockpit.deployments.delete.confirm')}
      danger
      busy={busy}
      onConfirm={() => {
        setBusy(true);
        void run(
          () =>
            deleteDeployment(deployment.id, {
              cascade,
              skipCustomListeners: skipListeners,
              skipIoMappings: skipMappings,
            }),
          {
            success: t('cockpit.deployments.delete.done', { name }),
            failure: t('cockpit.deployments.delete.failed'),
          },
        ).then((ok) => {
          setBusy(false);
          if (ok) onDeleted();
        });
      }}
    >
      <Checkbox
        label={t('cockpit.delete.cascade')}
        hint={t('cockpit.deployments.delete.cascadeHint')}
        checked={cascade}
        onChange={setCascade}
      />
      <Checkbox
        label={t('cockpit.delete.skipListeners')}
        hint={t('cockpit.delete.skipListenersHint')}
        checked={skipListeners}
        onChange={setSkipListeners}
      />
      <Checkbox
        label={t('cockpit.delete.skipMappings')}
        hint={t('cockpit.delete.skipMappingsHint')}
        checked={skipMappings}
        onChange={setSkipMappings}
      />
    </ConfirmDialog>
  );
}

/** One deployment: its resources and what each of them contains. */
export function DeploymentPage() {
  const { id = '' } = useParams();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [params, setParams] = useUrlState();
  const [deleting, setDeleting] = useState(false);
  const deployment = useQuery({
    queryKey: [COCKPIT, 'deployment', id],
    queryFn: () => getDeployment(id),
  });
  const resources = useQuery({
    queryKey: [COCKPIT, 'deployment-resources', id],
    queryFn: () => getDeploymentResources(id),
    enabled: !!deployment.data,
  });
  const definitions = useQuery({
    queryKey: [COCKPIT, 'deployment-definitions', id],
    queryFn: () => getDeploymentDefinitions(id),
    enabled: !!deployment.data,
  });

  if (deployment.isError && !deployment.data) {
    return (
      <div className="page">
        <PageHeader
          title={t('cockpit.deployment')}
          back={{ to: '/cockpit/deployments', label: t('cockpit.deployments.title') }}
        />
        <Card>
          {isApiError(deployment.error, 'notFound') ? (
            <EmptyState icon={SearchX} title={t('cockpit.deployments.notFound')} />
          ) : (
            <ErrorState error={deployment.error} onRetry={() => void deployment.refetch()} />
          )}
        </Card>
      </div>
    );
  }
  if (!deployment.data) {
    return (
      <div className="page">
        <Loading label={t('state.loading')}>
          <Skeleton lines={8} />
        </Loading>
      </div>
    );
  }

  const current = deployment.data;
  const sorted = [...(resources.data ?? [])].sort((a, b) => a.name.localeCompare(b.name));
  const selected = sorted.find((resource) => resource.id === params.get('resource')) ?? sorted[0];
  const definitionOf = (resource: DeploymentResource) =>
    definitions.data?.find((definition) => definition.resource === resource.name);

  return (
    <div className="page">
      <PageHeader
        title={current.name || t('cockpit.deployments.unnamed')}
        back={{ to: '/cockpit/deployments', label: t('cockpit.deployments.title') }}
        subtitle={
          <>
            <span>
              {t('cockpit.deployments.time')}: <Time value={current.deploymentTime} />
            </span>
            {current.source && (
              <span>
                {t('cockpit.deployments.source')}: {current.source}
              </span>
            )}
            {current.tenantId && <span>{t('cockpit.tenantOf', { tenant: current.tenantId })}</span>}
            <Id value={current.id} copy />
          </>
        }
        actions={
          <Button variant="danger-ghost" icon={Trash2} onClick={() => setDeleting(true)}>
            {t('cockpit.deployments.delete.action')}
          </Button>
        }
      />
      <div className="deployment">
        <Card flush title={t('cockpit.deployments.resources')} className="deployment__resources">
          {resources.isError ? (
            <ErrorState error={resources.error} onRetry={() => void resources.refetch()} />
          ) : !resources.data ? (
            <Loading label={t('state.loading')}>
              <Skeleton lines={4} />
            </Loading>
          ) : sorted.length === 0 ? (
            <EmptyState title={t('cockpit.deployments.noResources')} />
          ) : (
            <ul className="resource-list">
              {sorted.map((resource) => {
                const definition = definitionOf(resource);
                return (
                  <li key={resource.id}>
                    <button
                      type="button"
                      className={cx('resource-list__item', resource.id === selected?.id && 'is-selected')}
                      aria-current={resource.id === selected?.id ? 'true' : undefined}
                      onClick={() => setParams({ resource: resource.id })}
                    >
                      <ResourceIcon kind={resourceKind(resource.name)} />
                      <span className="resource-list__name">{resource.name}</span>
                    </button>
                    {definition && (
                      <Link className="link link--subtle resource-list__link" to={definitionPath(definition.id)}>
                        {t('cockpit.deployments.openDefinition', {
                          name: definition.name ?? definition.key,
                          version: definition.version,
                        })}
                      </Link>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
        <Card flush className="deployment__view">
          {selected ? (
            <ResourceView key={selected.id} deploymentId={id} resource={selected} />
          ) : (
            <EmptyState icon={File} title={t('cockpit.deployments.chooseResource')} />
          )}
        </Card>
      </div>
      {deleting && (
        <DeleteDeploymentDialog
          deployment={current}
          onClose={() => setDeleting(false)}
          onDeleted={() => void navigate('/cockpit/deployments')}
        />
      )}
    </div>
  );
}
