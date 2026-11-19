/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useId, useState, type InputHTMLAttributes } from 'react';
import { useQuery } from '@tanstack/react-query';
import { suggestIdentities } from '../api/engine';

const DELAY = 250;

/**
 * A text field for the id of a user or group that suggests matching users or
 * groups while typing. The suggestions are a native list of the browser: they
 * work with the keyboard and screen readers, and any id can still be typed.
 */
export function IdentityInput({
  kind,
  value,
  onChange,
  ...props
}: {
  kind: 'user' | 'group';
  value: string;
  onChange: (value: string) => void;
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'list'>) {
  const listId = useId();
  const [term, setTerm] = useState(value.trim());
  useEffect(() => {
    const timer = window.setTimeout(() => setTerm(value.trim()), DELAY);
    return () => window.clearTimeout(timer);
  }, [value]);
  const suggestions = useQuery({
    queryKey: ['identities', kind, term],
    queryFn: ({ signal }) => suggestIdentities(kind, term, signal),
    enabled: term.length > 0,
    staleTime: 30_000,
  });
  return (
    <>
      <input
        {...props}
        className="input"
        spellCheck={false}
        autoCapitalize="none"
        autoComplete="off"
        list={listId}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      <datalist id={listId}>
        {(suggestions.data ?? []).map((entry) => (
          <option key={entry.id} value={entry.id}>
            {entry.name && entry.name !== entry.id ? entry.name : undefined}
          </option>
        ))}
      </datalist>
    </>
  );
}
