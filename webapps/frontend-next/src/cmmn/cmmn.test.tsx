/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { Providers } from '../app/App';
import CmmnDiagram, { drawingOrder } from './CmmnDiagram';
import { isPlanItem, parseCaseDiagram, planItemsOf, wrapText } from './model';

// the case of the end-to-end tests: two human tasks, a stage with a task and a milestone
const FIXTURE = readFileSync(join(__dirname, '..', '..', 'e2e', 'fixtures', 'e2e-case.cmmn'), 'utf8');

describe('reading a case diagram', () => {
  it('finds the shapes, their kinds and their rules', () => {
    const diagram = parseCaseDiagram(FIXTURE, 'e2e_case');
    expect(diagram.caseKey).toBe('e2e_case');
    expect(diagram.caseName).toBe('E2E claim case');
    const byId = new Map(diagram.shapes.map((shape) => [shape.id, shape]));
    expect(byId.get('CasePlanModel_e2e')).toMatchObject({ kind: 'casePlanModel', name: 'Claim' });
    expect(byId.get('PI_review')).toMatchObject({ kind: 'humanTask', name: 'Review claim', manual: false, definitionId: 'HumanTask_review' });
    expect(byId.get('PI_check')).toMatchObject({ kind: 'humanTask', manual: true });
    expect(byId.get('PI_settle')).toMatchObject({ kind: 'stage', name: 'Settlement' });
    expect(byId.get('PI_pay')).toMatchObject({ kind: 'humanTask', manual: true });
    expect(byId.get('PI_approved')).toMatchObject({ kind: 'milestone' });
    expect(byId.get('Entry_settle')).toMatchObject({ kind: 'entryCriterion' });
    expect(byId.get('PI_review')?.bounds).toEqual({ x: 200, y: 130, width: 120, height: 80 });
    expect(diagram.shapes.filter(isPlanItem)).toHaveLength(5);
  });

  it('finds the connectors with the events they show', () => {
    const diagram = parseCaseDiagram(FIXTURE);
    expect(diagram.edges).toHaveLength(2);
    expect(diagram.edges[0]).toMatchObject({ kind: 'connector', label: 'complete' });
    expect(diagram.edges[1]?.points).toHaveLength(5);
    // the tab of the plan model is drawn above its border
    expect(diagram.box).toEqual({ x: 150, y: 36, width: 640, height: 464 });
  });

  it('finds the plan item of a task by the plan item or by its human task', () => {
    const diagram = parseCaseDiagram(FIXTURE);
    expect(planItemsOf(diagram, 'PI_review')).toEqual(['PI_review']);
    expect(planItemsOf(diagram, 'HumanTask_pay')).toEqual(['PI_pay']);
    expect(planItemsOf(diagram, null)).toEqual([]);
  });

  it('takes the rules of the definition when a plan item has no controls of its own', () => {
    const xml = `<definitions xmlns="http://www.omg.org/spec/CMMN/20151109/MODEL"
        xmlns:cmmndi="http://www.omg.org/spec/CMMN/20151109/CMMNDI" xmlns:dc="http://www.omg.org/spec/CMMN/20151109/DC">
      <case id="c"><casePlanModel id="m">
        <planItem id="p" definitionRef="t" />
        <humanTask id="t" name="Approve" isBlocking="false">
          <defaultControl><requiredRule /><repetitionRule /></defaultControl>
        </humanTask>
      </casePlanModel></case>
      <cmmndi:CMMNDI><cmmndi:CMMNDiagram id="d">
        <cmmndi:CMMNShape id="s" cmmnElementRef="p"><dc:Bounds x="0" y="0" width="100" height="80" /></cmmndi:CMMNShape>
      </cmmndi:CMMNDiagram></cmmndi:CMMNDI>
    </definitions>`;
    const [shape] = parseCaseDiagram(xml).shapes;
    expect(shape).toMatchObject({ name: 'Approve', required: true, repeatable: true, manual: false, nonBlocking: true });
  });

  it('reads models of CMMN 1.0, which the engine accepts as well', () => {
    const xml = `<definitions xmlns="http://www.omg.org/spec/CMMN/20131201/MODEL"
        xmlns:cmmndi="http://www.omg.org/spec/CMMN/20151109/CMMNDI" xmlns:dc="http://www.omg.org/spec/CMMN/20151109/DC">
      <case id="old" name="Old case"><casePlanModel id="m"><planItem id="p" definitionRef="t" /><humanTask id="t" name="Check" /></casePlanModel></case>
      <cmmndi:CMMNDI><cmmndi:CMMNDiagram id="d">
        <cmmndi:CMMNShape id="s" cmmnElementRef="p"><dc:Bounds x="0" y="0" width="100" height="80" /></cmmndi:CMMNShape>
      </cmmndi:CMMNDiagram></cmmndi:CMMNDI>
    </definitions>`;
    const diagram = parseCaseDiagram(xml, 'old');
    expect(diagram.caseName).toBe('Old case');
    expect(diagram.shapes).toEqual([expect.objectContaining({ id: 'p', kind: 'humanTask', name: 'Check' })]);
  });

  it('draws outer containers before what they contain, whatever the order of the file', () => {
    const diagram = parseCaseDiagram(FIXTURE);
    const byId = new Map(diagram.shapes.map((shape) => [shape.id, shape]));
    const inner = { ...(byId.get('PI_settle') as (typeof diagram.shapes)[number]), id: 'inner', bounds: { x: 450, y: 110, width: 100, height: 60 } };
    const order = drawingOrder([byId.get('Entry_settle'), byId.get('PI_pay'), inner, byId.get('PI_settle'), byId.get('CasePlanModel_e2e')].filter((shape) => !!shape) as typeof diagram.shapes).map((shape) => shape.id);
    expect(order).toEqual(['CasePlanModel_e2e', 'PI_settle', 'inner', 'PI_pay', 'Entry_settle']);
  });

  it('yields no shapes for a model without layout and refuses what is no XML', () => {
    const xml = '<definitions xmlns="http://www.omg.org/spec/CMMN/20151109/MODEL"><case id="c" /></definitions>';
    expect(parseCaseDiagram(xml).shapes).toEqual([]);
    expect(() => parseCaseDiagram('<definitions')).toThrow();
  });

  it('wraps names into lines that fit and cuts the rest', () => {
    expect(wrapText('Review claim', 200, 12)).toEqual(['Review claim']);
    expect(wrapText('Review the claim of the customer', 90, 12)).toEqual(['Review the', 'claim of the', 'customer']);
    const many = wrapText('one two three four five six seven eight', 30, 12, 2);
    expect(many).toHaveLength(2);
    expect(many[1]?.endsWith('…')).toBe(true);
    // scripts without spaces break between characters
    expect(wrapText('審査と承認を行う', 30, 12).length).toBeGreaterThan(1);
  });
});

