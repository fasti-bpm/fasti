/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId } from 'react';
import { LOGO_MARK } from './logoMark';

/** The Fasti monogram: a flow that starts, branches and reaches a state. */
export function Monogram({ size = 28 }: { size?: number }) {
  // gradient ids must be unique when the mark appears more than once
  const id = useId();
  const tileId = `${id}-tile`;
  const flowId = `${id}-flow`;
  const { colors } = LOGO_MARK;
  return (
    <svg
      className="monogram"
      width={size}
      height={size}
      viewBox={LOGO_MARK.viewBox}
      role="img"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={tileId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={colors.tile[0]} />
          <stop offset="1" stopColor={colors.tile[1]} />
        </linearGradient>
        <linearGradient id={flowId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={colors.flow[0]} />
          <stop offset="1" stopColor={colors.flow[1]} />
        </linearGradient>
      </defs>
      <rect
        {...LOGO_MARK.tile}
        fill={`url(#${tileId})`}
        stroke={colors.tileRing.color}
        strokeOpacity={colors.tileRing.opacity}
        strokeWidth={colors.tileRing.width}
      />
      <path
        d={LOGO_MARK.flow.d}
        fill="none"
        stroke={`url(#${flowId})`}
        strokeWidth={LOGO_MARK.flow.strokeWidth}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {LOGO_MARK.states.map((state) => (
        <circle key={`${state.cx},${state.cy}`} {...state} fill={colors.state} />
      ))}
    </svg>
  );
}

export function Wordmark({ size = 28 }: { size?: number }) {
  return (
    <span className="wordmark">
      <Monogram size={size} />
      <span className="wordmark__text">
        Fasti <span className="wordmark__suffix">BPM</span>
      </span>
    </span>
  );
}
