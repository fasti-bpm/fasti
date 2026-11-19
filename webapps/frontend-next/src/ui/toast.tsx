/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Toast as RadixToast } from 'radix-ui';
import { CircleAlert, CircleCheck, X } from 'lucide-react';
import { useI18n } from '../i18n';
import { cx, describeError } from './index';

export interface ToastOptions {
  tone?: 'success' | 'error';
  title: string;
  detail?: string;
  /** e.g. "Undo" */
  action?: { label: string; run: () => void };
}

interface ToastItem extends ToastOptions {
  id: number;
}

interface Toaster {
  show: (toast: ToastOptions) => void;
  /** Reports a failed action; server messages are shown as plain text. */
  error: (error: unknown, title?: string) => void;
}

const ToastContext = createContext<Toaster | null>(null);

let nextId = 1;

export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const show = useCallback((toast: ToastOptions) => {
    setToasts((current) => [...current.slice(-3), { ...toast, id: nextId++ }]);
  }, []);

  const error = useCallback(
    (cause: unknown, title?: string) => {
      const described = describeError(cause, t);
      show({
        tone: 'error',
        title: title ?? described.title,
        detail: title ? (described.detail ?? described.title) : described.detail,
      });
    },
    [show, t],
  );

  const remove = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const value = useMemo(() => ({ show, error }), [show, error]);

  return (
    <ToastContext.Provider value={value}>
      <RadixToast.Provider swipeDirection="up" label={t('toast.region')}>
        {children}
        {toasts.map((toast) => (
          <RadixToast.Root
            key={toast.id}
            className={cx('toast', `toast--${toast.tone ?? 'success'}`)}
            duration={toast.action ? 8000 : toast.tone === 'error' ? 9000 : 4000}
            onOpenChange={(open) => {
              if (!open) remove(toast.id);
            }}
          >
            <span className="toast__icon">
              {toast.tone === 'error' ? (
                <CircleAlert aria-hidden size={18} />
              ) : (
                <CircleCheck aria-hidden size={18} />
              )}
            </span>
            <div className="toast__text">
              <RadixToast.Title className="toast__title">{toast.title}</RadixToast.Title>
              {toast.detail && (
                <RadixToast.Description className="toast__detail">
                  {toast.detail}
                </RadixToast.Description>
              )}
            </div>
            {toast.action && (
              <RadixToast.Action asChild altText={toast.action.label}>
                <button type="button" className="btn btn--ghost btn--sm" onClick={toast.action.run}>
                  {toast.action.label}
                </button>
              </RadixToast.Action>
            )}
            <RadixToast.Close className="icon-btn icon-btn--sm" aria-label={t('action.close')}>
              <X aria-hidden size={16} />
            </RadixToast.Close>
          </RadixToast.Root>
        ))}
        <RadixToast.Viewport className="toast-viewport" />
      </RadixToast.Provider>
    </ToastContext.Provider>
  );
}

export function useToast(): Toaster {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside ToastProvider');
  }
  return context;
}
