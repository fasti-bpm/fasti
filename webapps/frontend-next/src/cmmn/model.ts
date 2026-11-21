/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// Reads what a case diagram shows from a CMMN 1.1 file: the shapes and edges of
// its diagram interchange (CMMNDI) and, for each shape, the element it draws.
// Only reading: the file is never changed or written.

/** CMMN 1.1 and CMMN 1.0, which the engine accepts as well. */
export const CMMN_MODELS = ['http://www.omg.org/spec/CMMN/20151109/MODEL', 'http://www.omg.org/spec/CMMN/20131201/MODEL'];

/** Elements of the model, whichever of the two versions it is written in. */
function modelElements(root: Document | Element, localName: string): Element[] {
  return CMMN_MODELS.flatMap((namespace) => Array.from(root.getElementsByTagNameNS(namespace, localName)));
}

/** Elements of the diagram interchange, found by their local name in any namespace. */
function diagramElements(root: Document | Element, localName: string): Element[] {
  return Array.from(root.getElementsByTagNameNS('*', localName));
}

export type ShapeKind =
  | 'casePlanModel'
  | 'stage'
  | 'planFragment'
  | 'humanTask'
  | 'processTask'
  | 'caseTask'
  | 'decisionTask'
  | 'task'
  | 'milestone'
  | 'eventListener'
  | 'timerEventListener'
  | 'userEventListener'
  | 'entryCriterion'
  | 'exitCriterion'
  | 'textAnnotation'
  | 'caseFileItem'
  | 'unknown';

const DEFINITION_KINDS = new Set<ShapeKind>([
  'stage',
  'planFragment',
  'humanTask',
  'processTask',
  'caseTask',
  'decisionTask',
  'task',
  'milestone',
  'eventListener',
  'timerEventListener',
  'userEventListener',
]);

export interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Point {
  x: number;
  y: number;
}

export interface DiagramShape {
  /** id of the drawn element: a plan item, a criterion, the case plan model, ... */
  id: string;
  kind: ShapeKind;
  name: string | null;
  bounds: Bounds;
  /** the plan item definition a plan item refers to, e.g. the human task */
  definitionId: string | null;
  /** a discretionary item of a planning table, drawn with a dashed border */
  discretionary: boolean;
  /** started by hand (manual activation rule) */
  manual: boolean;
  required: boolean;
  repeatable: boolean;
  /** a human task that does not wait for its task to be completed */
  nonBlocking: boolean;
  /** a stage or plan model whose content is shown */
  expanded: boolean;
}

export interface DiagramEdge {
  id: string;
  kind: 'connector' | 'association';
  points: Point[];
  /** the standard event of the source, e.g. complete, if the diagram shows it */
  label: string | null;
  labelBounds: Bounds | null;
}

export interface CaseDiagram {
  /** the key of the case (`case/@id`) */
  caseKey: string | null;
  caseName: string | null;
  shapes: DiagramShape[];
  edges: DiagramEdge[];
  /** the area all shapes and edges take up */
  box: Bounds;
}

const number = (element: Element | undefined | null, name: string): number => {
  const value = Number.parseFloat(element?.getAttribute(name) ?? '');
  return Number.isFinite(value) ? value : 0;
};

function boundsOf(element: Element): Bounds | null {
  const bounds = Array.from(element.children).find((child) => child.localName === 'Bounds');
  if (!bounds) return null;
  return { x: number(bounds, 'x'), y: number(bounds, 'y'), width: number(bounds, 'width'), height: number(bounds, 'height') };
}

function cleanName(value: string | null | undefined): string | null {
  const name = value?.replace(/\s+/g, ' ').trim();
  return name ? name : null;
}

function child(element: Element | undefined, localName: string): Element | undefined {
  return element ? Array.from(element.children).find((candidate) => candidate.localName === localName) : undefined;
}

