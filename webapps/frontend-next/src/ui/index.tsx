/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { forwardRef, useId, useLayoutEffect, useRef, useState } from 'react';
import type { ButtonHTMLAttributes, ComponentType, ReactNode } from 'react';
import { Dialog as RadixDialog, Tooltip as RadixTooltip } from 'radix-ui';
import { AlertTriangle, Inbox, LoaderCircle, Lock, SearchX, WifiOff, X } from 'lucide-react';
import type { LucideProps } from 'lucide-react';
import { isApiError } from '../api/http';
import { isQuietFocus, lastMenuTrigger } from './menu';
import { useI18n, type MessageKey, type MessageParams, type Translate } from '../i18n';

export function cx(...parts: (string | false | null | undefined)[]): string {
  return parts.filter(Boolean).join(' ');
}

type Icon = ComponentType<LucideProps>;

// --- buttons ---------------------------------------------------------------

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'danger-ghost';
  size?: 'sm' | 'md';
  icon?: Icon;
  busy?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'secondary', size = 'md', icon: IconComponent, busy, children, className, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type="button"
      {...rest}
      className={cx('btn', `btn--${variant}`, `btn--${size}`, className)}
      aria-busy={busy || undefined}
      disabled={rest.disabled || busy}
    >
      {busy ? (
        <LoaderCircle className="btn__icon spin" aria-hidden size={16} />
      ) : (
        IconComponent && <IconComponent className="btn__icon" aria-hidden size={16} />
      )}
      {children}
    </button>
  );
});

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: Icon;
  /** accessible name, also shown as tooltip */
  label: string;
  shortcut?: string;
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(function IconButton(
  { icon: IconComponent, label, shortcut, className, ...rest },
  ref,
) {
  return (
    <Tooltip label={label} shortcut={shortcut}>
      <button
        ref={ref}
        type="button"
        {...rest}
        aria-label={label}
        className={cx('icon-btn', className)}
      >
        <IconComponent aria-hidden size={18} />
      </button>
    </Tooltip>
  );
});

export function Tooltip({
  label,
  shortcut,
  children,
}: {
  label: string;
  shortcut?: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <RadixTooltip.Root open={open} onOpenChange={(next) => setOpen(next && !isQuietFocus())}>
      <RadixTooltip.Trigger asChild>{children}</RadixTooltip.Trigger>
      <RadixTooltip.Portal>
        <RadixTooltip.Content className="tooltip" sideOffset={6}>
          {label}
          {shortcut && <kbd className="kbd kbd--inverse">{shortcut}</kbd>}
        </RadixTooltip.Content>
      </RadixTooltip.Portal>
    </RadixTooltip.Root>
  );
}

// --- form fields -----------------------------------------------------------

export function Field({
  label,
  error,
  hint,
  required,
  children,
}: {
  label: string;
  error?: string | null;
  hint?: string;
  required?: boolean;
  children: (props: {
    id: string;
    'aria-invalid': boolean | undefined;
    'aria-describedby': string | undefined;
  }) => ReactNode;
}) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className={cx('field', error && 'field--invalid')}>
      <label className="field__label" htmlFor={id}>
        {label}
        {required && (
          <span className="field__required" aria-hidden>
            {' *'}
          </span>
        )}
      </label>
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {error ? (
        <p className="field__error" id={`${id}-error`} role="alert">
          {error}
        </p>
      ) : (
        hint && (
          <p className="field__hint" id={`${id}-hint`}>
            {hint}
          </p>
        )
      )}
    </div>
  );
}

// --- states ----------------------------------------------------------------

export function Skeleton({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cx('skeleton', className)} aria-hidden>
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} className="skeleton__line" />
      ))}
    </div>
  );
}

/** Announces a loading region to assistive technology while skeletons are shown. */
export function Loading({ label, children }: { label: string; children?: ReactNode }) {
  return (
    <div role="status" aria-live="polite" className="loading">
      <span className="sr-only">{label}</span>
      {children ?? <Skeleton />}
    </div>
  );
}

export function EmptyState({
  icon: IconComponent = Inbox,
  title,
  children,
  action,
}: {
  icon?: Icon;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="state">
      <span className="state__icon">
        <IconComponent aria-hidden size={22} />
      </span>
      <p className="state__title">{title}</p>
      {children && <div className="state__text">{children}</div>}
      {action && <div className="state__action">{action}</div>}
    </div>
  );
}

/**
 * A list that is empty only because of its filters or its search: it says so,
 * unlike a list with nothing in it, and offers the way back to everything.
 */
export function NoMatch({ title, search = false, onClear }: { title?: string; search?: boolean; onClear: () => void }) {
  const { t } = useI18n();
  return (
    <EmptyState
      icon={SearchX}
      title={title ?? (search ? t('empty.noSearchMatch') : t('empty.noMatch'))}
      action={
        <Button size="sm" onClick={onClear}>
          {search ? t('empty.clearSearch') : t('empty.clearFilters')}
        </Button>
      }
    >
      {t('empty.noMatchHint')}
    </EmptyState>
  );
}

/** An error whose text is a message of the catalog, so that it is shown in the chosen language. */
export class MessageError extends Error {
  readonly key: MessageKey;
  readonly params?: MessageParams;

