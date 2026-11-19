/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * Indents JSON text without reading it into JavaScript values, so numbers keep
 * every digit (JavaScript numbers lose precision beyond 2^53) and strings keep
 * their escapes. Throws like `JSON.parse` if the text is not JSON.
 */
export function formatJson(text: string, indent = 2): string {
  JSON.parse(text);
  const pad = (depth: number) => '\n' + ' '.repeat(depth * indent);
  let out = '';
  let depth = 0;
  let inString = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text.charAt(index);
    if (inString) {
      out += char;
      if (char === '\\') {
        out += text.charAt(index + 1);
        index += 1;
      } else if (char === '"') {
        inString = false;
      }
      continue;
    }
    switch (char) {
      case '"':
        inString = true;
        out += char;
        break;
      case '{':
      case '[': {
        const close = char === '{' ? '}' : ']';
        let next = index + 1;
        while (next < text.length && /\s/.test(text.charAt(next))) next += 1;
        if (text.charAt(next) === close) {
          out += char + close;
          index = next;
        } else {
          depth += 1;
          out += char + pad(depth);
        }
        break;
      }
      case '}':
      case ']':
        depth -= 1;
        out += pad(depth) + char;
        break;
      case ',':
        out += ',' + pad(depth);
        break;
      case ':':
        out += ': ';
        break;
      case ' ':
      case '\t':
      case '\n':
      case '\r':
        break;
      default:
        out += char;
    }
  }
  return out;
}
