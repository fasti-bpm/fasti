/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import Modeler from 'bpmn-js/lib/Modeler';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import { readPreference, writePreference } from '../lib/storage';
import { ErrorState } from '../ui';
import '../bpmn/bpmn.css';
import './modeler.css';
import type { EditorHandle, EditorProps } from './kinds';
import { THEME_COLOURS } from '../bpmn/diagrams';
import { EditorToolbar } from './EditorToolbar';
import { bpmnTranslationModules } from './libraryText';
import { useI18n } from '../i18n';
import { PropertiesPanel, type DiagramElement, type EditorServices, type SelectionListener } from './PropertiesPanel';
import type { Generic } from './extensions';

export type BpmnEditorHandle = EditorHandle;

const CAMUNDA_NS = 'http://camunda.org/schema/1.0/bpmn';

interface Canvas {
  zoom(level?: number | 'fit-viewport', center?: 'auto'): number;
  getRootElement(): DiagramElement;
}
interface EventBus {
  on(event: string, listener: (event: never) => void): void;
  off(event: string, listener: (event: never) => void): void;
}
interface CommandStack {
  canUndo(): boolean;
  canRedo(): boolean;
  undo(): void;
  redo(): void;
}
interface Modeling {
  updateProperties(element: DiagramElement, properties: Record<string, unknown>): void;
  updateModdleProperties(element: DiagramElement, object: unknown, properties: Record<string, unknown>): void;
  updateLabel(element: DiagramElement, text: string): void;
}

/** The services of the editor, in the shape the properties panel needs. */
const TOGETHER = 'fasti.together';

/** A command that runs several changes, so one undo reverts all of them. */
class TogetherHandler {
  preExecute(context: { run: () => void }) {
    context.run();
  }
  execute() {
    return [];
  }
  revert() {
    return [];
  }
}

function servicesOf(instance: Modeler): EditorServices {
  const commandStack = instance.get<{
    registerHandler(name: string, handler: unknown): void;
    execute(name: string, context: unknown): void;
    _getHandler?(name: string): unknown;
  }>('commandStack');
  if (!commandStack._getHandler?.(TOGETHER)) commandStack.registerHandler(TOGETHER, TogetherHandler);
  const eventBus = instance.get<EventBus>('eventBus');
  const registry = instance.get<{ get(id: string): DiagramElement | undefined }>('elementRegistry');
  const modeling = instance.get<Modeling>('modeling');
  const moddle = instance.get<{
    create(type: string, properties: Record<string, unknown>): unknown;
    createAny(name: string, uri: string, properties: Record<string, unknown>): unknown;
    ids: { assigned(id: string): unknown };
  }>('moddle');
  const selection = instance.get<{ get(): DiagramElement[] }>('selection');
  return {
    on: (event, listener: SelectionListener) => eventBus.on(event, listener as (event: never) => void),
    off: (event, listener: SelectionListener) => eventBus.off(event, listener as (event: never) => void),
    root: () => instance.get<Canvas>('canvas').getRootElement(),
    selected: () => selection.get(),
    byId: (id) => registry.get(id),
    // processes of pools are not drawn and may have been renamed without a claim: look at them too
    idTaken: (id) =>
      !!registry.get(id) ||
      !!moddle.ids.assigned(id) ||
      (instance.getDefinitions() as unknown as { rootElements?: { id: string }[] }).rootElements?.some(
        (element) => element.id === id,
      ) === true,
    update: (element, properties) => modeling.updateProperties(element, properties),
    updateObject: (element, object, properties) => modeling.updateModdleProperties(element, object, properties),
    label: (element, text) => modeling.updateLabel(element, text),
    create: (type, properties) => moddle.create(type, properties),
    together: (run) => commandStack.execute(TOGETHER, { run }),
    createGeneric: (name, properties) => moddle.createAny(`camunda:${name}`, CAMUNDA_NS, properties) as Generic,
    definitions: () => instance.getDefinitions() as unknown as ReturnType<EditorServices['definitions']>,
  };
}

/**
 * Edits a process model with the palette, context pad and keyboard of the
 * diagram library, a toolbar and a properties panel for what the engine
 * reads. Loaded on demand: the modelling code is only fetched when the editor
 * is opened. The bpmn.io watermark stays visible.
 */
