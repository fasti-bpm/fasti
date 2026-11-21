/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { KeyboardEvent, PointerEvent, ReactNode } from 'react';
import { Copy, ImageDown, Maximize, Minus, Plus } from 'lucide-react';
import { useI18n } from '../i18n';
import { ErrorState, IconButton, cx } from '../ui';
import { useToast } from '../ui/toast';
import { copyPng, diagramSvg, download, svgToPng } from '../bpmn/exportImage';
import { TAB_HEIGHT, isPlanItem, parseCaseDiagram, wrapText, type Bounds, type CaseDiagram, type DiagramEdge, type DiagramShape } from './model';
import '../bpmn/bpmn.css';
import './cmmn.css';

/** A class put on a shape, e.g. the state of its plan item. */
export interface ShapeMarker {
  id: string;
  marker: string;
}

/** A number put on a shape, e.g. how many instances wait there. */
export interface ShapeBadge {
  id: string;
  count: number;
}

export interface CmmnDiagramProps {
  xml: string;
  /** the key of the case to draw when the file holds several */
  caseKey?: string | null;
  markers?: ShapeMarker[];
  badges?: ShapeBadge[];
  /** plan items the page filters by or points to */
  selected?: string[];
  /** called with the id of a chosen plan item, `null` for the background */
  onSelect?: (id: string | null) => void;
  /** what the tooltip of a plan item says besides its name, e.g. its state */
  describe?: (id: string) => string | null;
  label?: string;
}

interface View {
  cx: number;
  cy: number;
  /** screen pixels per unit of the model */
  scale: number;
}

const MARGIN = 24;
const MAX_FIT_SCALE = 1.25;

function fitView(box: Bounds, width: number, height: number): View {
  const scale =
    width > 0 && height > 0 && box.width > 0 && box.height > 0
      ? Math.min(MAX_FIT_SCALE, (width - 2 * MARGIN) / box.width, (height - 2 * MARGIN) / box.height)
      : 1;
  return { cx: box.x + box.width / 2, cy: box.y + box.height / 2, scale: Math.max(scale, 0.05) };
}

// --- shapes -------------------------------------------------------------------

function Lines({ lines, x, y, size, anchor = 'middle' }: { lines: string[]; x: number; y: number; size: number; anchor?: 'start' | 'middle' }) {
  const height = size * 1.25;
  const top = y - ((lines.length - 1) * height) / 2;
  return (
    <text className="cmmn-text" fontSize={size} textAnchor={anchor} dominantBaseline="central">
      {lines.map((line, index) => (
        <tspan key={index} x={x} y={anchor === 'middle' ? top + index * height : y + index * height}>
          {line}
        </tspan>
      ))}
    </text>
  );
}

/** The small symbols at the bottom of a plan item: manual start, required, repeatable, the content of a stage. */
function Markers({ shape }: { shape: DiagramShape }) {
  const { x, y, width, height } = shape.bounds;
  const symbols: ((left: number, top: number) => ReactNode)[] = [];
  if (shape.manual) {
    symbols.push((left, top) => <path key="manual" className="cmmn-marker" d={`M ${left + 1} ${top} l 9 5 l -9 5 z`} />);
  }
  if (shape.required) {
    symbols.push((left, top) => (
      <text key="required" className="cmmn-marker-text" x={left + 5} y={top + 5} textAnchor="middle" dominantBaseline="central">
        !
      </text>
    ));
  }
  if (shape.repeatable) {
    symbols.push((left, top) => (
      <text key="repeatable" className="cmmn-marker-text" x={left + 5} y={top + 5} textAnchor="middle" dominantBaseline="central">
        #
      </text>
    ));
  }
  if (shape.kind === 'stage' || shape.kind === 'planFragment') {
    symbols.push((left, top) => (
      <g key="content">
        <rect className="cmmn-marker cmmn-marker--box" x={left} y={top} width={10} height={10} />
        <path className="cmmn-marker-line" d={`M ${left + 2} ${top + 5} h 6${shape.expanded ? '' : ` M ${left + 5} ${top + 2} v 6`}`} />
      </g>
    ));
  }
  if (symbols.length === 0) return null;
  const step = 14;
  const left = x + width / 2 - (symbols.length * step - 4) / 2;
  const top = y + height - 15;
  return <g aria-hidden>{symbols.map((draw, index) => draw(left + index * step, top))}</g>;
}

