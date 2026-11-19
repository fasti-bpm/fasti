/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { diagramFor, keyOfDefinitionId } from './diagrams';

const twoProcesses = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" id="d">
  <bpmn:process id="first" isExecutable="true"/>
  <bpmn:process id="second" isExecutable="true"/>
  <bpmn:collaboration id="c"><bpmn:participant id="p" processRef="third"/></bpmn:collaboration>
  <bpmn:process id="third" isExecutable="true"/>
  <bpmndi:BPMNDiagram id="d1"><bpmndi:BPMNPlane id="pl1" bpmnElement="first"/></bpmndi:BPMNDiagram>
  <bpmndi:BPMNDiagram id="d2"><bpmndi:BPMNPlane id="pl2" bpmnElement="second"/></bpmndi:BPMNDiagram>
  <bpmndi:BPMNDiagram id="d3"><bpmndi:BPMNPlane id="pl3" bpmnElement="c"/></bpmndi:BPMNDiagram>
</bpmn:definitions>`;

describe('diagramFor', () => {
  it('picks the diagram of the process, not the first one of the file', () => {
    expect(diagramFor(twoProcesses, 'second')).toBe('d2');
    expect(diagramFor(twoProcesses, 'first')).toBe('d1');
  });

  it('finds a process drawn as a participant of a collaboration', () => {
    expect(diagramFor(twoProcesses, 'third')).toBe('d3');
  });

  it('leaves the choice to the viewer without a key or a match', () => {
    expect(diagramFor(twoProcesses, null)).toBeUndefined();
    expect(diagramFor(twoProcesses, 'missing')).toBeUndefined();
  });
});

describe('keyOfDefinitionId', () => {
  it('reads the key of a definition id', () => {
    expect(keyOfDefinitionId('invoice:2:abc')).toBe('invoice');
    expect(keyOfDefinitionId('6f1c-uuid')).toBeUndefined();
    expect(keyOfDefinitionId(undefined)).toBeUndefined();
  });
});
