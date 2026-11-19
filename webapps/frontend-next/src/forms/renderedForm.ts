/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * For form fields that are defined in a process model, the engine generates
 * markup that is meant for the classic Tasklist. The markup is never shown:
 * it is parsed in a detached document and only the description of the fields
 * is taken from it, which is then rendered with the controls of this
 * application.
 */
export interface GeneratedField {
  name: string;
  label: string;
  /** type of the variable: String, Boolean, Long, ... */
  type: string;
  control: 'text' | 'checkbox' | 'select' | 'date';
  options: { value: string; label: string }[];
  defaultValue: string | null;
  /** pattern of a date field as understood by the engine, e.g. dd/MM/yyyy */
  datePattern: string | null;
  businessKey: boolean;
  required: boolean;
  readOnly: boolean;
  min: number | null;
  max: number | null;
  minLength: number | null;
  maxLength: number | null;
}

function numberAttribute(element: Element, name: string): number | null {
  const value = element.getAttribute(name);
  if (value === null || value.trim() === '') {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

export function parseRenderedForm(markup: string): GeneratedField[] {
  const document = new DOMParser().parseFromString(markup, 'text/html');
  const fields: GeneratedField[] = [];

  for (const element of document.querySelectorAll('[cam-variable-name], [cam-business-key]')) {
    const businessKey = element.hasAttribute('cam-business-key');
    const name = element.getAttribute('cam-variable-name') ?? element.getAttribute('name');
    if (!name) {
      continue;
    }
    const tag = element.tagName.toLowerCase();
    const datePattern = element.getAttribute('uib-datepicker-popup');
    const inputType = element.getAttribute('type');
    let label = '';
    for (const candidate of document.querySelectorAll('label[for]')) {
      if (candidate.getAttribute('for') === name) {
        label = candidate.textContent?.trim() ?? '';
        break;
      }
    }

    fields.push({
      name,
      label: label || name,
      type: element.getAttribute('cam-variable-type') ?? 'String',
      control:
        tag === 'select'
          ? 'select'
          : inputType === 'checkbox'
            ? 'checkbox'
            : datePattern
              ? 'date'
              : 'text',
      options:
        tag === 'select'
          ? Array.from(element.querySelectorAll('option')).map((option) => ({
              value: option.getAttribute('value') ?? '',
              label: option.textContent?.trim() ?? '',
            }))
          : [],
      defaultValue: tag === 'select' ? null : element.getAttribute('value'),
      datePattern,
      businessKey,
      required: element.hasAttribute('required'),
      readOnly: element.hasAttribute('disabled') || element.hasAttribute('readonly'),
      min: numberAttribute(element, 'min'),
      max: numberAttribute(element, 'max'),
      minLength: numberAttribute(element, 'minlength'),
      maxLength: numberAttribute(element, 'maxlength'),
    });
  }
  return fields;
}

const DAY_FIRST = 'dd/MM/yyyy';

/** `yyyy-MM-dd` (value of a date input) to the date pattern of the engine. */
export function toEnginePattern(isoDate: string, pattern: string | null): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!match || pattern !== DAY_FIRST) {
    return isoDate;
  }
  return `${match[3]}/${match[2]}/${match[1]}`;
}

/** The value of a date variable or a default value as `yyyy-MM-dd`. */
export function toIsoDate(value: unknown): string {
  if (typeof value !== 'string') {
    return '';
  }
  const iso = /^(\d{4}-\d{2}-\d{2})/.exec(value);
  if (iso?.[1]) {
    return iso[1];
  }
  const dayFirst = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  return dayFirst ? `${dayFirst[3]}-${dayFirst[2]}-${dayFirst[1]}` : '';
}

export function supportsDateInput(pattern: string | null): boolean {
  return pattern === DAY_FIRST;
}
