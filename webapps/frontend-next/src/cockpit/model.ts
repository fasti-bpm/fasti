/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/** An element of a process a token can be at: an activity, an event or a gateway. */
export interface FlowNode {
  id: string;
  name: string;
  /** local name of the element, e.g. `userTask` */
  type: string;
  /** the none start event of the executable process, where a new instance begins */
  initial?: boolean;
}

const FLOW_NODE =
  /^(task|userTask|serviceTask|scriptTask|sendTask|receiveTask|manualTask|businessRuleTask|callActivity|subProcess|transaction|adHocSubProcess|startEvent|endEvent|intermediateCatchEvent|intermediateThrowEvent|boundaryEvent|exclusiveGateway|parallelGateway|inclusiveGateway|eventBasedGateway|complexGateway)$/;

/** The start event without a trigger directly in the (first executable) process, if there is one. */
function initialStartEvent(model: Document): Element | undefined {
  const processes = Array.from(model.getElementsByTagName('*')).filter((element) => element.localName === 'process');
  const process = processes.find((element) => element.getAttribute('isExecutable') !== 'false') ?? processes[0];
  return Array.from(process?.children ?? []).find(
    (child) => child.localName === 'startEvent' && !Array.from(child.children).some((inner) => inner.localName.endsWith('EventDefinition')),
  );
}

/** The flow nodes of a model in document order; the model is only read, never shown. */
export function flowNodes(xml: string | undefined): FlowNode[] {
  if (!xml) {
    return [];
  }
  try {
    const model = new DOMParser().parseFromString(xml, 'application/xml');
    const initial = initialStartEvent(model);
    return Array.from(model.getElementsByTagName('*'))
      .filter((element) => FLOW_NODE.test(element.localName) && element.getAttribute('id'))
      .map((element) => {
        const id = element.getAttribute('id') ?? '';
        const name = element.getAttribute('name')?.replace(/\s+/g, ' ').trim();
        return { id, name: name || id, type: element.localName, ...(element === initial ? { initial: true } : {}) };
      });
  } catch {
    return [];
  }
}

/**
 * Splits a duration in milliseconds into a short text such as `2 days 3 hr` or `850 ms`, with the
 * unit names and digits of the given language.
 */
export function formatDuration(millis: number | null | undefined, locale = 'en'): string {
  if (millis === null || millis === undefined || Number.isNaN(millis)) {
    return '—';
  }
  const unit = (value: number, name: string) =>
    new Intl.NumberFormat(locale, { style: 'unit', unit: name, unitDisplay: 'short' }).format(value);
  if (millis < 1000) {
    return unit(Math.round(millis), 'millisecond');
  }
  const units: [number, string][] = [
    [86_400, 'day'],
    [3_600, 'hour'],
    [60, 'minute'],
    [1, 'second'],
  ];
  const parts: string[] = [];
  let rest = Math.round(millis / 1000);
  for (const [size, name] of units) {
    if (rest >= size) {
      parts.push(unit(Math.floor(rest / size), name));
      rest %= size;
    }
    if (parts.length === 2) break;
  }
  return parts.join(' ');
}

/** `12` is a number, `true` a boolean, `null` nothing, `"12"` and everything else text. */
export function parseConditionValue(text: string): string | number | boolean | null {
  const value = text.trim();
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (value === 'null') return null;
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value);
  const quoted = /^"(.*)"$/.exec(value);
  return quoted ? (quoted[1] ?? '') : value;
}
