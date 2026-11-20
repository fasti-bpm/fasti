/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { ChevronLeft } from 'lucide-react';
import { cx } from './index';

// The frame of a page: its header and the cards it is made of.

export function PageHeader({
  title,
  subtitle,
  back,
  badges,
  actions,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  back?: { to: string; label: string };
  badges?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header className="page-header">
      {back && (
        <Link to={back.to} className="icon-btn page-header__back" aria-label={back.label}>
          <ChevronLeft aria-hidden size={18} />
        </Link>
      )}
      <div className="page-header__titles">
        <div className="page-header__line">
          <h1 className="page-header__title">{title}</h1>
          {badges}
        </div>
        {subtitle && <div className="page-header__subtitle">{subtitle}</div>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}

export function Card({
  title,
  action,
  children,
  flush,
  className,
  label,
  id,
}: {
  id?: string;
  title?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  /** content reaches the border, e.g. a table */
  flush?: boolean;
  className?: string;
  /** names the card as a region, for a card that holds controls of its own */
  label?: string;
}) {
  return (
    <section id={id} className={cx('card', flush && 'card--flush', className)} aria-label={label}>
      {(title || action) && (
        <header className="card__header">
          <h2 className="card__title">{title}</h2>
          {action}
        </header>
      )}
      <div className="card__body">{children}</div>
    </section>
  );
}
