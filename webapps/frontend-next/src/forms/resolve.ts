/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { FormInfo } from '../api/types';
import type { ResolvedForm } from './types';

const EMBEDDED = 'embedded:';
const CAMUNDA_FORMS = 'camunda-forms:';
const APP = 'app:';
const DEPLOYMENT = 'deployment:';
const GENERATED = /^engine:\/\/engine\/:engine\/(task|process-definition)\/[^/]+\/rendered-form$/;

/**
 * Joins the context path of a process application and the path of a form
 * to a path on the origin of the page. Anything that would leave the origin
 * or the application is rejected.
 */
export function applicationResourcePath(contextPath: string | null, path: string): string | null {
  if (!contextPath || !contextPath.startsWith('/') || contextPath.startsWith('//')) {
    return null;
  }
  const joined = `${contextPath}/${path}`.replace(/\/{2,}/g, '/');
  if (joined.includes('\\') || joined.split('/').includes('..')) {
    return null;
  }
  return joined;
}

/** Determines the kind of a form from the form key of the engine. */
export function resolveForm(info: FormInfo): ResolvedForm {
  const key = info.key;
  if (info.camundaFormRef) {
    return { kind: 'camunda-form', info };
  }
  if (!key) {
    return { kind: 'generic', info };
  }
  if (key.startsWith(EMBEDDED)) {
    const rest = key.substring(EMBEDDED.length);
    return { kind: GENERATED.test(rest) ? 'generated' : 'embedded', info };
  }
  if (key.startsWith(CAMUNDA_FORMS)) {
    const rest = key.substring(CAMUNDA_FORMS.length);
    if (rest.startsWith(DEPLOYMENT)) {
      return { kind: 'camunda-form', info };
    }
    if (rest.startsWith(APP)) {
      const resourcePath = applicationResourcePath(info.contextPath, rest.substring(APP.length));
      if (resourcePath) {
        return { kind: 'camunda-form', info, resourcePath };
      }
    }
  }
  return { kind: 'external', info };
}
