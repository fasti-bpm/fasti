/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useRef, useState } from 'react';
import NavigatedViewer from 'dmn-js/lib/NavigatedViewer';
import 'dmn-js/dist/assets/diagram-js.css';
import 'dmn-js/dist/assets/dmn-font/css/dmn.css';
import 'dmn-js/dist/assets/dmn-js-shared.css';
import 'dmn-js/dist/assets/dmn-js-drd.css';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';
import 'dmn-js/dist/assets/dmn-js-literal-expression.css';
import 'dmn-js/dist/assets/dmn-js-boxed-expression.css';
import { useI18n } from '../i18n';
import { ErrorState, cx } from '../ui';
import './dmn.css';
import { THEME_COLOURS } from '../bpmn/diagrams';
import { dmnTranslation, translateModule } from '../modeler/libraryText';

interface View {
  id: string;
  name?: string;
  type: string;
  element: { id: string; name?: string };
}

/**
 * Shows a decision model: the requirements diagram, decision tables and
 * literal expressions, read only. The watermark of the library links to
 * bpmn.io and must stay visible.
 */
export default function DmnViewer({
  xml,
  decisionKey,
  highlightRules,
}: {
  xml: string;
  /** the decision to show first */
  decisionKey?: string | null;
  /** rules of the decision table that are marked, e.g. those that matched */
  highlightRules?: string[];
}) {
  const { t, locale } = useI18n();
  const container = useRef<HTMLDivElement>(null);
  const viewer = useRef<NavigatedViewer | null>(null);
  const [views, setViews] = useState<View[]>([]);
  const [active, setActive] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const element = container.current;
    if (!element) {
      return;
    }
    const instance = new NavigatedViewer({
      container: element,
      ...dmnTranslation(locale),
      drd: {
        drdRenderer: THEME_COLOURS,
        additionalModules: [translateModule(locale)],
      },
    });
    viewer.current = instance;
    let alive = true;
    setError(null);
    instance.on('views.changed', (event: { views: View[]; activeView?: View }) => {
      if (!alive) {
        return;
      }
      setViews(event.views);
      setActive(event.activeView?.id ?? null);
      if (event.activeView?.type === 'drd') {
        // the diagram is shown completely, whatever size it was drawn in
        instance.getActiveViewer()?.get<{ zoom(level: 'fit-viewport'): number }>('canvas').zoom('fit-viewport');
      }
    });
    instance
      .importXML(xml)
      .then(() => {
        if (!alive) {
          return;
        }
        const wanted = decisionKey
          ? instance.getViews().find((view: View) => view.element.id === decisionKey)
          : undefined;
        if (wanted) {
          return instance.open(wanted).then(() => setActive(wanted.id));
        }
      })
      .catch((cause: unknown) => {
        if (alive) {
          setError(cause);
        }
      });
    return () => {
      alive = false;
      viewer.current = null;
      instance.destroy();
    };
  }, [xml, decisionKey, locale]);

  const rulesKey = (highlightRules ?? []).join('\n');
  useEffect(() => {
    const element = container.current;
    if (!element || !rulesKey) {
      return;
    }
    const rules = rulesKey.split('\n');
    const mark = () => {
      element.querySelectorAll('.is-matched-rule').forEach((cell) => cell.classList.remove('is-matched-rule'));
      for (const rule of rules) {
        element
          .querySelectorAll(`[data-row-id="${CSS.escape(rule)}"]`)
          .forEach((cell) => cell.classList.add('is-matched-rule'));
      }
    };
    mark();
    // the table is drawn after the view opens
    const observer = new MutationObserver(mark);
    observer.observe(element, { childList: true, subtree: true });
    const stop = window.setTimeout(() => observer.disconnect(), 3000);
    return () => {
      observer.disconnect();
      window.clearTimeout(stop);
    };
  }, [rulesKey, active]);

  if (error) {
    return <ErrorState error={error} />;
  }
  // a table or an expression is as tall as its content; only the diagram needs a fixed area
  const activeType = views.find((view) => view.id === active)?.type;
  return (
    <div className={cx('dmn', activeType && activeType !== 'drd' && 'dmn--content')}>
      {views.length > 1 && (
        <div className="dmn__views" role="group" aria-label={t('dmn.views')}>
          {views.map((view) => (
            <button
              key={view.id}
              type="button"
              aria-pressed={view.id === active}
              className={cx('toggle', view.id === active && 'is-on')}
              onClick={() => {
                const target = viewer.current?.getViews().find((entry: View) => entry.id === view.id);
                if (target) {
                  void viewer.current?.open(target).then(() => setActive(target.id));
                }
              }}
            >
              {view.type === 'drd' ? t('dmn.view.drd') : (view.name ?? view.element.name ?? view.element.id)}
            </button>
          ))}
        </div>
      )}
      <div ref={container} className="dmn__canvas" />
    </div>
  );
}
