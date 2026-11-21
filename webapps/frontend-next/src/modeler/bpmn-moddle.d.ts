/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/** The part of the meta-model library of the editor that the tests read and write XML with. */
declare module 'bpmn-moddle' {
  export class BpmnModdle {
    fromXML(xml: string): Promise<{ rootElement: { rootElements: unknown[] } }>;
    toXML(element: unknown, options?: { format?: boolean }): Promise<{ xml: string }>;
    createAny(name: string, uri: string, properties: Record<string, unknown>): unknown;
  }
}
