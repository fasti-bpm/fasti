/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { forwardRef, useRef } from 'react';
import type { ComponentPropsWithoutRef } from 'react';
import { DropdownMenu } from 'radix-ui';

/*
 * A menu closed with the pointer gives the focus back to its button, which
 * would show the button's focus ring and open its tooltip although the user
 * never used the keyboard. Such a return is "quiet": the ring is hidden and
 * tooltips ignore focus for a moment. A menu closed with the keyboard keeps
 * the usual, visible return.
 */
let quietUntil = 0;

/** whether a tooltip opened by focus now would only echo a pointer action */
export function isQuietFocus(): boolean {
  return Date.now() < quietUntil;
}

/** the button of the menu used last; a dialog opened from a menu item gives the focus back to it */
let lastTrigger: HTMLElement | null = null;

export function lastMenuTrigger(): HTMLElement | null {
  return lastTrigger?.isConnected ? lastTrigger : null;
}

function triggerOf(content: HTMLElement): HTMLElement | null {
  const id = content.id;
  return id ? document.querySelector<HTMLElement>(`[aria-controls="${CSS.escape(id)}"]`) : null;
}

type ContentProps = ComponentPropsWithoutRef<typeof DropdownMenu.Content>;

export const MenuContent = forwardRef<HTMLDivElement, ContentProps>(function MenuContent(
  { className, align = 'end', sideOffset = 6, onCloseAutoFocus, onPointerDown, onKeyDown, ...rest },
  ref,
) {
  // the button that opened the menu, known while the menu is open
  const trigger = useRef<HTMLElement | null>(null);
  return (
    <DropdownMenu.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      className={className ? `menu ${className}` : 'menu'}
      {...rest}
      onPointerDown={(event) => {
        trigger.current = triggerOf(event.currentTarget);
        lastTrigger = trigger.current;
        onPointerDown?.(event);
      }}
      onKeyDown={(event) => {
        trigger.current = null;
        lastTrigger = triggerOf(event.currentTarget);
        onKeyDown?.(event);
      }}
      onCloseAutoFocus={(event) => {
        onCloseAutoFocus?.(event);
        const button = trigger.current;
        trigger.current = null;
        if (event.defaultPrevented || !button?.isConnected) return;
        event.preventDefault();
        quietUntil = Date.now() + 400;
        // focusVisible: false keeps the ring hidden where the browser supports it
        button.setAttribute('data-quiet-focus', '');
        button.addEventListener('blur', () => button.removeAttribute('data-quiet-focus'), { once: true });
        button.addEventListener('keydown', () => button.removeAttribute('data-quiet-focus'), { once: true });
        button.focus({ preventScroll: true, focusVisible: false } as FocusOptions);
      }}
    />
  );
});
