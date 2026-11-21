/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { MessageKey } from '../i18n';
import type { ModelKind } from './kinds';

const BPMN = 'http://www.omg.org/spec/BPMN/20100524/MODEL';
const CAMUNDA = 'http://camunda.org/schema/1.0/bpmn';
const CAMUNDA_DMN = 'http://camunda.org/schema/1.0/dmn';

/** One finding before a deployment: an error stops it, a warning is worth a look. */
export interface Finding {
  level: 'error' | 'warning';
  message: MessageKey;
  /** the element concerned, by its name or id */
  element?: string;
}

const label = (element: Element) => element.getAttribute('name')?.trim() || element.getAttribute('id') || element.localName;

function parse(text: string): Document | null {
  const document = new DOMParser().parseFromString(text, 'application/xml');
  return document.getElementsByTagName('parsererror').length > 0 ? null : document;
}

function children(parent: Element, name: string): Element[] {
  return Array.from(parent.children).filter((child) => child.namespaceURI === BPMN && child.localName === name);
}

const camunda = (element: Element, name: string) => element.getAttributeNS(CAMUNDA, name)?.trim() ?? '';

/** Whether a task that runs code says how; the engine refuses one that does not. */
function implemented(element: Element): boolean {
  return (
    !!camunda(element, 'class') ||
    !!camunda(element, 'delegateExpression') ||
    !!camunda(element, 'expression') ||
    // external, mail and shell tasks are configured by their type
    !!camunda(element, 'type') ||
    element.getElementsByTagNameNS(CAMUNDA, 'connector').length > 0
  );
}

/**
 * The earlier user tasks a user task names in its four-eyes property (`fasti:fourEyes`), or
 * `null` without one.
 */
function fourEyesSteps(task: Element): string[] | null {
  // the properties of the task itself, not those of its form fields
  const direct = (parent: Element, uri: string, name: string) =>
    Array.from(parent.children).filter((child) => child.namespaceURI === uri && child.localName === name);
  const properties = children(task, 'extensionElements')
    .flatMap((extensions) => direct(extensions, CAMUNDA, 'properties'))
    .flatMap((container) => direct(container, CAMUNDA, 'property'))
    .filter((property) => property.getAttribute('name') === 'fasti:fourEyes');
  if (properties.length === 0) return null;
  return properties.flatMap((property) =>
    (property.getAttribute('value') ?? '')
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean),
  );
}

/** The steps of a process or sub process that sequence flows connect. */
const STEP = /(Task|task|Gateway|Event|subProcess|callActivity|transaction)$/;

function checkFlows(scope: Element, findings: Finding[]): void {
  const flows = children(scope, 'sequenceFlow');
  const sources = new Set(flows.map((flow) => flow.getAttribute('sourceRef')));
  const targets = new Set(flows.map((flow) => flow.getAttribute('targetRef')));
  for (const step of Array.from(scope.children)) {
    if (step.namespaceURI !== BPMN || !STEP.test(step.localName) || step.localName === 'boundaryEvent') continue;
    const id = step.getAttribute('id');
    const eventSubProcess = step.getAttribute('triggeredByEvent') === 'true';
    // a step nothing leads to, or one that leads nowhere, is mostly an unfinished drawing
    const unreached = step.localName !== 'startEvent' && !eventSubProcess && !targets.has(id);
    const deadEnd = step.localName !== 'endEvent' && !eventSubProcess && !sources.has(id);
    if (unreached || deadEnd) {
      findings.push({ level: 'warning', message: 'readiness.unconnected', element: label(step) });
    }
    if (step.localName === 'subProcess' || step.localName === 'transaction') {
      checkFlows(step, findings);
    }
  }
}

