/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { FormTarget } from '../../api/types';
import { applicationResourcePath } from '../resolve';
import type { ResolvedForm } from '../types';

/*
 * The broker of embedded forms. In the isolated modes the form frame has no
 * session: it has an opaque origin (sandboxed) or another origin (separate
 * origin). Whatever a form needs from the REST API, the frame asks this
 * application for, which performs the request with the session of the user
 * after checking it against this allow-list. The allow-list is tied to the
 * task or start event the form is shown for: a form can read and complete
 * its own task, read the variables of its own process instance and start
 * its own process, and nothing else.
 */

export interface BrokerContext {
  /** context path plus application path, no trailing slash */
  appRoot: string;
  engine: string;
  /** origin of this application */
  origin: string;
  target: FormTarget;
  /** same-origin path of the form resource, if the form is served from this origin */
  formPath: string | null;
  /** the process instance and definition of the task, if known */
  processInstanceId?: string | null;
  processDefinitionId?: string | null;
  /** the case instance and definition of a task of a case (CMMN), if known */
  caseInstanceId?: string | null;
  caseDefinitionId?: string | null;
}

export type BrokerMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export interface BrokerRequest {
  method: string;
  url: string;
}

export type BrokerDecision =
  /** a request to the REST API, `path` below `{appRoot}/api` */
  | { allowed: true; kind: 'api'; method: BrokerMethod; path: string; search: string }
  /** the form resource itself */
  | { allowed: true; kind: 'resource'; method: 'GET'; path: string; search: string }
  | { allowed: false; reason: string };

const TASK = Symbol('task');
const INSTANCE = Symbol('processInstance');
const DEFINITION = Symbol('processDefinition');
const CASE_INSTANCE = Symbol('caseInstance');
const CASE_DEFINITION = Symbol('caseDefinition');
const ANY = Symbol('any');
type Token =
  | string
  | typeof TASK
  | typeof INSTANCE
  | typeof DEFINITION
  | typeof CASE_INSTANCE
  | typeof CASE_DEFINITION
  | typeof ANY;

type Rule = readonly [BrokerMethod, readonly Token[]];

const TASK_RULES: readonly Rule[] = [
  ['GET', ['task', TASK]],
  ['GET', ['task', TASK, 'form']],
  ['GET', ['task', TASK, 'form-variables']],
  ['GET', ['task', TASK, 'deployed-form']],
  ['GET', ['task', TASK, 'rendered-form']],
  ['GET', ['task', TASK, 'variables']],
  ['GET', ['task', TASK, 'variables', ANY]],
  ['GET', ['task', TASK, 'variables', ANY, 'data']],
  ['POST', ['task', TASK, 'variables']],
  ['PUT', ['task', TASK, 'variables', ANY]],
  ['GET', ['task', TASK, 'localVariables']],
  ['GET', ['task', TASK, 'localVariables', ANY]],
  ['GET', ['task', TASK, 'localVariables', ANY, 'data']],
  ['POST', ['task', TASK, 'localVariables']],
  ['PUT', ['task', TASK, 'localVariables', ANY]],
  ['POST', ['task', TASK, 'submit-form']],
  ['POST', ['task', TASK, 'complete']],
  ['POST', ['task', TASK, 'bpmnError']],
  ['POST', ['task', TASK, 'bpmnEscalation']],
  ['GET', ['process-instance', INSTANCE]],
  ['GET', ['process-instance', INSTANCE, 'variables']],
  ['GET', ['process-instance', INSTANCE, 'variables', ANY]],
  ['GET', ['process-instance', INSTANCE, 'variables', ANY, 'data']],
  ['GET', ['process-definition', DEFINITION]],
  ['GET', ['process-definition', DEFINITION, 'xml']],
  // a task of a case reads its case instance and definition, as the forms SDK does
  ['GET', ['case-instance', CASE_INSTANCE]],
  ['GET', ['case-instance', CASE_INSTANCE, 'variables']],
  ['GET', ['case-instance', CASE_INSTANCE, 'variables', ANY]],
  ['GET', ['case-instance', CASE_INSTANCE, 'variables', ANY, 'data']],
  ['GET', ['case-definition', CASE_DEFINITION]],
  ['GET', ['case-definition', CASE_DEFINITION, 'xml']],
];

const START_RULES: readonly Rule[] = [
  ['GET', ['process-definition', DEFINITION]],
  ['GET', ['process-definition', DEFINITION, 'xml']],
  ['GET', ['process-definition', DEFINITION, 'startForm']],
  ['GET', ['process-definition', DEFINITION, 'form-variables']],
  ['GET', ['process-definition', DEFINITION, 'deployed-start-form']],
  ['GET', ['process-definition', DEFINITION, 'rendered-form']],
  ['POST', ['process-definition', DEFINITION, 'submit-form']],
  ['POST', ['process-definition', DEFINITION, 'start']],
];