/** Whether a rule (manual activation, required, repetition) applies: on the item, or by default on its definition. */
function hasRule(item: Element, definition: Element | undefined, rule: string): boolean {
  const control = child(item, 'itemControl');
  if (control) return !!child(control, rule);
  return !!child(child(definition, 'defaultControl'), rule);
}

function kindOf(element: Element): ShapeKind {
  const name = element.localName as ShapeKind;
  if (DEFINITION_KINDS.has(name) || name === 'entryCriterion' || name === 'exitCriterion' || name === 'textAnnotation' || name === 'caseFileItem' || name === 'casePlanModel') {
    return name;
  }
  return 'unknown';
}

const emptyDiagram = (): CaseDiagram => ({ caseKey: null, caseName: null, shapes: [], edges: [], box: { x: 0, y: 0, width: 0, height: 0 } });

/**
 * The diagram of a case. In a file with several cases, the diagram that draws
 * the plan model of the case with the given key is taken; otherwise the first
 * one. A file without diagram interchange yields no shapes.
 */
export function parseCaseDiagram(xml: string, caseKey?: string | null): CaseDiagram {
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  if (document.getElementsByTagName('parsererror').length > 0) {
    throw new Error('The case model could not be read');
  }
  const elements = new Map<string, Element>();
  for (const element of modelElements(document, '*')) {
    const id = element.getAttribute('id');
    if (id) elements.set(id, element);
  }
  const cases = modelElements(document, 'case');
  const theCase = cases.find((candidate) => candidate.getAttribute('id') === caseKey) ?? cases[0];
  const planModel = theCase ? child(theCase, 'casePlanModel') : undefined;
  const diagrams = diagramElements(document, 'CMMNDiagram');
  const planModelId = planModel?.getAttribute('id');
  const diagram =
    diagrams.find((candidate) =>
      diagramElements(candidate, 'CMMNShape').some(
        (shape) => shape.getAttribute('cmmnElementRef') === planModelId,
      ),
    ) ?? diagrams[0];
  const result = emptyDiagram();
  result.caseKey = theCase?.getAttribute('id') ?? null;
  result.caseName = cleanName(theCase?.getAttribute('name'));
  if (!diagram) return result;

  for (const shape of diagramElements(diagram, 'CMMNShape')) {
    const ref = shape.getAttribute('cmmnElementRef') ?? '';
    const element = elements.get(ref);
    const bounds = boundsOf(shape);
    if (!element || !bounds) continue;
    const isItem = element.localName === 'planItem' || element.localName === 'discretionaryItem';
    const definitionId = isItem ? element.getAttribute('definitionRef') : null;
    const definition = definitionId ? elements.get(definitionId) : undefined;
    const kind = isItem ? (definition ? kindOf(definition) : 'unknown') : kindOf(element);
    if (kind === 'unknown') continue;
    const name =
      kind === 'textAnnotation'
        ? cleanName(child(element, 'text')?.textContent)
        : kind === 'casePlanModel'
          ? cleanName(element.getAttribute('name')) ?? result.caseName
          : cleanName(element.getAttribute('name')) ?? cleanName(definition?.getAttribute('name'));
    result.shapes.push({
      id: ref,
      kind,
      name,
      bounds,
      definitionId,
      discretionary: element.localName === 'discretionaryItem',
      manual: isItem && hasRule(element, definition, 'manualActivationRule'),
      required: isItem && hasRule(element, definition, 'requiredRule'),
      repeatable: isItem && hasRule(element, definition, 'repetitionRule'),
      nonBlocking: kind === 'humanTask' && definition?.getAttribute('isBlocking') === 'false',
      expanded: shape.getAttribute('isCollapsed') !== 'true',
    });
  }

  for (const edge of diagramElements(diagram, 'CMMNEdge')) {
    const points = diagramElements(edge, 'waypoint').map((point) => ({
      x: number(point, 'x'),
      y: number(point, 'y'),
    }));
    if (points.length < 2) continue;
    const ref = edge.getAttribute('cmmnElementRef') ?? '';
    const element = elements.get(ref);
    const event = cleanName(child(element, 'standardEvent')?.textContent);
    const label = child(edge, 'CMMNLabel');
    result.edges.push({
      id: edge.getAttribute('id') ?? ref,
      kind: element?.localName === 'association' ? 'association' : 'connector',
      points,
      label: edge.getAttribute('isStandardEventVisible') === 'true' ? event : null,
      labelBounds: label ? boundsOf(label) : null,
    });
  }

  result.box = boxOf(result);
  return result;
}

