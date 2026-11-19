/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { describe, expect, it } from 'vitest';
import { fakeServer, json } from '../test/server';
import type { TaskListItem } from '../api/types';
import { decodeSearch, encodeSearch, getCardVariables, normaliseCardVariables, searchLink, withCardVariables } from './searchLinks';

describe('search links', () => {
  it('round-trips criteria through a link', () => {
    const criteria = [
      { id: 'a', field: 'name' as const, value: 'Approve' },
      { id: 'b', field: 'processVariable' as const, variable: 'amount', operator: 'gt' as const, value: '100' },
      { id: 'c', field: 'assignee' as const, value: '  ' },
    ];
    const decoded = decodeSearch(encodeSearch(criteria, true));
    expect(decoded?.onlyMine).toBe(true);
    expect(decoded?.criteria.map(({ field, value, variable, operator }) => ({ field, value, variable, operator }))).toEqual([
      { field: 'name', value: 'Approve', variable: undefined, operator: undefined },
      { field: 'processVariable', value: '100', variable: 'amount', operator: 'gt' },
    ]);
  });

  it('drops what a link must not carry', () => {
    expect(decodeSearch('not json')).toBeNull();
    expect(decodeSearch('{"c":[["evil","x"],["processVariable","1"],["name",5]]}')?.criteria).toEqual([]);
    expect(decodeSearch('{"c":[["taskVariable","1","v","drop table"]]}')?.criteria[0]?.operator).toBe('eq');
  });

  it('builds a hash link with filter and search', () => {
    const link = searchLink('https://host/fasti/app/', 'f1', [{ id: 'a', field: 'name', value: 'x' }], false);
    const params = new URLSearchParams(link.split('?')[1]);
    expect(link.startsWith('https://host/fasti/app/#/tasklist?')).toBe(true);
    expect(params.get('filter')).toBe('f1');
    expect(decodeSearch(params.get('q'))?.criteria[0]?.value).toBe('x');
  });
});

describe('variables on task cards', () => {
  it('keeps names unique and few', () => {
    expect(normaliseCardVariables([' a', 'b', 'a', '', ...'cdefghij'.split('')])).toEqual(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']);
  });

  it('adds the values of the instance without replacing those of the filter', () => {
    const task = { id: 't', processInstanceId: 'p', variables: [{ name: 'a', value: 1 }] } as unknown as TaskListItem;
    const [result] = withCardVariables(
      [task],
      new Map([['p', [{ name: 'a', value: 2 }, { name: 'b', value: 3 }]]]),
    );
    expect(result?.variables).toEqual([
      { name: 'a', value: 1 },
      { name: 'b', value: 3 },
    ]);
  });

  it('reads only values of the instance scope, page by page', async () => {
    const server = fakeServer({
      'POST /engine/engine/default/variable-instance': (request) => {
        const first = Number(request.query.get('firstResult'));
        const page = first === 0
          ? Array.from({ length: 500 }, (_, index) => ({ name: 'amount', type: 'Integer', value: index, processInstanceId: `p${index}` }))
          : [{ name: 'amount', type: 'Integer', value: 7, processInstanceId: 'p500' }];
        return json(page);
      },
    });
    const tasks = Array.from({ length: 501 }, (_, index) => ({ id: `t${index}`, processInstanceId: `p${index}`, variables: [] }) as unknown as TaskListItem);
    const values = await getCardVariables(tasks, ['amount']);
    const requests = server.find('POST', '/engine/engine/default/variable-instance');
    expect(requests).toHaveLength(2);
    expect(requests[0]?.body).toEqual({ variableScopeIdIn: tasks.map((task) => task.processInstanceId), variableName: 'amount' });
    expect(values.get('p500')).toEqual([{ name: 'amount', type: 'Integer', value: 7 }]);
    expect(values.size).toBe(501);
  });
});
