/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useContext, useEffect, useRef, useState } from 'react';
import { SessionContext } from '../app/session';
import { env } from '../env';
import { readDraft, removeDraft, writeDraft } from '../forms/drafts';
import { draftPrefix } from '../forms/embedded/broker';
import '../forms/forms.css';
import { useBlocker, useNavigate, useSearchParams } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CircleCheck, CircleX, Download, FilePlus2, FolderOpen, Rocket } from 'lucide-react';
import {
  createDeployment,
  getDecisionDefinition,
  getDecisionXml,
  getDefinition,
  getDeployment,
  getLatestDefinitionId,
  getResourceText,
  startProcessInstance,
} from '../api/cockpit';
import { getProcessXml } from '../api/engine';
import { COCKPIT } from '../cockpit/common';
import { Checkbox, ConfirmDialog } from '../ui/controls';
import { Card, PageHeader } from '../ui/page';
import { useI18n, type MessageKey } from '../i18n';
import { Button, ErrorState, Field, Loading, MessageError, cx } from '../ui';
import { useToast } from '../ui/toast';
import { KINDS, MODEL_KINDS, isModelKind, kindOfFile, type EditorHandle, type ModelKind } from './kinds';
import { checkReadiness, type Finding } from './readiness';

export { blankProcess, processIdOf } from './kinds';

const EDITORS = {
  bpmn: lazy(() => import('./BpmnEditor')),
  dmn: lazy(() => import('./DmnEditor')),
  form: lazy(() => import('./FormEditor')),
};

const KIND_LABEL: Record<ModelKind, MessageKey> = {
  bpmn: 'modeler.kind.bpmn',
  dmn: 'modeler.kind.dmn',
  form: 'modeler.kind.form',
};

interface Model {
  kind: ModelKind;
  text: string;
  source: string;
}

/** Where a deployed model is read from, by the parameters of the page. */
interface DeployedSource {
  kind: ModelKind;
  load: () => Promise<string>;
  /** the tenant of what was deployed; a new version is deployed for the same tenant */
  tenant: () => Promise<string | null>;
  key: string;
}

function sourceKey(search: string): string | null {
  return deployedSource(new URLSearchParams(search))?.key ?? null;
}

function deployedSource(params: URLSearchParams): DeployedSource | null {
  const definition = params.get('definition');
  if (definition) {
    return {
      kind: 'bpmn',
      load: () => getProcessXml(definition),
      tenant: async () => (await getDefinition(definition)).tenantId ?? null,
      key: `p:${definition}`,
    };
  }
  const decision = params.get('decision');
  if (decision) {
    return {
      kind: 'dmn',
      load: () => getDecisionXml(decision),
      tenant: async () => (await getDecisionDefinition(decision)).tenantId ?? null,
      key: `d:${decision}`,
    };
  }
  const deployment = params.get('deployment');
  const resource = params.get('resource');
  const name = params.get('name') ?? '';
  const kind = kindOfFile(name);
  if (deployment && resource && kind) {
    return {
      kind,
      load: () => getResourceText(deployment, resource),
      tenant: async () => (await getDeployment(deployment)).tenantId ?? null,
      key: `r:${deployment}:${resource}`,
    };
  }
  return null;
}

