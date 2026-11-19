/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { copyPng, diagramSvg } from './exportImage';

describe('diagram image', () => {
  it('cuts the shown diagram to its bounds, undoes the zoom and drops editing helpers', () => {
    const markup = `
      <svg xmlns="http://www.w3.org/2000/svg" class="canvas"><g class="viewport" transform="matrix(2 0 0 2 10 10)">
        <g class="djs-element is-failed"><g class="djs-visual"><rect width="100" height="80" style="fill: red"></rect></g>
          <rect class="djs-hit"></rect><rect class="djs-outline"></rect></g>
      </g></svg>`;
    const svg = document.importNode(new DOMParser().parseFromString(markup, 'image/svg+xml').documentElement, true) as unknown as SVGSVGElement;
    document.body.append(svg);
    const out = diagramSvg(svg, { x: 150, y: 80, width: 100, height: 80 }, '#ffffff');
    const parsed = new DOMParser().parseFromString(out, 'image/svg+xml').documentElement;
    expect(parsed.getAttribute('viewBox')).toBe('126 56 148 128');
    expect(parsed.querySelector('.viewport')?.getAttribute('transform')).toBeNull();
    expect(parsed.querySelector('.djs-hit, .djs-outline')).toBeNull();
    expect(parsed.querySelector('.djs-visual rect')?.getAttribute('style')).toContain('fill');
    // a background, so the image reads on a dark page or document too
    expect(parsed.firstElementChild?.getAttribute('fill')).toBe('#ffffff');
    // the live diagram stays as it was
    expect(svg.querySelector('.djs-hit')).not.toBeNull();
  });

  it('reports when the browser cannot copy images', async () => {
    expect(await copyPng(Promise.resolve(new Blob()))).toBe(false);
  });
});
