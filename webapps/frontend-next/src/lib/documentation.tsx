/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ReactNode } from 'react';

/** The documentation the modeler wrote for a step and for its process. */
export interface ModelDocumentation {
  step: string | null;
  process: string | null;
}

function textOf(element: Element | undefined): string | null {
  const documentation = Array.from(element?.children ?? []).find((child) => child.localName === 'documentation');
  const text = documentation?.textContent?.trim();
  return text ? text : null;
}

/** Reads the `documentation` of an element and of the process that holds it; the model is only read, never shown. */
export function modelDocumentation(xml: string | undefined, elementId: string | null | undefined): ModelDocumentation {
  if (!xml) return { step: null, process: null };
  try {
    const model = new DOMParser().parseFromString(xml, 'application/xml');
    const all = Array.from(model.getElementsByTagName('*'));
    const element = elementId ? all.find((candidate) => candidate.getAttribute('id') === elementId) : undefined;
    let process: Element | null | undefined = element;
    while (process && process.localName !== 'process') process = process.parentElement;
    process ??= all.find((candidate) => candidate.localName === 'process' && candidate.getAttribute('isExecutable') !== 'false');
    return { step: textOf(element), process: textOf(process ?? undefined) };
  } catch {
    return { step: null, process: null };
  }
}

/**
 * The value of an extension property of an element (`camunda:property` in
 * `camunda:properties`), as the modeler set it; `null` if there is none. The
 * value attribute wins over text content.
 */
export function extensionProperty(xml: string | undefined, elementId: string | null | undefined, name: string): string | null {
  if (!xml || !elementId) return null;
  try {
    const model = new DOMParser().parseFromString(xml, 'application/xml');
    const element = Array.from(model.getElementsByTagName('*')).find((candidate) => candidate.getAttribute('id') === elementId);
    const extensions = Array.from(element?.children ?? []).find((child) => child.localName === 'extensionElements');
    const properties = Array.from(extensions?.children ?? []).filter((child) => child.localName === 'properties');
    for (const property of properties.flatMap((list) => Array.from(list.children))) {
      if (property.localName === 'property' && property.getAttribute('name') === name) {
        const value = property.getAttribute('value') ?? property.textContent;
        return value?.trim() || null;
      }
    }
    return null;
  } catch {
    return null;
  }
}

const SAFE_LINK = /^(https?:\/\/|mailto:)/i;

/** `**bold**`, `` `code` `` and `[text](https://…)` within a line; anything else stays text. */
function inline(text: string, key: string): ReactNode[] {
  const parts: ReactNode[] = [];
  const pattern = /\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)/g;
  let last = 0;
  let index = 0;
  for (const match of text.matchAll(pattern)) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    const id = `${key}-${index++}`;
    if (match[1] !== undefined) parts.push(<strong key={id}>{match[1]}</strong>);
    else if (match[2] !== undefined) parts.push(<code key={id}>{match[2]}</code>);
    else if (match[3] !== undefined && match[4] !== undefined && SAFE_LINK.test(match[4]))
      parts.push(
        <a key={id} href={match[4]} target="_blank" rel="noopener noreferrer">
          {match[3]}
        </a>,
      );
    else parts.push(match[0]);
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

/**
 * Documentation as a modeler writes it: paragraphs, `-` or `*` lists and a
 * few inline marks. Built from React elements, so no markup of the model
 * reaches the page.
 */
export function DocumentationText({ text }: { text: string }) {
  const blocks = text.replace(/\r\n/g, '\n').split(/\n\s*\n/);
  return (
    <div className="doc-text">
      {blocks.map((block, blockIndex) => {
        const lines = block.split('\n').filter((line) => line.trim());
        if (lines.length > 0 && lines.every((line) => /^\s*[-*]\s+/.test(line))) {
          return (
            <ul key={blockIndex}>
              {lines.map((line, lineIndex) => (
                <li key={lineIndex}>{inline(line.replace(/^\s*[-*]\s+/, ''), `${blockIndex}-${lineIndex}`)}</li>
              ))}
            </ul>
          );
        }
        return (
          <p key={blockIndex}>
            {lines.map((line, lineIndex) => (
              <span key={lineIndex}>
                {lineIndex > 0 && <br />}
                {inline(line.trim(), `${blockIndex}-${lineIndex}`)}
              </span>
            ))}
          </p>
        );
      })}
    </div>
  );
}