/** What was found in the model before it is deployed: errors stop the deployment. */
function Readiness({ findings }: { findings: Finding[] | null }) {
  const { t } = useI18n();
  if (!findings) {
    return <Loading label={t('readiness.checking')} />;
  }
  const errors = findings.filter((finding) => finding.level === 'error').length;
  return (
    <section className="readiness" aria-label={t('readiness.title')}>
      <p className={cx('readiness__summary', errors > 0 ? 'readiness__summary--error' : findings.length > 0 && 'readiness__summary--warning')} role="status">
        {errors > 0 ? (
          <CircleX aria-hidden size={16} />
        ) : findings.length > 0 ? (
          <AlertTriangle aria-hidden size={16} />
        ) : (
          <CircleCheck aria-hidden size={16} />
        )}
        {errors > 0
          ? t('readiness.blocked', { errors, warnings: findings.length - errors })
          : findings.length > 0
            ? t('readiness.warnings', { warnings: findings.length })
            : t('readiness.ready')}
      </p>
      {findings.length > 0 && (
        <ul className="readiness__list" tabIndex={0} aria-label={t('readiness.title')}>
          {findings.map((finding, index) => (
            <li key={index} className={cx('readiness__item', `readiness__item--${finding.level}`)}>
              <span className="sr-only">{finding.level === 'error' ? t('readiness.error') : t('readiness.warning')}: </span>
              {finding.level === 'error' ? <CircleX aria-hidden size={14} /> : <AlertTriangle aria-hidden size={14} />}
              <span>
                {finding.element && <strong className="readiness__element">{finding.element}: </strong>}
                {t(finding.message)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/**
 * Under the editor: the file, whether it has changes that are not deployed,
 * and what the checks before deploying find now, so a problem shows up while
 * drawing instead of only in the deploy dialog.
 */
function ModelStatus({
  kind,
  dirty,
  editor,
  revision,
}: {
  kind: ModelKind;
  dirty: boolean;
  editor: React.RefObject<EditorHandle | null>;
  revision: number;
}) {
  const { t } = useI18n();
  const [findings, setFindings] = useState<Finding[] | null>(null);
  // the file is named after the model as it is now, e.g. after its id was changed
  const [fileName, setFileName] = useState('');
  const [open, setOpen] = useState(false);
  useEffect(() => {
    let active = true;
    // shown once the editor is ready; the checks wait a moment after the last edit
    const timer = window.setTimeout(() => {
      void editor.current?.save().then(
        (text) => {
          if (!active) return;
          setFindings(checkReadiness(kind, text));
          setFileName(`${KINDS[kind].idOf(text) ?? kind}${KINDS[kind].extension}`);
        },
        () => {
          // a model that cannot be written out has no verdict; an older one would mislead
          if (active) setFindings(null);
        },
      );
    }, 400);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [editor, kind, revision]);
  const errors = findings?.filter((finding) => finding.level === 'error').length ?? 0;
  const warnings = (findings?.length ?? 0) - errors;
  const tone = errors > 0 ? 'error' : warnings > 0 ? 'warning' : 'ok';
  const Icon = tone === 'error' ? CircleX : tone === 'warning' ? AlertTriangle : CircleCheck;
  return (
    <>
      <div className="modeler-status">
        <span>{t(KIND_LABEL[kind])}</span>
        {fileName && <span className="modeler-status__file mono">{fileName}</span>}
        {dirty && <span className="modeler-status__dirty">{t('modeler.unsaved')}</span>}
        {findings && (
          <button
            type="button"
            className={cx('modeler-status__check', tone !== 'ok' && `modeler-status__check--${tone}`)}
            aria-expanded={findings.length > 0 ? open : undefined}
            disabled={findings.length === 0}
            onClick={() => setOpen(!open)}
          >
            <Icon aria-hidden size={15} />
            {tone === 'error'
              ? t('readiness.blocked', { errors, warnings })
              : tone === 'warning'
                ? t('readiness.warnings', { warnings })
                : t('readiness.ready')}
          </button>
        )}
      </div>
      {open && findings && findings.length > 0 && (
        <div className="modeler-status__findings">
          <Readiness findings={findings} />
        </div>
      )}
    </>
  );
}

function DeployDialog({
  onClose,
  editor,
  kind,
  initialName,
  tenantId,
  onDeployed,
}: {
  onClose: () => void;
  /** with the page of the instance that was started to try the new version, if one was */
  onDeployed: (startedPage: string | null) => void;
  editor: React.RefObject<EditorHandle | null>;
  kind: ModelKind;
  initialName: string;
  tenantId: string | null;
}) {
  const { t } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [name, setName] = useState(initialName);
  const [changedOnly, setChangedOnly] = useState(true);
  const [startAfter, setStartAfter] = useState(false);
  const [busy, setBusy] = useState(false);
  const info = KINDS[kind];
  const fileName = `${name.trim() || kind}${info.extension}`;
  // the model as it would be deployed, checked once when the dialog opens
  const [findings, setFindings] = useState<Finding[] | null>(null);
  useEffect(() => {
    let active = true;
    void editor.current?.save().then(
      (text) => active && setFindings(checkReadiness(kind, text)),
      () => active && setFindings([{ level: 'error', message: 'readiness.unsaved' }]),
    );
    return () => {
      active = false;
    };
  }, [editor, kind]);
  const blocked = !findings || findings.some((finding) => finding.level === 'error');
  return (
    <ConfirmDialog
      open
      onOpenChange={(next) => !next && onClose()}
      title={t('modeler.deploy.title')}
      text={
        <>
          {t('modeler.deploy.text', { file: fileName })}{' '}
          <strong>{tenantId ? t('modeler.deploy.tenant', { tenant: tenantId }) : t('modeler.deploy.noTenant')}</strong>
        </>
      }
      confirmLabel={t('modeler.deploy')}
      busy={busy}
      disabled={!name.trim() || blocked}
      onConfirm={() => {
        const current = editor.current;
        if (!current) return;
        setBusy(true);
        void (async () => {
          try {
            const text = await current.save();
            const deployment = await createDeployment({
              name: name.trim(),
              files: [new File([text], fileName, { type: info.mime })],
              duplicateFiltering: changedOnly,
              tenantId: tenantId ?? undefined,
            });
            toast.show({
              title: t('modeler.deploy.done', { name: deployment.name ?? name }),
              action: {
                label: t('modeler.deploy.show'),
                run: () => void navigate(`/cockpit/deployments/${encodeURIComponent(deployment.id)}`),
              },
            });
            void queryClient.invalidateQueries({ queryKey: [COCKPIT] });
            let started: string | null = null;
            if (startAfter && kind === 'bpmn') {
              try {
                // an unchanged model is not deployed again; its newest version is started then
                const created = Object.values(deployment.deployedProcessDefinitions ?? {})[0]?.id;
                const key = KINDS.bpmn.idOf(text);
                const definitionId = created ?? (key ? await getLatestDefinitionId(key, tenantId) : null);
                if (!definitionId) throw new MessageError('modeler.start.noDefinition');
                const instance = await startProcessInstance(definitionId);
                // an instance that ended right away is in the history only
                started = `/cockpit/${instance.ended ? 'history' : 'processes'}/instance/${encodeURIComponent(instance.id)}`;
              } catch (error) {
                toast.error(error, t('modeler.start.failed'));
              }
            }
            onDeployed(started);
            onClose();
          } catch (error) {
            toast.error(error, t('modeler.deploy.failed'));
          } finally {
            setBusy(false);
          }
        })();
      }}
    >
      <Readiness findings={findings} />
      <Field label={t('modeler.deploy.name')} hint={t('modeler.deploy.nameHint')}>
        {(props) => (
          <input {...props} className="input" value={name} onChange={(event) => setName(event.target.value)} />
        )}
      </Field>
      <Checkbox
        label={t('modeler.deploy.changedOnly')}
        hint={t('modeler.deploy.changedOnlyHint')}
        checked={changedOnly}
        onChange={setChangedOnly}
      />
      {kind === 'bpmn' && (
        <Checkbox
          label={t('modeler.start')}
          hint={t('modeler.start.hint')}
          checked={startAfter}
          onChange={setStartAfter}
        />
      )}
    </ConfirmDialog>
  );
}

/**
 * Draws process models, decisions and forms in the browser and deploys them.
 * A model is started empty, opened from a file or from what is deployed.
 */
export function ModelerPage() {
  const { t, relative } = useI18n();
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const deployedFrom = deployedSource(params);
  const requestedKind = params.get('kind');
  const editor = useRef<EditorHandle | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const [opened, setOpened] = useState<Model | null>(null);
  const [dirty, setDirty] = useState(false);
  // counts the edits, so the checks run again after each one
  const [revision, setRevision] = useState(0);
  // counts the models the editor finished loading; the checks start over for each
  const [loaded, setLoaded] = useState(0);
  const [deploying, setDeploying] = useState(false);
  const [pending, setPending] = useState<Model | null>(null);
  // where to go once the edits no longer hold the page, e.g. the instance started after a deployment
  const [goTo, setGoTo] = useState<string | null>(null);
  const navigate = useNavigate();
  // edits are kept in the browser, so a closed tab or an expired session does not lose them
  const session = useContext(SessionContext);
  const draftKey = session ? `${draftPrefix(env.engine, session.userId)}modeler` : null;
  const [recovered, setRecovered] = useState(() =>
    draftKey ? readDraft<Model & { tenantId?: string | null }>(draftKey) : null,
  );
  // the tenant of a restored model that came from a deployment, for its next deployment
  const [restoredTenant, setRestoredTenant] = useState<string | null>(null);
  // counts discards: a save that was under way when the draft was dropped is not written
  const draftGeneration = useRef(0);
  // what was last downloaded: as long as the model is that, there is nothing to keep
  const downloaded = useRef<string | null>(null);
  const forgetDraft = () => {
    draftGeneration.current++;
    if (draftKey) removeDraft(draftKey);
    setRecovered(null);
  };

  const deployed = useQuery({
    queryKey: ['model-source', deployedFrom?.key],
    queryFn: () => deployedFrom?.load() ?? Promise.resolve(''),
    enabled: !!deployedFrom && !opened,
    staleTime: Infinity,
  });
  const model: Model | null =
    opened ??
    (deployedFrom && deployed.data !== undefined
      ? { kind: deployedFrom.kind, text: deployed.data, source: deployedFrom.key }
      : null);
  const sourceTenant = useQuery({
    queryKey: ['model-tenant', deployedFrom?.key],
    queryFn: () => deployedFrom?.tenant() ?? Promise.resolve(null),
    enabled: !!deployedFrom,
    staleTime: Infinity,
  });
  // only a model that still is the deployed one keeps its tenant
  const fromDeployed = !!deployedFrom && model?.source === deployedFrom.key;
  const kind: ModelKind = model?.kind ?? deployedFrom?.kind ?? (isModelKind(requestedKind) ? requestedKind : 'bpmn');

  const draftTenant = fromDeployed ? (sourceTenant.data ?? null) : restoredTenant;
  const draftSource = model?.source ?? '';
  useEffect(() => {
    if (!dirty || !draftKey) return undefined;
    const generation = draftGeneration.current;
    let active = true;
    const timer = window.setInterval(() => {
      const current = editor.current;
      if (!current) return;
      void current.save().then(
        (text) => {
          if (!active || generation !== draftGeneration.current) return;
          if (text === downloaded.current) {
            removeDraft(draftKey);
            return;
          }
          writeDraft(draftKey, {
            kind: 'model',
            values: { kind, text, source: draftSource, tenantId: draftTenant },
            savedAt: Date.now(),
          });
        },
        () => undefined,
      );
    }, 2000);
    return () => {
      window.clearInterval(timer);
      active = false;
    };
  }, [dirty, draftKey, kind, draftSource, draftTenant]);

  // a link to another kind of model starts a new one of that kind, unless there are edits
  useEffect(() => {
    if (opened && !deployedFrom && !dirty && isModelKind(requestedKind) && requestedKind !== opened.kind) {
      setOpened({ kind: requestedKind, text: KINDS[requestedKind].blank(), source: '' });
    }
  }, [requestedKind, opened, deployedFrom, dirty]);

  useEffect(() => {
    if (!deployedFrom && !opened) {
      setOpened({ kind, text: KINDS[kind].blank(), source: '' });
    }
  }, [deployedFrom, opened, kind]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  // links inside the app would drop the edits as well, and so would opening another deployed model
  // (e.g. through the browser history); other changes of the address within the modeler may happen
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (!dirty) return false;
    if (currentLocation.pathname !== nextLocation.pathname) return true;
    const next = sourceKey(nextLocation.search);
    return next !== null && next !== sourceKey(currentLocation.search);
  });

  // another deployed model asked for by the address replaces the one being edited
  const [shownSource, setShownSource] = useState(deployedFrom?.key ?? null);
  const requestedSource = deployedFrom?.key ?? null;
  if (requestedSource !== shownSource) {
    setShownSource(requestedSource);
    if (requestedSource !== null) {
      setOpened(null);
      setDirty(false);
    }
  }

  const open = (next: Model) => {
    forgetDraft();
    setRestoredTenant(null);
    setDirty(false);
    setOpened(next);
    if (deployedFrom || params.get('kind') !== next.kind) setParams({ kind: next.kind }, { replace: true });
  };

  // replacing the model drops the edits, so ask in the same way as leaving the modeler
  const replace = (next: Model) => {
    if (dirty) setPending(next);
    else open(next);
  };

  const download = async () => {
    if (!editor.current || !model) return;
    try {
      const text = await editor.current.save();
      // the file is the copy now; the draft is dropped until the next edit
      downloaded.current = text;
      forgetDraft();
      const blob = new Blob([text], { type: KINDS[kind].mime });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${KINDS[kind].idOf(text) ?? kind}${KINDS[kind].extension}`;
      document.body.append(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch (error) {
      toast.error(error, t('modeler.saveFailed'));
    }
  };

  useEffect(() => {
    if (goTo && !dirty) {
      setGoTo(null);
      void navigate(goTo);
    }
  }, [goTo, dirty, navigate]);

  const Editor = EDITORS[kind];

  return (
    <div className="page page--modeler">
      {blocker.state === 'blocked' && (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && blocker.reset()}
          title={t('modeler.leave.title')}
          text={t('modeler.discard')}
          confirmLabel={t('modeler.leave')}
          danger
          onConfirm={() => blocker.proceed()}
        />
      )}
      {pending && (
        <ConfirmDialog
          open
          onOpenChange={(isOpen) => !isOpen && setPending(null)}
          title={t('modeler.discard.title')}
          text={t('modeler.discard')}
          confirmLabel={t('modeler.discard.confirm')}
          danger
          onConfirm={() => {
            open(pending);
            setPending(null);
          }}
        />
      )}
      <PageHeader
        title={t('modeler.title')}
        subtitle={t('modeler.subtitle')}
        actions={
          <div className="modeler__actions">
            <div className="segmented" role="group" aria-label={t('modeler.kind')}>
              {MODEL_KINDS.map((candidate) => (
                <button
                  key={candidate}
                  type="button"
                  className={cx('segmented__item', candidate === kind && 'is-active')}
                  aria-pressed={candidate === kind}
                  onClick={() =>
                    candidate !== kind && replace({ kind: candidate, text: KINDS[candidate].blank(), source: '' })
                  }
                >
                  {t(KIND_LABEL[candidate])}
                </button>
              ))}
            </div>
            <Button size="sm" icon={FilePlus2} onClick={() => replace({ kind, text: KINDS[kind].blank(), source: '' })}>
              {t('modeler.new')}
            </Button>
            <Button size="sm" icon={FolderOpen} onClick={() => fileInput.current?.click()}>
              {t('modeler.open')}
            </Button>
            <input
              ref={fileInput}
              type="file"
              accept=".bpmn,.bpmn20.xml,.dmn,.dmn11.xml,.form,.xml,.json"
              hidden
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = '';
                if (!file) return;
                const fileKind = kindOfFile(file.name) ?? (file.name.endsWith('.json') ? 'form' : kind);
                file.text().then(
                  (text) => replace({ kind: fileKind, text, source: file.name }),
                  (error: unknown) => toast.error(error, t('modeler.openFailed')),
                );
              }}
            />
            <Button size="sm" icon={Download} disabled={!model} onClick={() => void download()}>
              {t('modeler.download')}
            </Button>
            <Button size="sm" variant="primary" icon={Rocket} disabled={!model || (fromDeployed && !sourceTenant.isSuccess)} onClick={() => setDeploying(true)}>
              {t('modeler.deploy')}
            </Button>
          </div>
        }
      />
      {/* without the tenant of the deployed model it cannot be deployed again; say why and offer a retry */}
      {fromDeployed && sourceTenant.isError && (
        <ErrorState error={sourceTenant.error} onRetry={() => void sourceTenant.refetch()} />
      )}
      {recovered && !dirty && (
        <div className="notice notice--info notice--actions form-draft" role="status">
          <span className="notice__body">{t('modeler.draft', { time: relative(new Date(recovered.savedAt)) })}</span>
          <span className="notice__actions">
            <button
              type="button"
              className="link"
              onClick={() => {
                const { tenantId, ...restored } = recovered.values;
                setRestoredTenant(tenantId ?? null);
                setOpened(restored);
                setRecovered(null);
                setDirty(true);
                if (params.get('kind') !== restored.kind) setParams({ kind: restored.kind }, { replace: true });
              }}
            >
              {t('modeler.draft.restore')}
            </button>
            <button type="button" className="link" onClick={forgetDraft}>
              {t('form.draft.discard')}
            </button>
          </span>
        </div>
      )}
      <Card flush>
        {deployed.isError && !opened ? (
          <ErrorState error={deployed.error} onRetry={() => void deployed.refetch()} />
        ) : !model ? (
          <Loading label={t('diagram.loading')} />
        ) : (
          <Suspense fallback={<Loading label={t('diagram.loading')} />}>
            <Editor
              key={`${model.kind}|${model.source}|${model.text.length}|${model.text.slice(-64)}`}
              ref={editor}
              source={model.text}
              label={t('modeler.canvas')}
              onChange={() => {
                setDirty(true);
                setRevision((value) => value + 1);
              }}
              onReady={() => setLoaded((value) => value + 1)}
            />
          </Suspense>
        )}
        {model && loaded > 0 && (
          <ModelStatus key={loaded} kind={kind} dirty={dirty} editor={editor} revision={revision} />
        )}
      </Card>
      {deploying && model && (
        <DeployDialog
          onClose={() => setDeploying(false)}
          editor={editor}
          kind={kind}
          initialName={KINDS[kind].idOf(model.text) ?? kind}
          tenantId={draftTenant}
          onDeployed={(startedPage) => {
            setDirty(false);
            forgetDraft();
            if (startedPage) setGoTo(startedPage);
          }}
        />
      )}
    </div>
  );
}
