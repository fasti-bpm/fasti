/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { blankDecision, blankForm, blankProcess, decisionIdOf, formIdOf, kindOfFile, processIdOf } from './kinds';
import { checkReadiness } from './readiness';

describe('process modeler', () => {
  it('starts a new model as an executable process with a start event', () => {
    const xml = blankProcess('Process_x1');
    const document = new DOMParser().parseFromString(xml, 'application/xml');
    expect(document.getElementsByTagName('parsererror')).toHaveLength(0);
    const process = document.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'process')[0];
    expect(process?.getAttribute('id')).toBe('Process_x1');
    expect(process?.getAttribute('isExecutable')).toBe('true');
  });

  it('names a model after its first process', () => {
    expect(processIdOf(blankProcess('order'))).toBe('order');
    expect(processIdOf('<definitions><process id="plain" /></definitions>')).toBe('plain');
    expect(processIdOf('<definitions />')).toBeNull();
  });
});

describe('decision and form modeler', () => {
  it('starts a decision with a table and a time to live', () => {
    const xml = blankDecision('Decision_a1');
    const document = new DOMParser().parseFromString(xml, 'application/xml');
    expect(document.getElementsByTagName('parsererror')).toHaveLength(0);
    expect(document.getElementsByTagName('decisionTable')).toHaveLength(1);
    expect(decisionIdOf(xml)).toBe('Decision_a1');
    expect(xml).toContain('camunda:historyTimeToLive');
  });

  it('starts a form with an id that tasks refer to', () => {
    const text = blankForm('Form_b2');
    expect(JSON.parse(text)).toMatchObject({ id: 'Form_b2', type: 'default' });
    expect(formIdOf(text)).toBe('Form_b2');
    expect(formIdOf('{')).toBeNull();
    expect(formIdOf('{"id":3}')).toBeNull();
  });

  it('knows the kind of a model by its file name', () => {
    expect(kindOfFile('a/Order.BPMN')).toBe('bpmn');
    expect(kindOfFile('x.dmn11.xml')).toBe('dmn');
    expect(kindOfFile('task.form')).toBe('form');
    expect(kindOfFile('notes.txt')).toBeNull();
  });
});
describe('checks before deploying', () => {
  const process = (body: string, attributes = 'isExecutable="true" camunda:historyTimeToLive="30" name="Order"') =>
    `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:camunda="http://camunda.org/schema/1.0/bpmn"><process id="order" ${attributes}>${body}</process></definitions>`;
  const messages = (kind: 'bpmn' | 'dmn' | 'form', text: string) =>
    checkReadiness(kind, text).map((finding) => `${finding.level} ${finding.message} ${finding.element ?? ''}`.trim());

  it('passes a complete process', () => {
    expect(
      messages(
        'bpmn',
        process(`<startEvent id="s" /><sequenceFlow id="f1" sourceRef="s" targetRef="t" />
          <userTask id="t" name="Check" camunda:candidateGroups="sales" /><sequenceFlow id="f2" sourceRef="t" targetRef="g" />
          <exclusiveGateway id="g" default="f4" /><sequenceFlow id="f3" sourceRef="g" targetRef="x"><conditionExpression>ok</conditionExpression></sequenceFlow>
          <sequenceFlow id="f4" sourceRef="g" targetRef="e" />
          <serviceTask id="x" name="Ship" camunda:type="external" camunda:topic="ship" /><sequenceFlow id="f5" sourceRef="x" targetRef="e" />
          <endEvent id="e" />`),
      ),
    ).toEqual([]);
  });

  it('names what the engine refuses first, then what is worth a look', () => {
    expect(
      messages(
        'bpmn',
        process(
          `<startEvent id="s" /><sequenceFlow id="f1" sourceRef="s" targetRef="pay" /><serviceTask id="pay" name="Pay" />
           <serviceTask id="ext" camunda:type="external" /><userTask id="u" name="Approve" />`,
          'isExecutable="true"',
        ),
      ),
    ).toEqual([
      'error readiness.noImplementation Pay',
      'error readiness.noTopic ext',
      'warning readiness.noName order',
      'warning readiness.noTimeToLive order',
      'warning readiness.noEnd order',
      'warning readiness.noAssignee Approve',
      'warning readiness.unconnected Pay',
      'warning readiness.unconnected ext',
      'warning readiness.unconnected Approve',
    ]);
    expect(messages('bpmn', process('<startEvent id="s" />', 'isExecutable="false"'))).toEqual(['error readiness.notExecutable']);
    expect(messages('bpmn', '<definitions')).toEqual(['error readiness.invalidXml']);
  });

  it('refuses a four-eyes rule that names anything but user tasks of the process', () => {
    const rule = (value: string) =>
      `<extensionElements><camunda:properties><camunda:property name="fasti:fourEyes" value="${value}" /></camunda:properties></extensionElements>`;
    const model = (value: string) =>
      process(`<startEvent id="s" /><sequenceFlow id="f1" sourceRef="s" targetRef="draft" />
        <userTask id="draft" camunda:candidateGroups="sales" /><sequenceFlow id="f2" sourceRef="draft" targetRef="pay" />
        <serviceTask id="pay" camunda:expression="\${true}" /><sequenceFlow id="f3" sourceRef="pay" targetRef="approve" />
        <userTask id="approve" name="Approve" camunda:candidateGroups="sales">${rule(value)}</userTask>
        <sequenceFlow id="f4" sourceRef="approve" targetRef="e" /><endEvent id="e" />`);
    expect(messages('bpmn', model('draft, approve'))).toEqual([]);
    expect(messages('bpmn', model('draft,pay'))).toEqual(['error readiness.fourEyesSteps Approve']);
    expect(messages('bpmn', model('ghost'))).toEqual(['error readiness.fourEyesSteps Approve']);
    expect(messages('bpmn', model(' , '))).toEqual(['error readiness.fourEyesSteps Approve']);
  });

  it('reads a four-eyes rule from the properties of the task only', () => {
    const model = (extensions: string) =>
      process(`<startEvent id="s" /><sequenceFlow id="f1" sourceRef="s" targetRef="approve" />
        <userTask id="approve" name="Approve" camunda:candidateGroups="sales"><extensionElements>${extensions}</extensionElements></userTask>
        <sequenceFlow id="f2" sourceRef="approve" targetRef="e" /><endEvent id="e" />`);
    const property = '<camunda:property name="fasti:fourEyes" value="ghost" />';
    // a property of a form field is not a rule of the task
    expect(
      messages(
        'bpmn',
        model(`<camunda:formData><camunda:formField id="a" type="string"><camunda:properties>${property}</camunda:properties></camunda:formField></camunda:formData>`),
      ),
    ).toEqual([]);
    // nor is the element of another vendor
    expect(
      messages('bpmn', model(`<acme:properties xmlns:acme="http://acme.example/bpmn">${property}</acme:properties>`)),
    ).toEqual([]);
    expect(messages('bpmn', model(`<camunda:properties>${property}</camunda:properties>`))).toEqual([
      'error readiness.fourEyesSteps Approve',
    ]);
  });

  it('warns about a gateway that may find no way out', () => {
    expect(
      messages(
        'bpmn',
        process(`<startEvent id="s" /><sequenceFlow id="f0" sourceRef="s" targetRef="g" /><exclusiveGateway id="g" name="Amount?" />
          <sequenceFlow id="f1" sourceRef="g" targetRef="e"><conditionExpression>a</conditionExpression></sequenceFlow>
          <sequenceFlow id="f2" sourceRef="g" targetRef="e" /><endEvent id="e" />`),
      ),
    ).toEqual(['error readiness.noCondition Amount?']);
    const gateway = (flows: string, extra = '') =>
      messages(
        'bpmn',
        process(`<startEvent id="s" /><sequenceFlow id="f0" sourceRef="s" targetRef="g" /><exclusiveGateway id="g" ${extra} />${flows}<endEvent id="e" />`),
      );
    const conditional = (id: string) => `<sequenceFlow id="${id}" sourceRef="g" targetRef="e"><conditionExpression>a</conditionExpression></sequenceFlow>`;
    const plain = (id: string) => `<sequenceFlow id="${id}" sourceRef="g" targetRef="e" />`;
    expect(gateway(conditional('f1'))).toEqual(['error readiness.gatewaySingleCondition g']);
    expect(gateway(conditional('f1') + conditional('f2'), 'default="f2"')).toEqual(['error readiness.gatewayDefaultCondition g']);
    expect(gateway(conditional('f1') + plain('f2'), 'default="f2"')).toEqual([]);
    expect(gateway(plain('f1'))).toEqual([]);
  });

  it('accepts every way the engine knows to implement a task', () => {
    const steps = (task: string) =>
      messages('bpmn', process(`<startEvent id="s" /><sequenceFlow id="f1" sourceRef="s" targetRef="t" />${task}<sequenceFlow id="f2" sourceRef="t" targetRef="e" /><endEvent id="e" />`));
    expect(steps('<serviceTask id="t" camunda:type="mail" />')).toEqual([]);
    expect(steps('<sendTask id="t" camunda:class="org.example.Send" />')).toEqual([]);
    expect(steps('<businessRuleTask id="t" camunda:decisionRef="approve" />')).toEqual([]);
    expect(steps('<businessRuleTask id="t" camunda:type="external" />')).toEqual(['error readiness.noTopic t']);
  });

  it('checks decisions and forms', () => {
    expect(messages('dmn', blankDecision('d1'))).toEqual([]);
    expect(messages('dmn', '<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" />')).toEqual(['error readiness.noDecisions']);
    expect(messages('form', blankForm('f1'))).toEqual([]);
    expect(messages('form', '{"id":"f","components":[]}')).toEqual(['warning readiness.emptyForm']);
    expect(messages('form', '{"components":[{"type":"textfield"}]}')).toEqual(['error readiness.noFormId']);
    expect(messages('form', '{')).toEqual(['error readiness.invalidJson']);
  });
});
