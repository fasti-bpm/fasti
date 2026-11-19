/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { translate } from '../i18n';
import { describeInstance, nameList } from './summary';

const en = (key: Parameters<typeof translate>[1], params?: Parameters<typeof translate>[2]) => translate('en', key, params);
const vi = (key: Parameters<typeof translate>[1], params?: Parameters<typeof translate>[2]) => translate('vi', key, params);

describe('the plain-language status of an instance', () => {
  it('puts an incident before a suspension and the waiting activities', () => {
    expect(describeInstance({ suspended: true, failing: ['Charge card'], waiting: ['Charge card'] }, 'en', en)).toEqual({
      tone: 'danger',
      text: 'Stopped by an error at “Charge card”. Fix the cause, then retry it in the Incidents tab.',
    });
    expect(describeInstance({ suspended: true, failing: [], waiting: ['Approve'] }, 'en', en).tone).toBe('warning');
    expect(describeInstance({ suspended: false, failing: [], waiting: ['Approve', 'Ship'] }, 'en', en).text).toBe(
      'Waiting at “Approve” and “Ship”.',
    );
    expect(describeInstance({ suspended: false, failing: [], waiting: [] }, 'vi', vi).text).toBe('Đang chạy; hiện không chờ ở đâu.');
  });

  it('names a few activities once each and counts the rest', () => {
    expect(nameList(['A', 'A', 'B', 'C', 'D', 'E'], 'en', en)).toBe('“A”, “B”, “C”, and 2 more');
  });
});
