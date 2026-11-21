/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState, type ReactNode } from 'react';
import { Search } from 'lucide-react';
import { useI18n } from '../i18n';
import { Button, Dialog } from './index';

// Confirmations, check boxes and search fields shared by the pages.

/**
 * Asks before something is changed that cannot be taken back. The title names
 * the action, the text names the object and what happens to it.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  text,
  confirmLabel,
  danger,
  busy,
  disabled,
  onConfirm,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  text: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  disabled?: boolean;
  onConfirm: () => void;
  /** options of the action */
  children?: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title}>
      <form
        className="confirm"
        onSubmit={(event) => {
          event.preventDefault();
          onConfirm();
        }}
      >
        <p className="confirm__text">{text}</p>
        {children && <div className="confirm__options">{children}</div>}
        <div className="confirm__actions">
          <Button onClick={() => onOpenChange(false)}>{t('action.cancel')}</Button>
          <Button
            type="submit"
            variant={danger ? 'danger' : 'primary'}
            busy={busy}
            disabled={disabled}
          >
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

export function Checkbox({
  label,
  hint,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <label className="option">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>
        <span className="option__label">{label}</span>
        {hint && <span className="option__hint">{hint}</span>}
      </span>
    </label>
  );
}

export function SearchInput({
  value,
  onChange,
  label,
  id,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
  id?: string;
}) {
  return (
    <div className="search-input">
      <Search aria-hidden size={15} />
      <input
        id={id}
        className="input"
        type="search"
        aria-label={label}
        placeholder={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}

/**
 * A text filter whose value lives elsewhere, e.g. in the address: typed freely, applied with
 * Enter or on leaving the field. A value that arrives from outside (back, a link, cleared
 * filters) replaces what was typed. The field itself stays, so that applying a filter with
 * Enter keeps the keyboard focus in it.
 */
export function TextFilter({
  value,
  onApply,
  label,
}: {
  value: string;
  onApply: (value: string) => void;
  label: string;
}) {
  const [draft, setDraft] = useState(value);
  const [applied, setApplied] = useState(value);
  if (applied !== value) {
    setApplied(value);
    setDraft(value);
  }
  return (
    <input
      className="input"
      aria-label={label}
      placeholder={label}
      value={draft}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={() => onApply(draft)}
      onKeyDown={(event) => event.key === 'Enter' && onApply(draft)}
    />
  );
}

/** How many items a confirmation names before it says how many more there are. */
export const SAMPLE_SIZE = 5;

/**
 * A few of the items an action changes, so that the user sees what the
 * number in the confirmation stands for before it is too late.
 */
export function SampleList({ items, total }: { items: string[] | undefined; total: number }) {
  const { t, number } = useI18n();
  if (!items) {
    return <p className="sample sample--loading muted">{t('ops.sample.loading')}</p>;
  }
  if (items.length === 0) {
    return null;
  }
  const shown = items.slice(0, SAMPLE_SIZE);
  return (
    <div className="sample">
      <p className="sample__label">{t('ops.sample.label')}</p>
      <ul className="sample__list">
        {shown.map((item, index) => (
          <li key={`${index}-${item}`} className="sample__item">
            {item}
          </li>
        ))}
      </ul>
      {total > shown.length && <p className="sample__more muted">{t('ops.sample.more', { count: number(total - shown.length) })}</p>}
    </div>
  );
}
