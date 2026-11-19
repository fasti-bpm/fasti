/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DocumentationText, modelDocumentation } from './documentation';

const XML = `<?xml version="1.0"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <bpmn:process id="loan" isExecutable="true">
    <bpmn:documentation>Loans up to 1 bn VND.</bpmn:documentation>
    <bpmn:subProcess id="sub">
      <bpmn:userTask id="review" name="Review">
        <bpmn:documentation>Check the **payslip**.
- ID card
- Contract</bpmn:documentation>
      </bpmn:userTask>
    </bpmn:subProcess>
    <bpmn:userTask id="plain" />
  </bpmn:process>
</bpmn:definitions>`;

describe('model documentation', () => {
  it('reads the documentation of a step, also inside a sub-process, and of its process', () => {
    expect(modelDocumentation(XML, 'review')).toEqual({
      step: 'Check the **payslip**.\n- ID card\n- Contract',
      process: 'Loans up to 1 bn VND.',
    });
    expect(modelDocumentation(XML, 'plain')).toEqual({ step: null, process: 'Loans up to 1 bn VND.' });
    expect(modelDocumentation(undefined, 'review')).toEqual({ step: null, process: null });
  });

  it('shows a few marks and lists, but never markup or unsafe links', () => {
    render(
      <DocumentationText
        text={'See **this** and [the guide](https://example.org/guide) <img src=x onerror=alert(1)>\n\n- one\n- two\n\n[bad](javascript:alert(1))'}
      />,
    );
    expect(screen.getByText('this').tagName).toBe('STRONG');
    expect(screen.getByRole('link', { name: 'the guide' })).toHaveAttribute('href', 'https://example.org/guide');
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    expect(document.querySelector('img')).toBeNull();
    expect(screen.queryByRole('link', { name: 'bad' })).toBeNull();
    expect(screen.getByText('[bad](javascript:alert(1))')).toBeInTheDocument();
  });
});
