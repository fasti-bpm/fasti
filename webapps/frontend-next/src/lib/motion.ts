/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
/**
 * How a scroll started by the application moves: smoothly, unless the user
 * asked the system for less motion. The style sheets cover animations and
 * transitions; a scroll requested from code needs this.
 */
export function scrollBehavior(): ScrollBehavior {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ? 'auto'
    : 'smooth';
}