function boxOf(diagram: CaseDiagram): Bounds {
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  const add = (x: number, y: number) => {
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
  };
  for (const shape of diagram.shapes) {
    // the tab with the name of the plan model is drawn above its border
    const top = shape.kind === 'casePlanModel' ? shape.bounds.y - TAB_HEIGHT : shape.bounds.y;
    add(shape.bounds.x, top);
    add(shape.bounds.x + shape.bounds.width, shape.bounds.y + shape.bounds.height);
  }
  for (const edge of diagram.edges) {
    for (const point of edge.points) add(point.x, point.y);
  }
  if (!Number.isFinite(minX)) return { x: 0, y: 0, width: 0, height: 0 };
  return { x: minX, y: minY, width: maxX - minX, height: maxY - minY };
}

/** Height of the tab that carries the name of the case plan model. */
export const TAB_HEIGHT = 24;

/**
 * The plan items that draw a definition, e.g. the plan item of the human task
 * a task belongs to (its task definition key is the id of the human task).
 */
export function planItemsOf(diagram: CaseDiagram, elementId: string | null | undefined): string[] {
  if (!elementId) return [];
  return diagram.shapes
    .filter((shape) => shape.id === elementId || shape.definitionId === elementId)
    .map((shape) => shape.id);
}

/** Whether a shape stands for a plan item that has instances of its own. */
export function isPlanItem(shape: Pick<DiagramShape, 'kind'>): boolean {
  return DEFINITION_KINDS.has(shape.kind);
}

/**
 * Splits a name into lines that fit a width, by an estimate of the width of
 * the characters: wide scripts (CJK) count double. Words longer than a line
 * are cut; lines beyond `maxLines` end with an ellipsis.
 */
export function wrapText(text: string, maxWidth: number, fontSize: number, maxLines = 4): string[] {
  const widthOf = (value: string) =>
    Array.from(value).reduce((sum, char) => sum + (/[ᄀ-ᇿ⺀-꓏가-힣豈-﫿＀-￯]/u.test(char) ? 1 : 0.58), 0) * fontSize;
  const lines: string[] = [];
  let line = '';
  const push = (value: string) => {
    if (value) lines.push(value);
  };
  // scripts without spaces between words (CJK, Thai) break anywhere
  const words = /\s/.test(text) ? text.split(/\s+/) : Array.from(text);
  const joiner = /\s/.test(text) ? ' ' : '';
  for (const word of words) {
    const next = line ? `${line}${joiner}${word}` : word;
    if (widthOf(next) <= maxWidth || !line) {
      line = next;
      // a single word wider than the line is cut into pieces
      while (widthOf(line) > maxWidth && Array.from(line).length > 1) {
        const chars = Array.from(line);
        let cut = chars.length - 1;
        while (cut > 1 && widthOf(chars.slice(0, cut).join('')) > maxWidth) cut--;
        push(chars.slice(0, cut).join(''));
        line = chars.slice(cut).join('');
      }
    } else {
      push(line);
      line = word;
    }
  }
  push(line);
  if (lines.length > maxLines) {
    const kept = lines.slice(0, maxLines);
    kept[maxLines - 1] = `${kept[maxLines - 1]?.replace(/.$/u, '') ?? ''}…`;
    return kept;
  }
  return lines;
}
