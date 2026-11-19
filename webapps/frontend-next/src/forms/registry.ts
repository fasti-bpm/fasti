/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { lazy } from 'react';
import type { FormTarget } from '../api/types';
import type { FormRenderer, ResolvedForm } from './types';

const renderers: FormRenderer[] = [];

/** Adds a renderer; it is asked before the renderers registered earlier. */
export function registerFormRenderer(renderer: FormRenderer): void {
  const existing = renderers.findIndex((candidate) => candidate.id === renderer.id);
  if (existing >= 0) {
    renderers.splice(existing, 1);
  }
  renderers.unshift(renderer);
}

export function findFormRenderer(form: ResolvedForm, target: FormTarget): FormRenderer | null {
  return renderers.find((renderer) => renderer.supports(form, target)) ?? null;
}

// The built-in renderers. Each one is loaded on demand: the form library is
// only fetched when a form of that kind is opened.
registerFormRenderer({
  id: 'embedded',
  supports: (form) => form.kind === 'embedded',
  component: lazy(async () => ({ default: (await import('./EmbeddedForm')).EmbeddedForm })),
});

registerFormRenderer({
  id: 'external',
  supports: (form) => form.kind === 'external',
  component: lazy(async () => ({ default: (await import('./ExternalForm')).ExternalForm })),
});

registerFormRenderer({
  id: 'generic',
  supports: (form) => form.kind === 'generic',
  component: lazy(async () => ({ default: (await import('./GenericForm')).GenericForm })),
});

registerFormRenderer({
  id: 'generated',
  supports: (form) => form.kind === 'generated',
  component: lazy(async () => ({ default: (await import('./GeneratedForm')).GeneratedForm })),
});

registerFormRenderer({
  id: 'camunda-form',
  supports: (form) => form.kind === 'camunda-form',
  component: lazy(async () => ({ default: (await import('./CamundaForm')).CamundaForm })),
});