function TaskIcon({ shape }: { shape: DiagramShape }) {
  const left = shape.bounds.x + 8;
  const top = shape.bounds.y + 7;
  switch (shape.kind) {
    case 'humanTask':
      return (
        <g className="cmmn-icon" aria-hidden>
          <circle cx={left + 7} cy={top + 5} r={3.5} />
          <path d={`M ${left} ${top + 17} q 0 -7 7 -7 q 7 0 7 7 z`} />
          {shape.nonBlocking && <path d={`M ${left + 15} ${top + 4} h 4 m -2 -2 v 4`} />}
        </g>
      );
    case 'processTask':
      return <path className="cmmn-icon" aria-hidden d={`M ${left} ${top + 2} h 10 l 5 6 l -5 6 h -10 l 5 -6 z`} />;
    case 'caseTask':
      return <path className="cmmn-icon" aria-hidden d={`M ${left} ${top + 3} h 6 l 2 2 h 8 v 10 h -16 z`} />;
    case 'decisionTask':
      return (
        <g className="cmmn-icon" aria-hidden>
          <rect x={left} y={top + 2} width={16} height={12} />
          <path d={`M ${left} ${top + 6} h 16 M ${left + 5} ${top + 6} v 8`} />
        </g>
      );
    default:
      return null;
  }
}

function Outline({ shape }: { shape: DiagramShape }) {
  const { x, y, width: w, height: h } = shape.bounds;
  switch (shape.kind) {
    case 'casePlanModel': {
      return <rect className="cmmn-shape__border" x={x} y={y} width={w} height={h} />;
    }
    case 'stage':
    case 'planFragment': {
      const c = Math.min(10, w / 4, h / 4);
      return (
        <path
          className="cmmn-shape__border"
          d={`M ${x + c} ${y} H ${x + w - c} L ${x + w} ${y + c} V ${y + h - c} L ${x + w - c} ${y + h} H ${x + c} L ${x} ${y + h - c} V ${y + c} Z`}
        />
      );
    }
    case 'milestone':
      return <rect className="cmmn-shape__border" x={x} y={y} width={w} height={h} rx={h / 2} />;
    case 'eventListener':
    case 'timerEventListener':
    case 'userEventListener': {
      const r = Math.min(w, h) / 2;
      return (
        <>
          <circle className="cmmn-shape__border" cx={x + w / 2} cy={y + h / 2} r={r} />
          <circle className="cmmn-shape__inner" cx={x + w / 2} cy={y + h / 2} r={Math.max(r - 3, 1)} />
          {shape.kind === 'timerEventListener' && (
            <path className="cmmn-shape__inner" d={`M ${x + w / 2} ${y + h / 2 - r + 6} V ${y + h / 2} l ${r / 3} ${r / 4}`} />
          )}
        </>
      );
    }
    case 'entryCriterion':
    case 'exitCriterion':
      return (
        <path
          className={cx('cmmn-shape__border', shape.kind === 'exitCriterion' && 'cmmn-shape__border--filled')}
          d={`M ${x + w / 2} ${y} L ${x + w} ${y + h / 2} L ${x + w / 2} ${y + h} L ${x} ${y + h / 2} Z`}
        />
      );
    case 'textAnnotation':
      return <path className="cmmn-shape__line" d={`M ${x + 10} ${y} H ${x} V ${y + h} H ${x + 10}`} />;
    case 'caseFileItem': {
      const fold = Math.min(12, w / 3, h / 3);
      return (
        <path
          className="cmmn-shape__border"
          d={`M ${x} ${y} H ${x + w - fold} L ${x + w} ${y + fold} V ${y + h} H ${x} Z M ${x + w - fold} ${y} V ${y + fold} H ${x + w}`}
        />
      );
    }
    default:
      return <rect className="cmmn-shape__border" x={x} y={y} width={w} height={h} rx={10} />;
  }
}

