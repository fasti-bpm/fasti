/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { FormEditor as Editor } from '@bpmn-io/form-js-editor';
import '@bpmn-io/form-js-viewer/dist/assets/form-js.css';
import '@bpmn-io/form-js-editor/dist/assets/form-js-editor.css';
import '@bpmn-io/form-js-editor/dist/assets/properties-panel.css';
import { useI18n } from '../i18n';
import { ErrorState } from '../ui';
import { translateModule } from './libraryText';
import { EditorToolbar } from './EditorToolbar';
import './modeler.css';
import type { EditorHandle, EditorProps } from './kinds';

/**
 * Edits a form with the palette, canvas and properties panel of the form
 * library. Loaded on demand; the bpmn.io watermark stays visible.
 */
const FormEditor = forwardRef<EditorHandle, EditorProps>(function FormEditor({ source, onChange, onReady, label }, ref) {
  const { locale } = useI18n();
  const container = useRef<HTMLDivElement>(null);
  const editor = useRef<Editor | null>(null);
  const changed = useRef(onChange);
  const readyRef = useRef(onReady);
  const [error, setError] = useState<unknown>(null);
  const [history, setHistory] = useState({ undo: false, redo: false });

  useEffect(() => {
    changed.current = onChange;
    readyRef.current = onReady;
  }, [onChange, onReady]);

  useImperativeHandle(ref, () => ({
    save: () => {
      if (!editor.current) return Promise.reject(new Error('The editor is not ready'));
      return Promise.resolve(`${JSON.stringify(editor.current.saveSchema(), null, 2)}\n`);
    },
  }));

  useEffect(() => {
    const element = container.current;
    if (!element) return;
    const instance = new Editor({ container: element, additionalModules: [translateModule(locale)] });
    let alive = true;
    setError(null);
    let schema: unknown;
    try {
      schema = JSON.parse(source);
    } catch (cause) {
      setError(cause);
      return () => instance.destroy();
    }
    instance
      .importSchema(schema as Parameters<Editor['importSchema']>[0])
      .then(() => {
        if (!alive) return;
        editor.current = instance;
        readyRef.current?.();
        const stack = instance.get<{ canUndo(): boolean; canRedo(): boolean }>('commandStack');
        instance.on('changed', () => {
          setHistory({ undo: stack.canUndo(), redo: stack.canRedo() });
          changed.current();
        });
      })
      .catch((cause: unknown) => alive && setError(cause));
    return () => {
      alive = false;
      editor.current = null;
      instance.destroy();
    };
    // the language is read when the editor is made; changing it later must not drop the edits
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source]);

  return (
    <div className="modeler modeler--form">
      <EditorToolbar
        canUndo={history.undo}
        canRedo={history.redo}
        onUndo={() => editor.current?.get<{ undo(): void }>('commandStack').undo()}
        onRedo={() => editor.current?.get<{ redo(): void }>('commandStack').redo()}
      />
      {error ? <ErrorState error={error} /> : null}
      <div ref={container} className="modeler__canvas form-editor" role="application" aria-label={label} />
    </div>
  );
});

export default FormEditor;
