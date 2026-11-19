/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { extensionProperty } from '../lib/documentation';
import { modelRunbook, runbookPatterns, runbookType } from './runbookRules';

const XML = `<?xml version="1.0"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
  <bpmn:process id="pay" isExecutable="true">
    <bpmn:serviceTask id="charge">
      <bpmn:extensionElements>
        <camunda:properties>
          <camunda:property name="other" value="x" />
          <camunda:property name="fasti.runbook" value=" https://wiki.example.org/runbooks/charge " />
        </camunda:properties>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="notify">
      <bpmn:extensionElements>
        <camunda:properties><camunda:property name="fasti.runbook">Call the **mail team**.</camunda:property></camunda:properties>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="bare" />
  </bpmn:process>
</bpmn:definitions>`;

describe('incident runbooks', () => {
  it('tells the types with their own guidance from the others', () => {
    expect(runbookType('failedJob')).toBe('failedJob');
    expect(runbookType('failedExternalTask')).toBe('failedExternalTask');
    expect(runbookType('myCustomType')).toBe('other');
  });

  it('finds the common error patterns in a message and a stack trace', () => {
    expect(runbookPatterns('java.net.ConnectException: Connection refused')).toEqual(['connection']);
    expect(runbookPatterns('Read timed out')).toEqual(['timeout']);
    expect(runbookPatterns(null, 'org.camunda.bpm.engine.OptimisticLockingException: ENGINE-03005')).toEqual(['optimisticLock']);
    expect(runbookPatterns("Unknown property used in expression: ${gateway.charge()}. Cause: Cannot resolve identifier 'gateway'")).toEqual([
      'expression',
    ]);
    expect(runbookPatterns('HTTP 503 Service Unavailable')).toEqual(['serverError']);
    expect(runbookPatterns('401 Unauthorized')).toEqual(['unauthorized']);
    expect(runbookPatterns('java.lang.ClassNotFoundException: com.acme.Delegate')).toEqual(['classNotFound']);
    expect(runbookPatterns('java.sql.SQLException: Lock wait timeout exceeded')).toEqual(['database']);
    expect(runbookPatterns('Something odd happened')).toEqual([]);
    expect(runbookPatterns(undefined, null, '')).toEqual([]);
  });

  it('reads the runbook of an activity from its extension properties', () => {
    expect(extensionProperty(XML, 'charge', 'fasti.runbook')).toBe('https://wiki.example.org/runbooks/charge');
    expect(extensionProperty(XML, 'notify', 'fasti.runbook')).toBe('Call the **mail team**.');
    expect(extensionProperty(XML, 'bare', 'fasti.runbook')).toBeNull();
    expect(extensionProperty(XML, 'missing', 'fasti.runbook')).toBeNull();
    expect(extensionProperty('<not xml', 'charge', 'fasti.runbook')).toBeNull();
    expect(extensionProperty(undefined, 'charge', 'fasti.runbook')).toBeNull();
  });

  it('only links to http, https and mailto; anything else stays text', () => {
    expect(modelRunbook('https://wiki.example.org/x')).toEqual({ kind: 'link', href: 'https://wiki.example.org/x' });
    expect(modelRunbook('mailto:ops@example.org')).toEqual({ kind: 'link', href: 'mailto:ops@example.org' });
    expect(modelRunbook('javascript:alert(1)')).toEqual({ kind: 'text', text: 'javascript:alert(1)' });
    expect(modelRunbook('https://a.example/ with spaces')).toEqual({ kind: 'text', text: 'https://a.example/ with spaces' });
    expect(modelRunbook('  ')).toBeNull();
    expect(modelRunbook(null)).toBeNull();
  });
});
