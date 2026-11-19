/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
const BPMNDI = 'http://www.omg.org/spec/BPMN/20100524/DI';
const BPMN = 'http://www.omg.org/spec/BPMN/20100524/MODEL';

/**
 * Finds the diagram that draws the given process in a file with several
 * diagrams, either as its own plane or as a participant of a collaboration.
 * Without a match, the first diagram is drawn as before.
 */
/** The key in a definition id such as `invoice:2:…`; ids of very long keys hold none. */
export function keyOfDefinitionId(id: string | null | undefined): string | undefined {
  const parts = (id ?? '').split(':');
  return parts.length >= 3 ? parts[0] : undefined;
}

export function diagramFor(xml: string, processKey: string | null | undefined): string | undefined {
  if (!processKey || typeof DOMParser === 'undefined') {
    return undefined;
  }
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  const diagrams = Array.from(document.getElementsByTagNameNS(BPMNDI, 'BPMNDiagram'));
  if (diagrams.length < 2) {
    return undefined;
  }
  const collaborations = new Set(
    Array.from(document.getElementsByTagNameNS(BPMN, 'participant'))
      .filter((participant) => participant.getAttribute('processRef') === processKey)
      .map((participant) => participant.parentElement?.getAttribute('id'))
      .filter(Boolean),
  );
  const match = diagrams.find((diagram) => {
    const plane = diagram.getElementsByTagNameNS(BPMNDI, 'BPMNPlane')[0];
    const element = plane?.getAttribute('bpmnElement');
    return element === processKey || (element != null && collaborations.has(element));
  });
  return match?.getAttribute('id') ?? undefined;
}

/**
 * Colours of diagrams as references to the theme: shapes follow a change of
 * the theme at once, also the ones added later in an editor, and nothing of
 * the theme is written into a saved model.
 */
export const THEME_COLOURS = {
  defaultFillColor: 'var(--diagram-fill)',
  defaultStrokeColor: 'var(--diagram-stroke)',
  defaultLabelColor: 'var(--diagram-label)',
} as const;
