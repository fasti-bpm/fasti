/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/** Parts of the canvas that only serve editing and selection. */
const HELPERS = '.djs-hit, .djs-outline, .djs-resizer, .djs-bendpoints, .djs-segment-dragger, .djs-dragger';

const STYLED = ['fill', 'stroke', 'stroke-width', 'stroke-dasharray', 'stroke-opacity', 'fill-opacity', 'opacity', 'font-family', 'font-size', 'font-weight'];

export interface Box {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * The diagram as it is shown, as a standalone SVG: colours of the theme and
 * the markers of states (current, failed, heat) are written into the
 * elements, the zoom is undone and the image is cut to the diagram.
 */
export function diagramSvg(svg: SVGSVGElement, inner: Box, background: string, margin = 24): string {
  const clone = svg.cloneNode(true) as SVGSVGElement;
  // styles come from the live elements, which the cascade has reached
  const source = Array.from(svg.querySelectorAll<SVGElement>('*'));
  const target = Array.from(clone.querySelectorAll<SVGElement>('*'));
  source.forEach((element, index) => {
    const copy = target[index];
    if (!copy || typeof window.getComputedStyle !== 'function') return;
    const computed = window.getComputedStyle(element);
    const style = STYLED.map((name) => {
      const value = computed.getPropertyValue(name);
      return value ? `${name}:${value}` : '';
    })
      .filter(Boolean)
      .join(';');
    if (style) copy.setAttribute('style', style);
  });
  clone.querySelectorAll(HELPERS).forEach((element) => element.remove());
  clone.querySelectorAll('.viewport, .layer-root, [class*="layer-"]').forEach((layer) => layer.removeAttribute('transform'));
  const x = inner.x - margin;
  const y = inner.y - margin;
  const width = Math.max(1, Math.ceil(inner.width + 2 * margin));
  const height = Math.max(1, Math.ceil(inner.height + 2 * margin));
  clone.setAttribute('viewBox', `${x} ${y} ${width} ${height}`);
  clone.setAttribute('width', String(width));
  clone.setAttribute('height', String(height));
  clone.removeAttribute('class');
  clone.removeAttribute('style');
  const back = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  back.setAttribute('x', String(x));
  back.setAttribute('y', String(y));
  back.setAttribute('width', String(width));
  back.setAttribute('height', String(height));
  back.setAttribute('fill', background);
  clone.insertBefore(back, clone.firstChild);
  return new XMLSerializer().serializeToString(clone);
}

/** Draws an SVG into a PNG at the given scale; the SVG goes in as a data URL, which the page policy allows. */
export async function svgToPng(svg: string, scale = 2): Promise<Blob> {
  const image = new Image();
  const loaded = new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error('The diagram could not be drawn as an image'));
  });
  image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  await loaded;
  const canvas = document.createElement('canvas');
  canvas.width = Math.ceil(image.width * scale);
  canvas.height = Math.ceil(image.height * scale);
  const context = canvas.getContext('2d');
  if (!context) throw new Error('No canvas');
  context.scale(scale, scale);
  context.drawImage(image, 0, 0);
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('No image'))), 'image/png'),
  );
}

/** Saves a file through a temporary link. */
export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Copies a PNG to the clipboard; `false` where the browser cannot, e.g. without a secure context. */
export async function copyPng(png: Blob | Promise<Blob>): Promise<boolean> {
  if (typeof ClipboardItem === 'undefined' || !navigator.clipboard?.write) return false;
  // the item takes the promise, so Safari keeps the click as the user gesture
  await navigator.clipboard.write([new ClipboardItem({ 'image/png': png })]);
  return true;
}
