/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useSyncExternalStore } from 'react';
import type { Batch } from '../api/cockpit';

/** What a bulk change on many instances started: the batch that does the work in the background. */
export interface Receipt {
  batchId: string;
  /** the change in the words of the user, e.g. "Suspend 12 instances" */
  label: string;
  /** when the batch was started, in milliseconds */
  at: number;
}

const KEY = 'fasti.next.receipts';
const LIMIT = 5;
const listeners = new Set<() => void>();

function read(): Receipt[] {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(KEY) ?? '[]');
    return Array.isArray(value)
      ? value.filter(
          (item): item is Receipt =>
            !!item && typeof item.batchId === 'string' && typeof item.label === 'string' && typeof item.at === 'number',
        )
      : [];
  } catch {
    return [];
  }
}

let receipts: Receipt[] = read();

function write(next: Receipt[]): void {
  receipts = next;
  try {
    sessionStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    // the receipts then last for this page only
  }
  listeners.forEach((listener) => listener());
}

/** Keeps a receipt of a started batch; the newest first, a few at most. */
export function addReceipt(batch: Batch | unknown, label: string): void {
  if (!batch || typeof batch !== 'object' || typeof (batch as Batch).id !== 'string') {
    return;
  }
  const batchId = (batch as Batch).id;
  write([{ batchId, label, at: Date.now() }, ...receipts.filter((item) => item.batchId !== batchId)].slice(0, LIMIT));
}

/** Forgets every receipt, e.g. when the user signs out. */
export function clearReceipts(): void {
  write([]);
}

export function dismissReceipt(batchId: string): void {
  write(receipts.filter((item) => item.batchId !== batchId));
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useReceipts(): Receipt[] {
  return useSyncExternalStore(subscribe, () => receipts);
}

/** Runs a call that starts a batch and keeps a receipt of it; the result passes through. */
export function withReceipt(call: () => Promise<unknown>, label: string): () => Promise<unknown> {
  return async () => {
    const result = await call();
    addReceipt(result, label);
    return result;
  };
}
