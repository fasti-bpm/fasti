/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useId, useState } from 'react';
import type { ReactNode } from 'react';
import { ChevronDown, SlidersHorizontal } from 'lucide-react';
import { useI18n } from '../i18n';
import { cx } from '../ui';

/**
 * Filters that are rarely used. On a wide screen they stay in the row; on a
 * phone they fold behind one button, so the results are on the first screen.
 * The button says how many of the folded filters are set.
 */
export function MoreFilters({ active, children }: { active: number; children: ReactNode }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <div className={cx('more-filters', open && 'is-open')}>
      <button
        type="button"
        className={cx('toggle more-filters__toggle', active > 0 && 'is-on')}
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen(!open)}
      >
        <SlidersHorizontal aria-hidden size={14} />
        {active > 0 ? t('filters.moreActive', { count: active }) : t('filters.more')}
        <ChevronDown aria-hidden size={14} className="more-filters__chevron" />
      </button>
      <div id={id} className="more-filters__body">
        {children}
      </div>
    </div>
  );
}