function renderDiagram(props: Partial<Parameters<typeof CmmnDiagram>[0]> = {}) {
  return render(
    <Providers queryClient={new QueryClient()} locale="en">
      <CmmnDiagram xml={FIXTURE} {...props} />
    </Providers>,
  );
}

describe('the case diagram', () => {
  it('draws the plan items with their names and states', () => {
    const { container } = renderDiagram({ markers: [{ id: 'PI_review', marker: 'is-active' }], label: 'Claim diagram' });
    expect(screen.getByRole('img', { name: 'Claim diagram' })).toBeInTheDocument();
    expect(screen.getAllByText('Review claim').length).toBeGreaterThan(0);
    const review = container.querySelector('[data-element-id="PI_review"]');
    expect(review).toHaveClass('cmmn-shape--humanTask', 'is-active');
    // criteria and the plan model are drawn but cannot be chosen
    expect(container.querySelector('.cmmn-shape--entryCriterion')).not.toHaveAttribute('data-element-id');
    expect(container.querySelectorAll('.cmmn-edge')).toHaveLength(2);
  });

  it('puts numbers on plan items', () => {
    const { container } = renderDiagram({ badges: [{ id: 'PI_check', count: 3 }, { id: 'PI_pay', count: 0 }] });
    const badges = container.querySelectorAll('.cmmn-badge');
    expect(badges).toHaveLength(1);
    expect(badges[0]).toHaveTextContent('3');
  });

  it('lets plan items be chosen with the mouse and the keyboard', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    const { container } = renderDiagram({ onSelect, selected: ['PI_check'], describe: (id) => (id === 'PI_check' ? 'Enabled' : null) });
    const check = screen.getByRole('button', { name: 'Check documents · Enabled' });
    expect(check).toHaveAttribute('aria-pressed', 'true');
    expect(check).toHaveClass('is-selected');

    const review = screen.getByRole('button', { name: 'Review claim' });
    review.focus();
    await user.keyboard('{Enter}');
    expect(onSelect).toHaveBeenLastCalledWith('PI_review');

    const svg = container.querySelector('svg.cmmn-diagram__svg') as SVGSVGElement;
    await user.pointer([{ keys: '[MouseLeft]', target: check }]);
    expect(onSelect).toHaveBeenLastCalledWith('PI_check');
    await user.pointer([{ keys: '[MouseLeft]', target: svg }]);
    expect(onSelect).toHaveBeenLastCalledWith(null);
  });

  it('says so when the model has no layout', () => {
    render(
      <Providers queryClient={new QueryClient()} locale="en">
        <CmmnDiagram xml='<definitions xmlns="http://www.omg.org/spec/CMMN/20151109/MODEL"><case id="c" /></definitions>' />
      </Providers>,
    );
    expect(screen.getByText(/has no diagram layout/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Zoom in' })).toBeNull();
  });
});