  constructor(key: MessageKey, params?: MessageParams) {
    super(key);
    this.key = key;
    this.params = params;
  }
}

/** Whether the engine refused a change because someone else changed the same data meanwhile. */
function isConflict(error: { status: number; serverType?: string }): boolean {
  return error.status === 409 || /OptimisticLocking/i.test(error.serverType ?? '');
}

/**
 * What went wrong, in words for the user: a title, what to do next (`hint`)
 * and the technical detail of the server, which is shown on request.
 */
export function describeError(error: unknown, t: Translate): { title: string; detail?: string; hint?: string } {
  if (error instanceof MessageError) {
    return { title: t('error.unexpected'), detail: t(error.key, error.params) };
  }
  if (isApiError(error)) {
    const detail = error.serverMessage;
    switch (error.kind) {
      case 'network':
        return { title: t('error.network'), hint: t('error.hint.network') };
      case 'unauthorized':
        return { title: t('error.unauthorized') };
      case 'forbidden':
        return { title: t('error.forbidden'), detail, hint: t('error.hint.forbidden') };
      case 'notFound':
        return { title: t('error.notFound'), detail, hint: t('error.hint.notFound') };
      case 'server':
        return isConflict(error)
          ? { title: t('error.conflict'), detail, hint: t('error.hint.conflict') }
          : { title: t('error.server'), detail, hint: t('error.hint.server') };
      default:
        return isConflict(error)
          ? { title: t('error.conflict'), detail, hint: t('error.hint.conflict') }
          : { title: t('error.request'), detail, hint: t('error.hint.request') };
    }
  }
  return { title: t('error.unexpected'), detail: error instanceof Error ? error.message : undefined };
}

/** The technical detail of an error, folded away, with a way to copy it for a report. */
export function ErrorDetail({ detail }: { detail: string }) {
  const { t } = useI18n();
  const [copied, setCopied] = useState(false);
  return (
    <details className="error-detail">
      <summary>{t('error.details')}</summary>
      <p className="state__text state__text--detail">{detail}</p>
      <button
        type="button"
        className="link"
        onClick={() =>
          void navigator.clipboard?.writeText(detail).then(
            () => setCopied(true),
            () => undefined,
          )
        }
      >
        {copied ? t('error.copied') : t('error.copy')}
      </button>
    </details>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n();
  const { title, detail, hint } = describeError(error, t);
  const IconComponent = isApiError(error, 'network')
    ? WifiOff
    : isApiError(error, 'forbidden')
      ? Lock
      : AlertTriangle;
  return (
    <div className="state state--error" role="alert">
      <span className="state__icon">
        <IconComponent aria-hidden size={22} />
      </span>
      <p className="state__title">{title}</p>
      {hint && <p className="state__text">{hint}</p>}
      {detail && <ErrorDetail detail={detail} />}
      {onRetry && (
        <div className="state__action">
          <Button onClick={onRetry}>{t('action.retry')}</Button>
        </div>
      )}
    </div>
  );
}

export function Notice({
  tone = 'info',
  title,
  children,
}: {
  tone?: 'info' | 'warning' | 'danger';
  title?: string;
  children: ReactNode;
}) {
  return (
    <div className={cx('notice', `notice--${tone}`)} role={tone === 'danger' ? 'alert' : 'note'}>
      {title && <p className="notice__title">{title}</p>}
      <div className="notice__body">{children}</div>
    </div>
  );
}

// --- dialog ----------------------------------------------------------------

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  wide,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  wide?: boolean;
}) {
  const { t } = useI18n();
  // The dialogs are opened from state, not from a Radix trigger, so Radix has nothing to give the
  // focus back to on closing. Remember the control that had it when the dialog opened.
  // Layout effects run before Radix moves the focus into the dialog in its own effect.
  const opener = useRef<Element | null>(null);
  useLayoutEffect(() => {
    if (open) opener.current = document.activeElement;
  }, [open]);
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="dialog__overlay" />
        <RadixDialog.Content
          className={cx('dialog', wide && 'dialog--wide')}
          onCloseAutoFocus={(event) => {
            // a dialog opened from a menu item returns to the button of that menu, as the item is gone
            const fromMenu = opener.current instanceof HTMLElement && !!opener.current.closest('[role="menu"]');
            const target = fromMenu || !opener.current?.isConnected ? lastMenuTrigger() ?? opener.current : opener.current;
            if (target instanceof HTMLElement && target.isConnected && target !== document.body) {
              event.preventDefault();
              target.focus();
            }
          }}
        >
          <header className="dialog__header">
            <RadixDialog.Title className="dialog__title">{title}</RadixDialog.Title>
            <RadixDialog.Close asChild>
              <button type="button" className="icon-btn" aria-label={t('action.close')}>
                <X aria-hidden size={18} />
              </button>
            </RadixDialog.Close>
          </header>
          {description ? (
            <RadixDialog.Description className="dialog__description">
              {description}
            </RadixDialog.Description>
          ) : (
            <RadixDialog.Description className="sr-only">{title}</RadixDialog.Description>
          )}
          <div className="dialog__body">{children}</div>
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}

export function Kbd({ children }: { children: ReactNode }) {
  return <kbd className="kbd">{children}</kbd>;
}
