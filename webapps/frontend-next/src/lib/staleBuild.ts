/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */

/**
 * A page left open across an update of the server still names the files of the old build. The
 * parts it loads later (a tab, a diagram, a page) are gone then and fail to load. Such a failure
 * is told apart from others, so that it is shown where it happened, with the advice to reload,
 * instead of replacing the whole page and what the user entered there.
 */

/** The errors Vite reported as a part of the build that could not be loaded. */
const reported = new WeakSet<object>();

/** Messages of browsers for a module or a style sheet that could not be loaded. */
const CHUNK_FAILURE =
  /dynamically imported module|Importing a module script failed|Unable to preload CSS|Failed to load module script/i;

export function isChunkLoadError(error: unknown): boolean {
  return (
    error instanceof Error &&
    (reported.has(error) || CHUNK_FAILURE.test(error.message) || error.name === 'ChunkLoadError')
  );
}

/**
 * Listens for the event Vite sends when a part of the build fails to load. The page is not
 * reloaded by itself: that would throw away a task form being filled in. The part that failed
 * says so in its place, and the user reloads when it suits them.
 */
export function watchStaleBuild(target: Window = window): () => void {
  const listener = (event: Event) => {
    const payload = (event as Event & { payload?: unknown }).payload;
    if (payload instanceof Error) reported.add(payload);
  };
  target.addEventListener('vite:preloadError', listener);
  return () => target.removeEventListener('vite:preloadError', listener);
}