function ShapeLabel({ shape }: { shape: DiagramShape }) {
  const { x, y, width: w, height: h } = shape.bounds;
  const name = shape.name;
  if (!name) return null;
  switch (shape.kind) {
    case 'casePlanModel':
      return null;
    case 'stage':
    case 'planFragment':
      return <Lines lines={wrapText(name, w - 24, 12, 2)} x={x + 12} y={y + 16} size={12} anchor="start" />;
    case 'entryCriterion':
    case 'exitCriterion':
    case 'eventListener':
    case 'timerEventListener':
    case 'userEventListener':
    case 'caseFileItem':
      return <Lines lines={wrapText(name, 120, 11, 2)} x={x + w / 2} y={y + h + 14} size={11} />;
    case 'textAnnotation':
      return <Lines lines={wrapText(name, w - 10, 11, Math.max(1, Math.floor(h / 14)))} x={x + 6} y={y + 12} size={11} anchor="start" />;
    default: {
      const lines = wrapText(name, w - 16, 12, Math.max(1, Math.floor((h - 18) / 15)));
      return <Lines lines={lines} x={x + w / 2} y={y + h / 2} size={12} />;
    }
  }
}

/** The tab with the name of the case plan model, above its border. */
function PlanModelTab({ shape }: { shape: DiagramShape }) {
  const { x, y, width } = shape.bounds;
  const name = shape.name ?? '';
  const lines = wrapText(name, Math.max(width - 40, 60), 12, 1);
  const tabWidth = Math.min(width, Math.max(90, (lines[0]?.length ?? 0) * 7 + 36));
  return (
    <g>
      <path className="cmmn-shape__border" d={`M ${x} ${y} L ${x + 10} ${y - TAB_HEIGHT} H ${x + tabWidth - 10} L ${x + tabWidth} ${y}`} />
      <Lines lines={lines} x={x + 18} y={y - TAB_HEIGHT / 2} size={12} anchor="start" />
    </g>
  );
}

function EdgeView({ edge }: { edge: DiagramEdge }) {
  const points = edge.points.map((point) => `${point.x},${point.y}`).join(' ');
  const middle = edge.points[Math.floor((edge.points.length - 1) / 2)];
  const next = edge.points[Math.floor((edge.points.length - 1) / 2) + 1];
  const labelAt = edge.labelBounds
    ? { x: edge.labelBounds.x + edge.labelBounds.width / 2, y: edge.labelBounds.y + edge.labelBounds.height / 2 }
    : middle && next
      ? { x: (middle.x + next.x) / 2, y: (middle.y + next.y) / 2 - 8 }
      : null;
  return (
    <g className={cx('cmmn-edge', `cmmn-edge--${edge.kind}`)} aria-hidden>
      <polyline points={points} />
      {edge.label && labelAt && (
        <text className="cmmn-text cmmn-edge__label" x={labelAt.x} y={labelAt.y} textAnchor="middle" dominantBaseline="central">
          {edge.label}
        </text>
      )}
    </g>
  );
}

function Badge({ shape, count, title }: { shape: DiagramShape; count: number; title: string }) {
  const text = count > 999 ? '999+' : String(count);
  const width = Math.max(20, text.length * 7 + 10);
  const x = shape.bounds.x - 8;
  const y = shape.bounds.y + shape.bounds.height - 9;
  return (
    <g className="cmmn-badge">
      <title>{title}</title>
      <rect x={x} y={y} width={width} height={18} rx={9} />
      <text x={x + width / 2} y={y + 9} textAnchor="middle" dominantBaseline="central">
        {text}
      </text>
    </g>
  );
}

// --- the diagram ----------------------------------------------------------------

/** Order of drawing: containers first, so that what they contain stays on top; criteria last. */
const LAYER: Partial<Record<DiagramShape['kind'], number>> = {
  casePlanModel: 0,
  stage: 1,
  planFragment: 1,
  entryCriterion: 3,
  exitCriterion: 3,
};

/** How many containers (plan model, stages, plan fragments) enclose a shape. */
function depthOf(shape: DiagramShape, containers: DiagramShape[]): number {
  const { x, y, width, height } = shape.bounds;
  return containers.filter(
    (other) =>
      other !== shape &&
      other.bounds.x <= x &&
      other.bounds.y <= y &&
      other.bounds.x + other.bounds.width >= x + width &&
      other.bounds.y + other.bounds.height >= y + height,
  ).length;
}

/** Containers first, outer before inner, so that a stage never covers what it contains; criteria last. */
export function drawingOrder(shapes: DiagramShape[]): DiagramShape[] {
  const containers = shapes.filter((shape) => (LAYER[shape.kind] ?? 2) <= 1);
  const key = (shape: DiagramShape) => {
    const layer = LAYER[shape.kind] ?? 2;
    return layer <= 1 ? depthOf(shape, containers) / (containers.length + 1) : layer;
  };
  return [...shapes].sort((a, b) => key(a) - key(b));
}

