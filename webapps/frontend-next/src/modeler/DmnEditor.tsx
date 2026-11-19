/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import DmnModeler from 'dmn-js/lib/Modeler';
import 'dmn-js/dist/assets/diagram-js.css';
import 'dmn-js/dist/assets/dmn-font/css/dmn.css';
import 'dmn-js/dist/assets/dmn-js-shared.css';
import 'dmn-js/dist/assets/dmn-js-drd.css';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';
import 'dmn-js/dist/assets/dmn-js-decision-table-controls.css';
import 'dmn-js/dist/assets/dmn-js-literal-expression.css';
import 'dmn-js/dist/assets/dmn-js-boxed-expression.css';
import 'dmn-js/dist/assets/dmn-js-boxed-expression-controls.css';
import { ErrorState, cx } from '../ui';
import { EditorToolbar } from './EditorToolbar';
import '../dmn/dmn.css';
import './modeler.css';
import type { EditorHandle, EditorProps } from './kinds';
import { THEME_COLOURS } from '../bpmn/diagrams';
import { useI18n } from '../i18n';
import { dmnTranslation, translateModule } from './libraryText';

interface CommandStack {
  canUndo(): boolean;
  canRedo(): boolean;
  undo(): void;
  redo(): void;
}
interface Canvas {
  zoom(level?: number | 'fit-viewport', center?: 'auto'): number;
}
type ActiveViewer = { get<T>(name: string): T };

interface DmnView {
  id: string;
  name?: string;
  type: string;
}

/**
 * Edits a decision model: the requirements diagram, decision tables and
 * literal expressions, with a tab per view. Loaded on demand; the bpmn.io
 * watermark stays visible.
 */
const DmnEditor = forwardRef<EditorHandle, EditorProps>(function DmnEditor({ source, onChange, onReady, label }, ref) {
  const container = useRef<HTMLDivElement>(null);
  const modeler = useRef<DmnModeler | null>(null);
  const { locale } = useI18n();
  const changed = useRef(onChange);
  const readyRef = useRef(onReady);
  const [error, setError] = useState<unknown>(null);
  const [views, setViews] = useState<DmnView[]>([]);
  const [active, setActive] = useState<string | null>(null);
  const [history, setHistory] = useState({ undo: false, redo: false });

  useEffect(() => {
    changed.current = onChange;
    readyRef.current = onReady;
  }, [onChange, onReady]);

  useImperativeHandle(ref, () => ({
    save: async () => {
      if (!modeler.current) throw new Error('The editor is not ready');
      const result = await modeler.current.saveXML({ format: true });
      return result.xml ?? '';
    },
  }));

  useEffect(() => {
    const element = container.current;
    if (!element) return;
    const instance = new DmnModeler({
      container: element,
      ...dmnTranslation(locale),
      drd: { drdRenderer: THEME_COLOURS, additionalModules: [translateModule(locale)] },
    });
    let alive = true;
    setError(null);
    instance.on('views.changed', (event) => {
      if (!alive) return;
      setViews(event.views.map((view) => ({ id: view.id, name: view.name ?? view.element.name, type: view.type })));
      setActive(event.activeView?.id ?? null);
    });
    instance
      .importXML(source)
      .then(() => {
        if (!alive) return;
        modeler.current = instance;
        readyRef.current?.();
        // every view has its own command stack: the toolbar follows the one shown
        const listened = new WeakSet<object>();
        const listen = () => {
          const viewer = instance.getActiveViewer() as ActiveViewer | undefined;
          if (!viewer) return;
          const stack = viewer.get<CommandStack>('commandStack');
          const update = () => setHistory({ undo: stack.canUndo(), redo: stack.canRedo() });
          update();
          const eventBus = viewer.get<{ on(event: string, listener: () => void): void }>('eventBus');
          if (listened.has(eventBus)) return;
          listened.add(eventBus);
          eventBus.on('commandStack.changed', () => {
            update();
            changed.current();
          });
        };
        listen();
        instance.on('views.changed', listen);
        // the requirements diagram is fitted with a margin, so nothing starts cut off
        if (instance.getActiveView()?.type === 'drd') {
          const canvas = (instance.getActiveViewer() as ActiveViewer | undefined)?.get<Canvas>('canvas');
          const level = canvas?.zoom('fit-viewport', 'auto');
          if (canvas && level && Number.isFinite(level)) canvas.zoom(level >= 1 ? 1 : level * 0.8, 'auto');
        }
      })
      .catch((cause: unknown) => alive && setError(cause));
    return () => {
      alive = false;
      modeler.current = null;
      instance.destroy();
    };
    // the language is read when the editor is made; changing it later must not drop the edits
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source]);

  const viewer = () => modeler.current?.getActiveViewer() as ActiveViewer | undefined;
  const drd = views.find((view) => view.id === active)?.type === 'drd';
  const zoom = (step: 1 | -1 | 0) => {
    const canvas = viewer()?.get<Canvas>('canvas');
    if (!canvas) return;
    if (step === 0) canvas.zoom('fit-viewport', 'auto');
    else canvas.zoom(Math.min(4, Math.max(0.2, canvas.zoom() * (step > 0 ? 1.25 : 0.8))), 'auto');
  };

  return (
    <div className="modeler modeler--dmn">
      <EditorToolbar
        canUndo={history.undo}
        canRedo={history.redo}
        onUndo={() => viewer()?.get<CommandStack>('commandStack').undo()}
        onRedo={() => viewer()?.get<CommandStack>('commandStack').redo()}
        onZoom={drd ? zoom : undefined}
      >
        {views.length > 1 && (
          <div className="segmented modeler__views" role="group" aria-label={label}>
            {views.map((view) => (
              <button
                key={view.id}
                type="button"
                aria-pressed={view.id === active}
                className={cx('segmented__item', view.id === active && 'is-active')}
                onClick={() => {
                  const target = modeler.current?.getViews().find((candidate) => candidate.id === view.id);
                  if (target) void modeler.current?.open(target);
                }}
              >
                {view.name || view.id}
              </button>
            ))}
          </div>
        )}
      </EditorToolbar>
      {error ? <ErrorState error={error} /> : null}
      <div ref={container} className="diagram modeler__canvas dmn" role="application" aria-label={label} />
    </div>
  );
});

export default DmnEditor;