const METHODS = new Set<string>(['GET', 'POST', 'PUT', 'DELETE']);

function deny(reason: string): BrokerDecision {
  return { allowed: false, reason };
}

/**
 * Splits a path into decoded segments. Returns null for paths that could be
 * read differently by the server than by this check: encoded slashes or
 * backslashes, dot segments, empty segments, path parameters, invalid
 * escapes, control characters.
 */
export function pathSegments(pathname: string): string[] | null {
  if (!pathname.startsWith('/')) {
    return null;
  }
  if (pathname === '/') {
    return [];
  }
  const raw = pathname.substring(1).split('/');
  const segments: string[] = [];
  for (const [index, part] of raw.entries()) {
    if (part === '' && index === raw.length - 1 && index > 0) {
      // a trailing slash
      continue;
    }
    let decoded: string;
    try {
      decoded = decodeURIComponent(part);
    } catch {
      return null;
    }
    if (
      decoded === '' ||
      decoded === '.' ||
      decoded === '..' ||
      decoded.includes('/') ||
      decoded.includes('\\') ||
      decoded.includes(';') ||
      // eslint-disable-next-line no-control-regex
      /[\u0000-\u001f\u007f]/.test(decoded)
    ) {
      return null;
    }
    segments.push(decoded);
  }
  return segments;
}

function startsWith(segments: string[], prefix: string[]): boolean {
  return prefix.length <= segments.length && prefix.every((part, i) => segments[i] === part);
}

function matches(rule: readonly Token[], rest: string[], context: BrokerContext): boolean {
  if (rule.length !== rest.length) {
    return false;
  }
  const target = context.target;
  return rule.every((token, i) => {
    const segment = rest[i];
    switch (token) {
      case TASK:
        return target.type === 'task' && segment === target.taskId;
      case INSTANCE:
        return !!context.processInstanceId && segment === context.processInstanceId;
      case CASE_INSTANCE:
        return target.type === 'task' && !!context.caseInstanceId && segment === context.caseInstanceId;
      case CASE_DEFINITION:
        return target.type === 'task' && !!context.caseDefinitionId && segment === context.caseDefinitionId;
      case DEFINITION:
        return target.type === 'start'
          ? segment === target.processDefinitionId
          : !!context.processDefinitionId && segment === context.processDefinitionId;
      case ANY:
        return true;
      default:
        return segment === token;
    }
  });
}

/**
 * Decides whether a request of the form frame may be performed with the
 * session of the user. Everything that is not explicitly allowed is refused.
 */
export function authorize(context: BrokerContext, request: BrokerRequest): BrokerDecision {
  const method = typeof request.method === 'string' ? request.method.toUpperCase() : '';
  if (!METHODS.has(method)) {
    return deny(`method ${String(request.method).slice(0, 20)} is not allowed`);
  }
  if (typeof request.url !== 'string' || request.url.length > 8192) {
    return deny('invalid URL');
  }
  let url: URL;
  try {
    url = new URL(request.url, context.origin);
  } catch {
    return deny('invalid URL');
  }
  if (url.origin !== context.origin || url.username || url.password) {
    return deny('only requests to this origin are brokered');
  }
  const segments = pathSegments(url.pathname);
  const root = pathSegments(context.appRoot === '' ? '/' : context.appRoot);
  if (!segments || !root) {
    return deny('ambiguous path');
  }

  // the form resource of a process application, e.g. /invoice/forms/a.html;
  // a form key that points into the REST API gets no exception: deployed and
  // rendered forms are covered by the rules of the task or start event below.
  // The query is dropped, so that a static resource cannot be turned into a
  // query of another endpoint.
  if (context.formPath && method === 'GET' && !startsWith(segments, [...root, 'api'])) {
    const formSegments = pathSegments(context.formPath);
    if (formSegments && formSegments.join('/') === segments.join('/')) {
      return { allowed: true, kind: 'resource', method: 'GET', path: url.pathname, search: '' };
    }
  }

  if (!startsWith(segments, root)) {
    return deny('not a request to the REST API');
  }
  const rest = segments.slice(root.length);

  // the configuration and custom scripts of the classic Tasklist, which the
  // frame loads like the classic Tasklist did; static files of this origin
  if (
    method === 'GET' &&
    rest.length >= 3 &&
    startsWith(rest, ['app', 'tasklist']) &&
    rest[2] !== context.engine &&
    /\.js$/.test(rest[rest.length - 1] ?? '')
  ) {
    return { allowed: true, kind: 'resource', method: 'GET', path: url.pathname, search: '' };
  }

  // the user of the session, which the forms SDK passes to the form
  if (
    method === 'GET' &&
    rest.length === 5 &&
    startsWith(rest, ['api', 'admin', 'auth', 'user']) &&
    rest[4] === context.engine
  ) {
    return {
      allowed: true,
      kind: 'api',
      method: 'GET',
      path: `/admin/auth/user/${encodeURIComponent(context.engine)}`,
      search: '',
    };
  }

  const enginePrefix = ['api', 'engine', 'engine', context.engine];
  if (!startsWith(rest, enginePrefix)) {
    return deny('not a request to the engine of this form');
  }
  const resource = rest.slice(enginePrefix.length);
  const rules = context.target.type === 'task' ? TASK_RULES : START_RULES;
  const allowed = rules.some(
    ([ruleMethod, tokens]) => ruleMethod === method && matches(tokens, resource, context),
  );
  if (!allowed) {
    return deny('not allowed for the task or start event of this form');
  }
  return {
    allowed: true,
    kind: 'api',
    method: method as BrokerMethod,
    path: '/' + rest.slice(1).map(encodeURIComponent).join('/'),
    search: url.search,
  };
}

