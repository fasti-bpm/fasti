/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { bpmnTexts, bpmnTranslationModules, dmnTranslation, libraryTexts, libraryTranslate, translateModule, withEnglishSearch } from './libraryText';

describe('libraryTranslate', () => {
  it('reads the text of the language and fills in placeholders', () => {
    const translate = libraryTranslate(libraryTexts('vi'));
    expect(translate('Hit policy')).toBe('Chính sách khớp');
    expect(translate('Remove {label}', { label: 'Ô văn bản' })).toBe('Xóa Ô văn bản');
  });

  it('keeps English for a language or text without translation', () => {
    expect(libraryTranslate(libraryTexts('de'))('Hit policy')).toBe('Hit policy');
    expect(libraryTranslate(libraryTexts('vi'))('Column {index} of {count}', { index: 2 })).toBe('Column 2 of {count}');
  });

  it('gives every dmn-js view the service', () => {
    const [kind, translate] = translateModule('vi').translate;
    expect(kind).toBe('value');
    expect(translate('When')).toBe('Khi');
    const options = dmnTranslation('vi');
    expect(options.decisionTable.additionalModules[0]?.translate[1]('Then')).toBe('Thì');
  });
});

describe('bpmn-js texts', () => {
  it('translates the palette for Vietnamese and leaves English alone', () => {
    expect(bpmnTexts('vi')['Create task']).toBe('Tạo tác vụ');
    expect(bpmnTranslationModules('en')).toEqual([]);
    expect(bpmnTranslationModules('vi')).toHaveLength(2);
  });

  it('lets a menu find a translated element by its English name, also inside groups', () => {
    const english = new Map([['Tác vụ người dùng', 'User task']]);
    const entries = withEnglishSearch(
      { a: { label: 'Tác vụ người dùng', search: 'human' }, b: { label: 'Khác' }, g: { label: 'Nhóm', entries: { c: { label: 'Tác vụ người dùng' } } } },
      english,
    );
    expect(entries.a?.search).toEqual(['human', 'User task']);
    expect(entries.b?.search).toBeUndefined();
    expect(entries.g?.entries?.c?.search).toEqual(['User task']);
  });
});
