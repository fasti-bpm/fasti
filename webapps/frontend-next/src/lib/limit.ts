/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/** How many engine calls a bulk action keeps open at once, so a large batch does not flood the server. */
export const BULK_CONCURRENCY = 4;

/**
 * Runs `work` for every item with at most `limit` calls running at once and
 * settles like `Promise.allSettled`, in the order of the items.
 */
export async function settleLimited<T, R>(
  items: readonly T[],
  work: (item: T) => Promise<R>,
  limit: number = BULK_CONCURRENCY,
): Promise<PromiseSettledResult<R>[]> {
  const results: PromiseSettledResult<R>[] = new Array(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const index = next++;
      try {
        results[index] = { status: 'fulfilled', value: await work(items[index] as T) };
      } catch (reason) {
        results[index] = { status: 'rejected', reason };
      }
    }
  };
  await Promise.all(Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, worker));
  return results;
}

/** Rejects with the first failure, for callers that report a bulk action as one success or failure. */
export function throwIfAnyFailed<R>(results: PromiseSettledResult<R>[]): R[] {
  const failed = results.find((result): result is PromiseRejectedResult => result.status === 'rejected');
  if (failed) throw failed.reason;
  return results.map((result) => (result as PromiseFulfilledResult<R>).value);
}