/**
 * Decides whether a file of a variable may be downloaded for the form: only
 * `GET .../variables/{name}/data` of the own task or process instance.
 */
export function authorizeDownload(context: BrokerContext, url: string): BrokerDecision {
  const decision = authorize(context, { method: 'GET', url });
  if (!decision.allowed) {
    return decision;
  }
  if (decision.kind !== 'api' || !/\/(variables|localVariables)\/[^/]+\/data$/.test(decision.path)) {
    return deny('not a file of a variable');
  }
  return decision;
}

/**
 * The path of the form resource the frame will load, determined like the
 * frame does (frontend/ui/forms/client/scripts/fasti-form-frame.js), or null
 * if the form is not served from this origin.
 */
export function formResourcePath(
  form: ResolvedForm,
  target: FormTarget,
  appRoot: string,
  engine: string,
  origin: string,
): string | null {
  const key = form.info.key;
  if (!key?.startsWith('embedded:')) {
    return null;
  }
  const rest = key.substring('embedded:'.length);
  const engineBase = `${appRoot}/api/engine/engine/${encodeURIComponent(engine)}`;
  if (rest.startsWith('app:')) {
    return applicationResourcePath(form.info.contextPath, rest.substring('app:'.length));
  }
  if (rest.startsWith('deployment:')) {
    return target.type === 'task'
      ? `${engineBase}/task/${encodeURIComponent(target.taskId)}/deployed-form`
      : `${engineBase}/process-definition/${encodeURIComponent(target.processDefinitionId)}/deployed-start-form`;
  }
  let resolved = rest;
  if (rest.startsWith('engine:')) {
    resolved = rest
      .replace('engine://', `${appRoot}/api/engine/`)
      .replace(':engine', encodeURIComponent(engine));
  }
  try {
    const url = new URL(resolved, origin);
    return url.origin === origin ? url.pathname : null;
  } catch {
    return null;
  }
}

/** The key of the local storage the forms SDK uses for the entered values of a form. */
export function storageKey(target: FormTarget): string {
  return `camForm:${target.type === 'task' ? target.taskId : target.processDefinitionId}`;
}

/** The prefix under which this application keeps the entered values of isolated forms. */
export const STORAGE_PREFIX = 'fasti.embeddedForm.';

/**
 * The prefix of the entered values of the given user on the given engine:
 * drafts are never shared between users or engines of one browser.
 */
export function draftPrefix(engine: string, userId: string): string {
  return `${STORAGE_PREFIX}${encodeURIComponent(engine)}/${encodeURIComponent(userId)}/`;
}

/** The local storage key of the entered values of a form of the given user. */
export function draftKey(engine: string, userId: string, target: FormTarget): string {
  return draftPrefix(engine, userId) + storageKey(target);
}

/**
 * Removes the stored entered values of the given user on the given engine, and
 * those of earlier versions that were not tied to a user.
 */
export function clearDrafts(engine: string, userId: string, storage: Storage = window.localStorage): void {
  const prefix = draftPrefix(engine, userId);
  const legacy = `${STORAGE_PREFIX}camForm:`;
  const keys: string[] = [];
  for (let i = 0; i < storage.length; i++) {
    const key = storage.key(i);
    if (key && (key.startsWith(prefix) || key.startsWith(legacy))) {
      keys.push(key);
    }
  }
  keys.forEach((key) => storage.removeItem(key));
}

/** Upper bound of the entered values of one form that are kept. */
export const MAX_STORED_VALUE = 512 * 1024;