const BpmnEditor = forwardRef<BpmnEditorHandle, EditorProps>(
  function BpmnEditor({ source: xml, onChange, onReady, label }, ref) {
    const container = useRef<HTMLDivElement>(null);
    const modeler = useRef<Modeler | null>(null);
    const changed = useRef(onChange);
    const readyRef = useRef(onReady);
    const [error, setError] = useState<unknown>(null);
    const [ready, setReady] = useState<Modeler | null>(null);
    const [history, setHistory] = useState({ undo: false, redo: false });
    const [panel, setPanel] = useState(() => readPreference<boolean>('modelerPanel', true));
    // the texts of the diagram tools follow the language the editor was opened in; a change of
    // language must not rebuild the editor and drop unsaved edits
    const { locale } = useI18n();

    useEffect(() => {
      changed.current = onChange;
      readyRef.current = onReady;
    }, [onChange, onReady]);

    useImperativeHandle(ref, () => ({
      save: async () => {
        if (!modeler.current) {
          throw new Error('The editor is not ready');
        }
        const result = await modeler.current.saveXML({ format: true });
        return result.xml ?? '';
      },
    }));

    useEffect(() => {
      const element = container.current;
      if (!element) {
        return;
      }
      const instance = new Modeler({
        container: element,
        // the shapes refer to the colours of the theme, so a theme change needs no redraw
        bpmnRenderer: THEME_COLOURS,
        additionalModules: bpmnTranslationModules(locale) as never[],
      });
      let active = true;
      setError(null);
      instance
        .importXML(xml)
        .then(() => {
          if (!active) return;
          // attributes of the engine need their namespace, also in files that had none
          const definitions = instance.getDefinitions() as unknown as { $attrs: Record<string, string> };
          if (!definitions.$attrs['xmlns:camunda']) definitions.$attrs['xmlns:camunda'] = CAMUNDA_NS;
          modeler.current = instance;
          setReady(instance);
          readyRef.current?.();
          const stack = instance.get<CommandStack>('commandStack');
          instance.get<EventBus>('eventBus').on('commandStack.changed', () => {
            setHistory({ undo: stack.canUndo(), redo: stack.canRedo() });
            changed.current();
          });
        })
        .catch((cause: unknown) => active && setError(cause));
      return () => {
        active = false;
        modeler.current = null;
        setReady(null);
        instance.destroy();
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps -- see the language above
    }, [xml]);

    const services = useMemo(() => (ready ? servicesOf(ready) : null), [ready]);
    // the diagram is fitted once the panel beside it took its room
    useEffect(() => {
      if (!ready) return;
      // with room for the palette on the left and the zoom of a small diagram kept at most 1
      const canvas = ready.get<
        Canvas & {
          scroll(delta: { dx: number; dy: number }): void;
          viewbox(): { x: number; width: number; scale: number; inner: { x: number; width: number } };
        }
      >('canvas');
      const level = canvas.zoom('fit-viewport', 'auto');
      if (Number.isFinite(level) && level > 0) {
        // a diagram that fits at full size stays at full size
        canvas.zoom(level >= 1 ? 1 : level * 0.85, 'auto');
        // only as far as the room on the right allows, so nothing is cut off there
        const box = canvas.viewbox();
        const right = (box.x + box.width - (box.inner.x + box.inner.width)) * box.scale;
        const dx = Math.min(48, Math.max(0, right - 8));
        if (dx > 0) canvas.scroll({ dx, dy: 0 });
      }
    }, [ready]);
    const stack = () => modeler.current?.get<CommandStack>('commandStack');
    const zoom = (step: 1 | -1 | 0) => {
      const canvas = modeler.current?.get<Canvas>('canvas');
      if (!canvas) return;
      if (step === 0) canvas.zoom('fit-viewport', 'auto');
      else canvas.zoom(Math.min(4, Math.max(0.2, canvas.zoom() * (step > 0 ? 1.25 : 0.8))), 'auto');
    };

    return (
      <div className="modeler modeler--bpmn">
        <EditorToolbar
          canUndo={history.undo}
          canRedo={history.redo}
          onUndo={() => stack()?.undo()}
          onRedo={() => stack()?.redo()}
          onZoom={zoom}
          panel={panel}
          onPanel={() => {
            writePreference('modelerPanel', !panel);
            setPanel(!panel);
          }}
        />
        {error ? <ErrorState error={error} /> : null}
        <div className="modeler__workspace">
          <div ref={container} className="diagram modeler__canvas" role="application" aria-label={label} />
          {panel && services && <PropertiesPanel services={services} />}
        </div>
      </div>
    );
  },
);

export default BpmnEditor;
