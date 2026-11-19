/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState } from 'react';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import { Copy, ImageDown, Maximize, Minus, Plus } from 'lucide-react';
import { useI18n } from '../i18n';
import { THEME_COLOURS, diagramFor } from './diagrams';
import { ErrorState, IconButton } from '../ui';
import { useToast } from '../ui/toast';
import { copyPng, diagramSvg, download, svgToPng, type Box } from './exportImage';
import './bpmn.css';

interface Canvas {
  zoom(level?: number | 'fit-viewport', center?: 'auto'): number;
  viewbox(): { inner: Box };
  addMarker(elementId: string, marker: string): void;
  removeMarker(elementId: string, marker: string): void;
  scroll?(delta: { dx: number; dy: number }): void;
}
interface ElementRegistry {
  get(id: string): unknown;
}
interface Overlays {
  add(elementId: string, type: string, overlay: unknown): string;
  remove(filter: { type: string }): void;
}
interface EventBus {
  on(event: string, listener: (event: { element?: { id?: string; type?: string } }) => void): void;
}

/** What is going on at an activity: shown as badges at its lower left corner. */
export interface ActivityBadge {
  activityId: string;
  instances: number;
  incidents: number;
  /** finished or canceled instances, e.g. from history */
  finished?: number;
}

/** A class put on an activity, e.g. a colour of the heat map. */
export interface ActivityMarker {
  activityId: string;
  marker: string;
}

const HIGHLIGHT = 'is-current';
const SELECTED = 'is-selected';
const BADGES = 'activity-badges';

/** Fits the diagram with a margin, so the zoom buttons in the corner cover none of it. */
function fit(canvas: Canvas) {
  const level = canvas.zoom('fit-viewport', 'auto');
  const narrow = typeof window !== 'undefined' && window.innerWidth < 640;
  if (!Number.isFinite(level) || level <= 0 || level >= 1) return;
  canvas.zoom(level * (narrow ? 0.72 : 0.9), 'auto');
  // on a phone the zoom buttons take the right edge: the diagram moves a little to the left
  if (narrow) canvas.scroll?.({ dx: -22, dy: 0 });
}
const NOT_AN_ACTIVITY = /Process$|Collaboration$|Participant$|Lane$|SequenceFlow$|label$/;

function badge(kind: 'instances' | 'incidents' | 'finished', value: number, title: string): HTMLElement {
  const element = document.createElement('span');
  element.className = `diagram-badge diagram-badge--${kind}`;
  element.textContent = value > 999 ? '999+' : String(value);
  element.title = title;
  return element;
}

export interface BpmnDiagramProps {
  xml: string;
  /** the activity a task belongs to */
  highlight?: string | null;
  badges?: ActivityBadge[];
  /** activities the surrounding page filters by */
  selected?: string[];
  /** further classes on activities */
  markers?: ActivityMarker[];
  /** called with the id of a clicked activity, `null` for the background */
  onSelect?: (activityId: string | null) => void;
  /** called with the id and type of a double-clicked activity, e.g. to open a called instance */
  onOpen?: (activityId: string, type: string) => void;
  label?: string;
  /** the key of the process to show when the file holds several diagrams */
  processKey?: string | null;
}


/**
 * Shows a process diagram, marks activities and puts numbers on them. The
 * watermark of the diagram library links to bpmn.io and must stay visible.
 */