/**
 * Shows a case diagram, read only, drawn from the diagram interchange of the
 * model: plan items with the symbols of CMMN, their states as markers and
 * their numbers as badges. Plan items can be chosen with the mouse or the
 * keyboard when the page passes `onSelect`.
 */
export default function CmmnDiagram({ xml, caseKey, markers, badges, selected, onSelect, describe, label }: CmmnDiagramProps) {
  const { t } = useI18n();
  const toast = useToast();
  const container = useRef<HTMLDivElement>(null);
  const svg = useRef<SVGSVGElement>(null);
  const parsed = useMemo((): { diagram: CaseDiagram | null; error: unknown } => {
    try {
      return { diagram: parseCaseDiagram(xml, caseKey), error: null };
    } catch (error) {
      return { diagram: null, error };
    }
  }, [xml, caseKey]);
  const diagram = parsed.diagram;
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [view, setView] = useState<View | null>(null);
  const drag = useRef<{ x: number; y: number; view: View; moved: boolean } | null>(null);

  useEffect(() => {
    const element = container.current;
    if (!element) return;
    const measure = () => setSize({ width: element.clientWidth, height: element.clientHeight });
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  // a new model or a new size of the area fits the diagram again
  const box = diagram?.box;
  const fit = useCallback(() => {
    if (box) setView(fitView(box, size.width, size.height));
  }, [box, size.width, size.height]);
  useEffect(fit, [fit]);

  const classes = useMemo(() => {
    const byId = new Map<string, string[]>();
    for (const entry of markers ?? []) {
      byId.set(entry.id, [...(byId.get(entry.id) ?? []), entry.marker]);
    }
    for (const id of selected ?? []) {
      byId.set(id, [...(byId.get(id) ?? []), 'is-selected']);
    }
    return byId;
  }, [markers, selected]);
  const counts = useMemo(() => new Map((badges ?? []).filter((entry) => entry.count > 0).map((entry) => [entry.id, entry.count])), [badges]);

  const current = view ?? (box ? fitView(box, size.width, size.height) : { cx: 0, cy: 0, scale: 1 });
  const viewBox =
    size.width > 0 && size.height > 0
      ? [current.cx - size.width / 2 / current.scale, current.cy - size.height / 2 / current.scale, size.width / current.scale, size.height / current.scale]
      : box
        ? [box.x - MARGIN, box.y - MARGIN, box.width + 2 * MARGIN, box.height + 2 * MARGIN]
        : [0, 0, 100, 100];

  const shown = useRef(current);
  useEffect(() => {
    shown.current = current;
  });
  const zoom = useCallback(
    (factor: number) =>
      setView((before) => {
        const base = before ?? shown.current;
        return { ...base, scale: Math.min(4, Math.max(0.1, base.scale * factor)) };
      }),
    [],
  );
  // the page scrolls; with Ctrl or ⌘ the diagram zooms. A passive listener could not keep
  // the browser from zooming the whole page, so the listener is added here.
  useEffect(() => {
    const element = svg.current;
    if (!element) return;
    const onWheel = (event: globalThis.WheelEvent) => {
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      zoom(event.deltaY < 0 ? 1.1 : 1 / 1.1);
    };
    element.addEventListener('wheel', onWheel, { passive: false });
    return () => element.removeEventListener('wheel', onWheel);
  }, [zoom, diagram]);

  const onPointerDown = (event: PointerEvent<SVGSVGElement>) => {
    if (event.button !== 0) return;
    drag.current = { x: event.clientX, y: event.clientY, view: current, moved: false };
  };
  const onPointerMove = (event: PointerEvent<SVGSVGElement>) => {
    const start = drag.current;
    if (!start) return;
    const dx = event.clientX - start.x;
    const dy = event.clientY - start.y;
    if (!start.moved && Math.hypot(dx, dy) < 4) return;
    if (!start.moved) {
      start.moved = true;
      event.currentTarget.setPointerCapture?.(event.pointerId);
    }
    setView({ ...start.view, cx: start.view.cx - dx / start.view.scale, cy: start.view.cy - dy / start.view.scale });
  };
  const onPointerUp = (event: PointerEvent<SVGSVGElement>) => {
    const start = drag.current;
    drag.current = null;
    if (start?.moved) {
      event.currentTarget.releasePointerCapture?.(event.pointerId);
      return;
    }
    if (!onSelect) return;
    const target = (event.target as Element).closest?.('[data-element-id]');
    onSelect(target?.getAttribute('data-element-id') ?? null);
  };
  const onKey = (event: KeyboardEvent<SVGGElement>, id: string) => {
    if (onSelect && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      onSelect(id);
    }
  };

  const image = (): string | null => {
    if (!svg.current || !box) return null;
    const background = getComputedStyle(container.current as Element).getPropertyValue('--diagram-fill').trim() || '#ffffff';
    return diagramSvg(svg.current, box, background);
  };
  const fileName = (extension: string) => `${diagram?.caseKey ?? 'case'}.${extension}`;
  const copyImage = async () => {
    const source = image();
    if (!source) return;
    try {
      if (await copyPng(svgToPng(source))) toast.show({ title: t('diagram.copied') });
      else {
        download(await svgToPng(source), fileName('png'));
        toast.show({ title: t('diagram.downloadedInstead') });
      }
    } catch (failure) {
      toast.error(failure, t('diagram.copyFailed'));
    }
  };
  const downloadSvg = () => {
    const source = image();
    if (source) download(new Blob([source], { type: 'image/svg+xml' }), fileName('svg'));
  };

  if (parsed.error) {
    return <ErrorState error={parsed.error} />;
  }
  const shapes = drawingOrder(diagram?.shapes ?? []);
  const empty = shapes.length === 0;

  return (
    <div className={cx('diagram', 'cmmn-diagram', onSelect && 'diagram--selectable')}>
      <div ref={container} className="diagram__canvas">
        {empty ? (
          <p className="cmmn-diagram__empty">{t('cmmn.diagram.noLayout')}</p>
        ) : (
          <svg
            ref={svg}
            className="cmmn-diagram__svg"
            viewBox={viewBox.join(' ')}
            role={onSelect ? 'group' : 'img'}
            aria-label={label ?? t('cmmn.diagram.label')}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={() => (drag.current = null)}
          >
            <g className="viewport">
              {shapes.map((shape) => {
                const selectable = !!onSelect && isPlanItem(shape);
                const extra = describe?.(shape.id);
                const title = [shape.name ?? shape.id, extra].filter(Boolean).join(' · ');
                return (
                  <g
                    key={shape.id}
                    className={cx('cmmn-shape', `cmmn-shape--${shape.kind}`, shape.discretionary && 'is-discretionary', ...(classes.get(shape.id) ?? []))}
                    data-element-id={isPlanItem(shape) ? shape.id : undefined}
                    role={selectable ? 'button' : undefined}
                    tabIndex={selectable ? 0 : undefined}
                    aria-label={selectable ? title : undefined}
                    aria-pressed={selectable ? (selected ?? []).includes(shape.id) : undefined}
                    onKeyDown={selectable ? (event) => onKey(event, shape.id) : undefined}
                  >
                    {isPlanItem(shape) && <title>{title}</title>}
                    {shape.kind === 'casePlanModel' && <PlanModelTab shape={shape} />}
                    <Outline shape={shape} />
                    <TaskIcon shape={shape} />
                    <ShapeLabel shape={shape} />
                    <Markers shape={shape} />
                  </g>
                );
              })}
              {(diagram?.edges ?? []).map((edge) => (
                <EdgeView key={edge.id} edge={edge} />
              ))}
              {shapes
                .filter((shape) => counts.has(shape.id))
                .map((shape) => {
                  const count = counts.get(shape.id) ?? 0;
                  return <Badge key={`badge-${shape.id}`} shape={shape} count={count} title={t('diagram.badge.instances', { count })} />;
                })}
            </g>
          </svg>
        )}
      </div>
      {!empty && (
        <div className="diagram__tools">
          <IconButton icon={Plus} label={t('diagram.zoomIn')} onClick={() => zoom(1.25)} />
          <IconButton icon={Minus} label={t('diagram.zoomOut')} onClick={() => zoom(0.8)} />
          <IconButton icon={Maximize} label={t('diagram.fit')} onClick={fit} />
          <IconButton icon={Copy} label={t('diagram.copyImage')} onClick={() => void copyImage()} />
          <IconButton icon={ImageDown} label={t('diagram.downloadSvg')} onClick={downloadSvg} />
        </div>
      )}
    </div>
  );
}
