/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { afterEach, describe, expect, it } from 'vitest';
import { setDensity, setStartPage, setTenantScope, startPage, startPath, tenantFilter, tenantFilterBody } from './userPrefs';

afterEach(() => window.localStorage.clear());

describe('user preferences', () => {
  it('opens only start pages of applications the user may use', () => {
    expect(startPath('home', [])).toBe('/');
    expect(startPath('processes', ['tasklist'])).toBe('/processes');
    expect(startPath('cockpit', ['tasklist'])).toBeNull();
    expect(startPath('admin', ['admin'])).toBe('/admin');
  });

  it('falls back to the home page for unknown values', () => {
    expect(startPage()).toBe('home');
    setStartPage('cockpit');
    expect(startPage()).toBe('cockpit');
    window.localStorage.setItem('fasti.next.startPage', '"javascript:"');
    expect(startPage()).toBe('home');
  });

  it('marks the document for the compact density', () => {
    setDensity('compact');
    expect(document.documentElement.dataset.density).toBe('compact');
    setDensity('comfortable');
    expect(document.documentElement.dataset.density).toBeUndefined();
  });

  it('narrows queries to the chosen tenant', () => {
    expect(tenantFilter()).toEqual({});
    setTenantScope('t1');
    expect(tenantFilter()).toEqual({ tenantIdIn: 't1' });
    expect(tenantFilterBody()).toEqual({ tenantIdIn: ['t1'] });
    setTenantScope(null);
    expect(tenantFilterBody()).toEqual({});
  });
});
