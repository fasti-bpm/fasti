/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ComponentType } from 'react';
import type { FormInfo, FormTarget, ProcessInstanceRef, VariableMap } from '../api/types';

/**
 * How the form of a task or start event is provided:
 *
 * - `camunda-form`: a form schema, deployed with the process or served by
 *   the process application,
 * - `generated`: form fields defined in the process model,
 * - `generic`: no form at all, variables are entered freely,
 * - `embedded`: an HTML form with scripts, written for the forms SDK,
 * - `external`: a form of another application, opened in a new tab.
 */
export type FormKind = 'camunda-form' | 'generated' | 'generic' | 'embedded' | 'external';

export interface ResolvedForm {
  kind: FormKind;
  info: FormInfo;
  /** same-origin path of the form resource if it is served by an application */
  resourcePath?: string;
}

export interface SubmitOptions {
  /** only used when a process is started */
  businessKey?: string;
}

/** What the host of a form passes to the renderer that displays it. */
export interface FormRendererProps {
  target: FormTarget;
  form: ResolvedForm;
  /** the user may look at the form but not change or submit it */
  disabled: boolean;
  /** label of the submit button: "Complete" or "Start" */
  submitLabel: string;
  /**
   * Completes the task or starts the process with the given variables. The
   * host reports a failure to the user; the promise is rejected in that case
   * so that the renderer can keep the entered values.
   */
  submit: (variables: VariableMap, options?: SubmitOptions) => Promise<void>;
  /**
   * Writes the entered values of a task without completing it; missing for a
   * start form. The host reports a failure; the promise is rejected then.
   */
  save?: (variables: VariableMap) => Promise<void>;
  /**
   * For renderers that submit by themselves, e.g. embedded forms, which are
   * submitted by the forms SDK in their frame: tells the host that the task
   * was completed or the process started.
   */
  submitted: (result?: ProcessInstanceRef) => void;
  /** Lets the host report a failure of such a renderer; `null` clears it. */
  reportFailure: (error: unknown) => void;
}

/**
 * A form renderer displays one kind of form. Renderers are looked up in the
 * order of their registration, the one registered last wins: a custom
 * renderer can therefore take over forms from the built-in ones without any
 * change to the Tasklist.
 */
export interface FormRenderer {
  id: string;
  supports: (form: ResolvedForm, target: FormTarget) => boolean;
  component: ComponentType<FormRendererProps>;
}