export default function BpmnDiagram({
  xml,
  highlight,
  badges,
  selected,
  markers,
  onSelect,
  onOpen,
  label,
  processKey,
}: BpmnDiagramProps) {
  const { t } = useI18n();
  const container = useRef<HTMLDivElement>(null);
  const viewer = useRef<NavigatedViewer | null>(null);
  const select = useRef(onSelect);
  const open = useRef(onOpen);
  const [error, setError] = useState<unknown>(null);
  // counts the diagrams that were drawn: marks and badges belong to the current one
  const [drawn, setDrawn] = useState(0);

  useEffect(() => {
    select.current = onSelect;
    open.current = onOpen;
  }, [onSelect, onOpen]);

  useEffect(() => {
    const element = container.current;
    if (!element) {
      return;
    }
    const instance = new NavigatedViewer({
      container: element,
      bpmnRenderer: THEME_COLOURS,
    });
    let active = true;
    setError(null);
    instance.get<EventBus>('eventBus').on('element.click', (event) => {
      const clicked = event.element;
      if (select.current && clicked?.id) {
        select.current(NOT_AN_ACTIVITY.test(clicked.type ?? '') ? null : clicked.id);
      }
    });
    instance.get<EventBus>('eventBus').on('element.dblclick', (event) => {
      const clicked = event.element;
      if (open.current && clicked?.id && !NOT_AN_ACTIVITY.test(clicked.type ?? '')) {
        open.current(clicked.id, clicked.type ?? '');
      }
    });
    instance
      .importXML(xml, diagramFor(xml, processKey))
      .then(() => {
        if (!active) {
          return;
        }
        fit(instance.get<Canvas>('canvas'));
        viewer.current = instance;
        setDrawn((value) => value + 1);
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(cause);
        }
      });
    // the diagram keeps fitting its area when the window or the layout changes
    let width = element.clientWidth;
    const observer = new ResizeObserver(() => {
      if (viewer.current === instance && element.clientWidth !== width && element.clientWidth > 0) {
        width = element.clientWidth;
        fit(instance.get<Canvas>('canvas'));
      }
    });
    observer.observe(element);
    return () => {
      active = false;
      observer.disconnect();
      viewer.current = null;
      instance.destroy();
    };
  }, [xml, processKey]);

  const selectedKey = (selected ?? []).join('\n');
  const markerKey = (markers ?? []).map((entry) => `${entry.activityId} ${entry.marker}`).join('\n');
  useEffect(() => {
    const instance = viewer.current;
    if (!instance || drawn === 0) {
      return;
    }
    const canvas = instance.get<Canvas>('canvas');
    const registry = instance.get<ElementRegistry>('elementRegistry');
    const marked: [string, string][] = [];
    if (highlight && registry.get(highlight)) {
      marked.push([highlight, HIGHLIGHT]);
    }
    for (const id of selectedKey ? selectedKey.split('\n') : []) {
      if (registry.get(id)) {
        marked.push([id, SELECTED]);
      }
    }
    for (const line of markerKey ? markerKey.split('\n') : []) {
      const [id = '', marker = ''] = line.split(' ');
      if (marker && registry.get(id)) {
        marked.push([id, marker]);
      }
    }
    marked.forEach(([id, marker]) => canvas.addMarker(id, marker));
    return () => {
      if (viewer.current === instance) {
        marked.forEach(([id, marker]) => canvas.removeMarker(id, marker));
      }
    };
  }, [drawn, highlight, selectedKey, markerKey]);

  useEffect(() => {
    const instance = viewer.current;
    if (!instance || drawn === 0 || !badges) {
      return;
    }
    const overlays = instance.get<Overlays>('overlays');
    const registry = instance.get<ElementRegistry>('elementRegistry');
    for (const entry of badges) {
      const finished = entry.finished ?? 0;
      if ((entry.instances <= 0 && entry.incidents <= 0 && finished <= 0) || !registry.get(entry.activityId)) {
        continue;
      }
      const group = document.createElement('div');
      group.className = 'diagram-badges';
      if (entry.instances > 0) {
        group.append(
          badge('instances', entry.instances, t('diagram.badge.instances', { count: entry.instances })),
        );
      }
      if (finished > 0) {
        group.append(badge('finished', finished, t('diagram.badge.finished', { count: finished })));
      }
      if (entry.incidents > 0) {
        group.append(
          badge('incidents', entry.incidents, t('diagram.badge.incidents', { count: entry.incidents })),
        );
      }
      // events and gateways carry their label below: their numbers go above
      const type = (registry.get(entry.activityId) as { type?: string } | undefined)?.type ?? '';
      const small = /Event$|Gateway$/.test(type);
      overlays.add(entry.activityId, BADGES, {
        position: small ? { top: -9, left: -9 } : { bottom: 11, left: -9 },
        // the numbers stay readable when the diagram is zoomed out
        scale: false,
        html: group,
      });
    }
    return () => {
      if (viewer.current === instance) {
        overlays.remove({ type: BADGES });
      }
    };
  }, [drawn, badges, t]);

  const zoom = (factor: number | 'fit') => {
    const canvas = viewer.current?.get<Canvas>('canvas');
    if (!canvas) {
      return;
    }
    if (factor === 'fit') {
      canvas.zoom('fit-viewport', 'auto');
    } else {
      canvas.zoom(canvas.zoom() * factor);
    }
  };

  // the diagram as shown, with the colours of its states, as an image to paste or a file to keep
  const toast = useToast();
  const image = (): string | null => {
    const canvas = viewer.current?.get<Canvas>('canvas');
    const svg = container.current?.querySelector<SVGSVGElement>('.djs-container > svg');
    if (!canvas || !svg) return null;
    const background = getComputedStyle(container.current as Element).getPropertyValue('--diagram-fill').trim() || '#ffffff';
    return diagramSvg(svg, canvas.viewbox().inner, background);
  };
  const fileName = (extension: string) => `${processKey ?? 'diagram'}.${extension}`;
  const copyImage = async () => {
    const svg = image();
    if (!svg) return;
    try {
      if (await copyPng(svgToPng(svg))) toast.show({ title: t('diagram.copied') });
      else {
        download(await svgToPng(svg), fileName('png'));
        toast.show({ title: t('diagram.downloadedInstead') });
      }
    } catch (failure) {
      toast.error(failure, t('diagram.copyFailed'));
    }
  };
  const downloadSvg = () => {
    const svg = image();
    if (svg) download(new Blob([svg], { type: 'image/svg+xml' }), fileName('svg'));
  };

  if (error) {
    return <ErrorState error={error} />;
  }
  return (
    <div className={onSelect ? 'diagram diagram--selectable' : 'diagram'}>
      <div
        ref={container}
        className="diagram__canvas"
        role="img"
        aria-label={label ?? t('diagram.label')}
      />
      <div className="diagram__tools">
        <IconButton icon={Plus} label={t('diagram.zoomIn')} onClick={() => zoom(1.25)} />
        <IconButton icon={Minus} label={t('diagram.zoomOut')} onClick={() => zoom(0.8)} />
        <IconButton icon={Maximize} label={t('diagram.fit')} onClick={() => zoom('fit')} />
        {/* aria-disabled rather than disabled, so the tooltip still says what the tool does */}
        <IconButton
          icon={Copy}
          label={t('diagram.copyImage')}
          aria-disabled={drawn === 0 || undefined}
          onClick={() => drawn > 0 && void copyImage()}
        />
        <IconButton
          icon={ImageDown}
          label={t('diagram.downloadSvg')}
          aria-disabled={drawn === 0 || undefined}
          onClick={() => drawn > 0 && downloadSvg()}
        />
      </div>
    </div>
  );
}
