/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useState } from 'react';
import { DropdownMenu } from 'radix-ui';
import { Check, Languages, Monitor, Moon, Sun } from 'lucide-react';
import {
  LOCALE_MANIFESTS,
  browserLocale,
  loadLocale,
  manifestOf,
  storedLocale,
  translate,
  useI18n,
  type Locale,
  type MessageKey,
} from '../i18n';
import { useTheme, type ThemeChoice } from '../theme/theme';
import { IconButton } from '../ui';
import { MenuContent } from '../ui/menu';

export function LanguageSwitch() {
  const { locale, setLocale, t } = useI18n();
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <IconButton icon={Languages} label={t('prefs.language')} />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <MenuContent>
          <DropdownMenu.RadioGroup
            value={locale}
            onValueChange={setLocale}
          >
            {LOCALE_MANIFESTS.map(({ tag, nativeName, dir }) => (
              <DropdownMenu.RadioItem key={tag} value={tag} className="menu__item" lang={tag} dir={dir}>
                <span className="menu__check">
                  <DropdownMenu.ItemIndicator>
                    <Check aria-hidden size={16} />
                  </DropdownMenu.ItemIndicator>
                </span>
                {nativeName}
              </DropdownMenu.RadioItem>
            ))}
          </DropdownMenu.RadioGroup>
        </MenuContent>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

/**
 * Offers the language of the browser, in that language, as long as the user
 * has not chosen one; it is never applied without a click.
 */
export function LanguageSuggestion() {
  const { locale, setLocale } = useI18n();
  const [suggested, setSuggested] = useState<Locale | null>(null);
  useEffect(() => {
    const candidate = storedLocale() === null ? browserLocale() : null;
    if (candidate && candidate !== locale) {
      let active = true;
      loadLocale(candidate).then(
        () => active && setSuggested(candidate),
        () => undefined,
      );
      return () => {
        active = false;
      };
    }
    setSuggested(null);
    return undefined;
  }, [locale]);
  if (!suggested) {
    return null;
  }
  const { nativeName, dir } = manifestOf(suggested);
  return (
    <button type="button" className="btn btn--ghost btn--sm" lang={suggested} dir={dir} onClick={() => setLocale(suggested)}>
      {translate(suggested, 'prefs.suggestLanguage', { language: nativeName })}
    </button>
  );
}

const THEMES: { value: ThemeChoice; label: MessageKey; icon: typeof Sun }[] = [
  { value: 'system', label: 'prefs.theme.system', icon: Monitor },
  { value: 'light', label: 'prefs.theme.light', icon: Sun },
  { value: 'dark', label: 'prefs.theme.dark', icon: Moon },
];

export function ThemeSwitch() {
  const { choice, resolved, setChoice } = useTheme();
  const { t } = useI18n();
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <IconButton icon={resolved === 'dark' ? Moon : Sun} label={t('prefs.theme')} />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <MenuContent>
          <DropdownMenu.RadioGroup
            value={choice}
            onValueChange={(value) => setChoice(value as ThemeChoice)}
          >
            {THEMES.map(({ value, label, icon: ThemeIcon }) => (
              <DropdownMenu.RadioItem key={value} value={value} className="menu__item">
                <span className="menu__check">
                  <DropdownMenu.ItemIndicator>
                    <Check aria-hidden size={16} />
                  </DropdownMenu.ItemIndicator>
                </span>
                <ThemeIcon aria-hidden size={16} />
                {t(label)}
              </DropdownMenu.RadioItem>
            ))}
          </DropdownMenu.RadioGroup>
        </MenuContent>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
