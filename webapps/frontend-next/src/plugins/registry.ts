/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ComponentType } from 'react';
import type { Task } from '../api/types';

/**
 * A deliberately small plugin host. A plugin point is a named place of the
 * user interface; a plugin contributes one component to one point. Plugins
 * are registered in code before the application is rendered (see
 * `src/plugins/index.ts`); the README of this module describes how plugins
 * that are deployed separately could be mounted later.
 */
export interface PluginPoints {
  /** additional entries of the main navigation */
  'shell.navigation': Record<string, never>;
  /** additional tabs of an open task */
  'tasklist.task.tab': { task: Task };
}

export type PluginPointName = keyof PluginPoints;

export interface Plugin<P extends PluginPointName = PluginPointName> {
  id: string;
  point: P;
  /** message shown as navigation entry or tab title */
  label: (locale: string) => string;
  component: ComponentType<PluginPoints[P]>;
}

const plugins: Plugin[] = [];

export function registerPlugin<P extends PluginPointName>(plugin: Plugin<P>): void {
  if (plugins.some((existing) => existing.id === plugin.id)) {
    throw new Error(`A plugin with id '${plugin.id}' is already registered`);
  }
  plugins.push(plugin as unknown as Plugin);
}

export function pluginsFor<P extends PluginPointName>(point: P): Plugin<P>[] {
  return plugins.filter((plugin) => plugin.point === point) as unknown as Plugin<P>[];
}

/** Test support. */
export function clearPlugins(): void {
  plugins.length = 0;
}
