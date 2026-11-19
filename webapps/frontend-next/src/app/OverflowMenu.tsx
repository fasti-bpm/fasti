/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useLayoutEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import { DropdownMenu } from 'radix-ui';
import { ChevronDown } from 'lucide-react';
import { cx } from '../ui';
import { MenuContent } from '../ui/menu';

interface Hidden {
  index: number;
  text: string;
  active: boolean;
}

/** room kept at the end of the row for the menu button */
const RESERVE = 64;

function isActive(item: HTMLElement): boolean {
  return (
    item.classList.contains('active') ||
    item.getAttribute('aria-current') === 'page' ||
    item.getAttribute('aria-selected') === 'true'
  );
}

/** Which items do not fit into the row, measured with all of them shown. */
export function measureOverflow(container: HTMLElement, items: HTMLElement[]): number[] {
  const box = container.getBoundingClientRect();
  if (box.width === 0) return [];
  const rtl = getComputedStyle(container).direction === 'rtl';
  const beyond = (item: HTMLElement, reserve: number) => {
    const rect = item.getBoundingClientRect();
    return rtl ? rect.left < box.left + reserve - 0.5 : rect.right > box.right - reserve + 0.5;
  };
  if (!items.some((item) => beyond(item, 0))) return [];
  return items.flatMap((item, index) => (beyond(item, RESERVE) ? [index] : []));
}

/**
 * A "More" menu for a row of links or tabs that is too narrow, e.g. on a
 * phone: the items that do not fit are hidden from the row and offered in the
 * menu, and the menu names the current item when it is one of them. Choosing
 * an entry clicks the hidden item, so links and tabs behave as before.
 */
export function OverflowMenu({
  target,
  selector,
  label,
  watch,
}: {
  target: RefObject<HTMLElement | null>;
  selector: string;
  label: string;
  /** measures again when this changes, e.g. the current page */
  watch?: unknown;
}) {
  const [hidden, setHidden] = useState<Hidden[]>([]);

  useLayoutEffect(() => {
    const container = target.current;
    if (!container) return undefined;
    let frame = 0;
    const measure = () => {
      const items = Array.from(container.querySelectorAll<HTMLElement>(selector));
      for (const item of items) item.removeAttribute('data-overflow');
      const indexes = measureOverflow(container, items);
      for (const index of indexes) items[index]?.setAttribute('data-overflow', '');
      const next = indexes.map((index) => ({
        index,
        text: items[index]?.textContent?.trim() ?? '',
        active: !!items[index] && isActive(items[index]),
      }));
      setHidden((previous) => (JSON.stringify(previous) === JSON.stringify(next) ? previous : next));
    };
    measure();
    const later = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(measure);
    };
    const observer = new ResizeObserver(later);
    observer.observe(container);
    // other words (a language), other items or another current one are measured again
    const changes = new MutationObserver(later);
    changes.observe(container, {
      subtree: true,
      childList: true,
      characterData: true,
      attributeFilter: ['class', 'aria-selected', 'aria-current'],
    });
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      changes.disconnect();
    };
  }, [target, selector, watch]);

  if (hidden.length === 0) return null;
  const current = hidden.find((entry) => entry.active);
  const click = (index: number) => {
    const item = target.current?.querySelectorAll<HTMLElement>(selector)[index];
    if (!item) return;
    // tabs are chosen on mouse down, links on click
    item.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, button: 0 }));
    item.click();
  };
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className={cx('overflow-menu', current && 'is-active')} aria-label={label}>
        <span className="overflow-menu__text">{current ? current.text : label}</span>
        <ChevronDown aria-hidden size={14} />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <MenuContent>
          {hidden.map((entry) => (
            <DropdownMenu.Item
              key={entry.index}
              className={cx('menu__item', entry.active && 'is-active')}
              onSelect={() => click(entry.index)}
            >
              {entry.text}
            </DropdownMenu.Item>
          ))}
        </MenuContent>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

/**
 * A row whose items are moved into a "More" menu when they do not fit. The
 * row clips its content, so what overflows is measured instead of scrolled.
 */
export function OverflowRow({
  className,
  selector,
  label,
  watch,
  children,
}: {
  className?: string;
  selector: string;
  label: string;
  watch?: unknown;
  children: ReactNode;
}) {
  const items = useRef<HTMLDivElement>(null);
  return (
    <div className={cx('overflow-row', className)}>
      <div ref={items} className="overflow-row__items">
        {children}
      </div>
      <OverflowMenu target={items} selector={selector} label={label} watch={watch} />
    </div>
  );
}