/** Process models: what the engine would refuse and what would surprise at run time. */
function checkBpmn(text: string): Finding[] {
  const document = parse(text);
  if (!document) return [{ level: 'error', message: 'readiness.invalidXml' }];
  const findings: Finding[] = [];
  const executable = Array.from(document.getElementsByTagNameNS(BPMN, 'process')).filter(
    (process) => process.getAttribute('isExecutable') === 'true',
  );
  if (executable.length === 0) {
    findings.push({ level: 'error', message: 'readiness.notExecutable' });
  }
  for (const process of executable) {
    const name = label(process);
    if (!process.getAttribute('name')?.trim()) {
      findings.push({ level: 'warning', message: 'readiness.noName', element: name });
    }
    if (!camunda(process, 'historyTimeToLive')) {
      findings.push({ level: 'warning', message: 'readiness.noTimeToLive', element: name });
    }
    if (children(process, 'startEvent').length === 0) {
      findings.push({ level: 'error', message: 'readiness.noStart', element: name });
    }
    if (children(process, 'endEvent').length === 0) {
      findings.push({ level: 'warning', message: 'readiness.noEnd', element: name });
    }
    const flows = Array.from(process.getElementsByTagNameNS(BPMN, 'sequenceFlow'));
    const userTasks = new Set(
      Array.from(process.getElementsByTagNameNS(BPMN, 'userTask')).map((task) => task.getAttribute('id')),
    );
    for (const element of Array.from(process.getElementsByTagNameNS(BPMN, '*'))) {
      const kind = element.localName;
      const id = element.getAttribute('id');
      if (kind === 'serviceTask' || kind === 'sendTask') {
        if (!implemented(element)) {
          findings.push({ level: 'error', message: 'readiness.noImplementation', element: label(element) });
        } else if (camunda(element, 'type') === 'external' && !camunda(element, 'topic')) {
          findings.push({ level: 'error', message: 'readiness.noTopic', element: label(element) });
        }
      } else if (kind === 'businessRuleTask') {
        if (!camunda(element, 'decisionRef') && !implemented(element)) {
          findings.push({ level: 'error', message: 'readiness.noDecision', element: label(element) });
        } else if (camunda(element, 'type') === 'external' && !camunda(element, 'topic')) {
          findings.push({ level: 'error', message: 'readiness.noTopic', element: label(element) });
        }
      } else if (kind === 'userTask') {
        if (!camunda(element, 'assignee') && !camunda(element, 'candidateUsers') && !camunda(element, 'candidateGroups')) {
          findings.push({ level: 'warning', message: 'readiness.noAssignee', element: label(element) });
        }
        // the engine refuses a four-eyes rule that names anything but user tasks of the process
        const steps = fourEyesSteps(element);
        if (steps && (steps.length === 0 || steps.some((step) => !userTasks.has(step)))) {
          findings.push({ level: 'error', message: 'readiness.fourEyesSteps', element: label(element) });
        }
      } else if (kind === 'exclusiveGateway') {
        // the rules the engine applies when it parses an exclusive gateway
        const outgoing = flows.filter((flow) => flow.getAttribute('sourceRef') === id);
        const fallback = element.getAttribute('default');
        const conditional = (flow: Element) => flow.getElementsByTagNameNS(BPMN, 'conditionExpression').length > 0;
        const problem =
          outgoing.length === 0
            ? 'readiness.gatewayNoWay'
            : outgoing.length === 1
              ? conditional(outgoing[0] as Element)
                ? 'readiness.gatewaySingleCondition'
                : null
              : outgoing.some((flow) => flow.getAttribute('id') === fallback && conditional(flow))
                ? 'readiness.gatewayDefaultCondition'
                : outgoing.some((flow) => flow.getAttribute('id') !== fallback && !conditional(flow))
                  ? 'readiness.noCondition'
                  : null;
        if (problem) {
          findings.push({ level: 'error', message: problem, element: label(element) });
        }
      }
    }
    checkFlows(process, findings);
  }
  return findings;
}

/** Decisions: there must be one; history needs a time to live. */
function checkDmn(text: string): Finding[] {
  const document = parse(text);
  if (!document) return [{ level: 'error', message: 'readiness.invalidXml' }];
  const decisions = Array.from(document.getElementsByTagName('*')).filter((element) => element.localName === 'decision');
  if (decisions.length === 0) return [{ level: 'error', message: 'readiness.noDecisions' }];
  return decisions
    .filter((decision) => !decision.getAttributeNS(CAMUNDA_DMN, 'historyTimeToLive')?.trim())
    .map((decision) => ({ level: 'warning', message: 'readiness.noTimeToLive', element: label(decision) }));
}

/** Forms: the engine finds a form by its id. */
function checkForm(text: string): Finding[] {
  try {
    const form = JSON.parse(text) as { id?: unknown; components?: unknown };
    const findings: Finding[] = [];
    if (typeof form.id !== 'string' || !form.id.trim()) {
      findings.push({ level: 'error', message: 'readiness.noFormId' });
    }
    if (!Array.isArray(form.components) || form.components.length === 0) {
      findings.push({ level: 'warning', message: 'readiness.emptyForm' });
    }
    return findings;
  } catch {
    return [{ level: 'error', message: 'readiness.invalidJson' }];
  }
}

/** What to look at before a model is deployed, errors first. */
export function checkReadiness(kind: ModelKind, text: string): Finding[] {
  const findings = kind === 'bpmn' ? checkBpmn(text) : kind === 'dmn' ? checkDmn(text) : checkForm(text);
  return [
    ...findings.filter((finding) => finding.level === 'error'),
    ...findings.filter((finding) => finding.level === 'warning'),
  ];
}
