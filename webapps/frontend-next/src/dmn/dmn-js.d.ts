/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
// the model library ships without type declarations: the part that is used
declare module 'dmn-js/lib/NavigatedViewer' {
  interface DmnView {
    id: string;
    name?: string;
    type: string;
    element: { id: string; name?: string };
  }

  export default class NavigatedViewer {
    constructor(options: { container: HTMLElement; [viewer: string]: unknown });
    importXML(xml: string): Promise<{ warnings: unknown[] }>;
    getViews(): DmnView[];
    getActiveView(): DmnView | undefined;
    getActiveViewer(): { get<T>(service: string): T } | undefined;
    open(view: DmnView): Promise<unknown>;
    on(event: string, listener: (event: { views: DmnView[]; activeView?: DmnView }) => void): void;
    destroy(): void;
  }
}

declare module 'dmn-js/lib/Modeler' {
  interface DmnView {
    id: string;
    name?: string;
    type: string;
    element: { id: string; name?: string };
  }

  export default class Modeler {
    constructor(options: { container: HTMLElement; [option: string]: unknown });
    importXML(xml: string): Promise<{ warnings: unknown[] }>;
    saveXML(options?: { format?: boolean }): Promise<{ xml?: string }>;
    getViews(): DmnView[];
    getActiveView(): DmnView | undefined;
    getActiveViewer(): { get<T>(service: string): T } | undefined;
    open(view: DmnView): Promise<unknown>;
    on(event: string, listener: (event: { views: DmnView[]; activeView?: DmnView }) => void): void;
    destroy(): void;
  }
}