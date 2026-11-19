/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BookOpen, ExternalLink, LifeBuoy } from 'lucide-react';
import { getProcessXml } from '../api/engine';
import { useI18n } from '../i18n';
import { DocumentationText, extensionProperty } from '../lib/documentation';
import { RUNBOOK_PROPERTY, modelRunbook, runbookPatterns, runbookType, type RunbookType } from './runbookRules';

/**
 * What to check for a failure: the runbook the modeler linked to the activity
 * (`camunda:property name="fasti.runbook"`), hints for the error patterns found
 * in the message and stack trace, and what the type of incident usually means.
 * Every text is rendered as React elements; links only go to http(s) or mailto.
 */
export function IncidentRunbook({
  type,
  message,
  trace,
  processDefinitionId,
  activityId,
}: {
  /** the incident type, e.g. failedJob */
  type: string;
  message?: string | null;
  trace?: string | null;
  processDefinitionId?: string | null;
  activityId?: string | null;
}) {
  const { t } = useI18n();
  const id = useId();
  // the model of the version; shared with the diagram, so mostly read already
  const xml = useQuery({
    queryKey: ['process-xml', processDefinitionId],
    queryFn: () => getProcessXml(processDefinitionId ?? ''),
    enabled: !!processDefinitionId && !!activityId,
    staleTime: Infinity,
  });
  const fromModel = useMemo(
    () => modelRunbook(extensionProperty(xml.data, activityId, RUNBOOK_PROPERTY)),
    [xml.data, activityId],
  );
  const kind: RunbookType = runbookType(type);
  const patterns = runbookPatterns(message, trace);
  return (
    <section className="runbook" aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`} className="runbook__title">
        <LifeBuoy aria-hidden size={16} />
        {t('runbook.title')}
      </h3>
      {fromModel && (
        <div className="runbook__model">
          <h4 className="runbook__heading">
            <BookOpen aria-hidden size={14} />
            {t('runbook.fromModel')}
          </h4>
          {fromModel.kind === 'link' ? (
            <a className="btn btn--secondary btn--sm runbook__link" href={fromModel.href} target="_blank" rel="noopener noreferrer">
              <ExternalLink className="btn__icon" aria-hidden size={14} />
              {t('runbook.open')}
              <span className="runbook__href">{fromModel.href.replace(/^(https?:\/\/|mailto:)/i, '')}</span>
            </a>
          ) : (
            <DocumentationText text={fromModel.text} />
          )}
        </div>
      )}
      <ul className="runbook__hints">
        {patterns.map((pattern) => (
          <li key={pattern} className="runbook__hint runbook__hint--pattern">
            <h4 className="runbook__heading">{t(`runbook.pattern.${pattern}.title`)}</h4>
            <DocumentationText text={t(`runbook.pattern.${pattern}.text`)} />
          </li>
        ))}
        <li className="runbook__hint">
          {patterns.length > 0 || fromModel ? (
            // the general guidance stays folded when something more specific was found
            <details>
              <summary className="runbook__heading runbook__summary">{t(`runbook.type.${kind}.title`)}</summary>
              <DocumentationText text={t(`runbook.type.${kind}.text`)} />
            </details>
          ) : (
            <>
              <h4 className="runbook__heading">{t(`runbook.type.${kind}.title`)}</h4>
              <DocumentationText text={t(`runbook.type.${kind}.text`)} />
            </>
          )}
        </li>
      </ul>
      {!fromModel && <p className="runbook__tip">{t('runbook.tip', { property: RUNBOOK_PROPERTY })}</p>}
    </section>
  );
}
