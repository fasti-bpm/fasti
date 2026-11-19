/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { Suspense, lazy, useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useNavigationType } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { DropdownMenu } from 'radix-ui';
import {
  Check,
  ChevronDown,
  House,
  Info,
  Keyboard,
  LogOut,
  Search,
  Server,
  Settings2,
} from 'lucide-react';
import { getEngines } from '../api/engine';
import { env, enginePageUrl } from '../env';
import { useI18n } from '../i18n';
import { pluginsFor } from '../plugins/registry';
import { Dialog, Kbd, Loading, Tooltip } from '../ui';
import { Wordmark } from './Logo';
import { COCKPIT_GROUPS } from './navigation';
import { LanguageSwitch, ThemeSwitch } from './preferences';
import { TenantSwitch } from './TenantSwitch';
import { startPage, startPath } from '../lib/userPrefs';
import { useLogout, useSession, useUnauthorizedHandler } from './session';
import { GO_TO, SEQUENCE_TIMEOUT, SHORTCUTS, SHORTCUT_GROUPS, isTyping } from './shortcuts';
import { OverflowRow } from './OverflowMenu';
import { MenuContent } from '../ui/menu';

const loadPalette = () => import('./CommandPalette');
const CommandPalette = lazy(async () => ({ default: (await loadPalette()).CommandPalette }));

/** The start page is applied on the first page of a visit only. */
let startApplied = false;

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return (first + last).toUpperCase() || '?';
}

function EngineMenu() {
  const { t } = useI18n();
  const engines = useQuery({ queryKey: ['engines'], queryFn: getEngines, staleTime: Infinity });
  if (!engines.data || engines.data.length < 2) {
    return null;
  }
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className="chip-btn" aria-label={t('shell.engine')}>
        <Server aria-hidden size={14} />
        {env.engine}
        <ChevronDown aria-hidden size={14} />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <MenuContent>
          <DropdownMenu.Label className="menu__label">{t('shell.engine')}</DropdownMenu.Label>
          {engines.data.map((engine) => (
            <DropdownMenu.Item key={engine.name} asChild className="menu__item">
              <a href={enginePageUrl(engine.name)}>
                <span className="menu__check">
                  {engine.name === env.engine && <Check aria-hidden size={16} />}
                </span>
                {engine.name}
              </a>
            </DropdownMenu.Item>
          ))}
        </MenuContent>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

/** Every monitoring page, grouped by goal, reachable from any page in two steps. */
function CockpitMenu() {
  const { t } = useI18n();
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className="nav-menu-btn" aria-label={t('nav.cockpitPages')}>
        <ChevronDown aria-hidden size={14} />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <MenuContent className="nav-menu" align="start">
          {COCKPIT_GROUPS.map((group, index) => (
            <DropdownMenu.Group key={group.label}>
              {index > 0 && <DropdownMenu.Separator className="menu__separator" />}
              <DropdownMenu.Label className="menu__label">{t(group.label)}</DropdownMenu.Label>
              {group.sections.map((section) => (
                <DropdownMenu.Item key={section.to} asChild className="menu__item">
                  <Link to={section.to}>{t(section.label)}</Link>
                </DropdownMenu.Item>
              ))}
            </DropdownMenu.Group>
          ))}
        </MenuContent>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

export function Shell() {
  const { t, locale } = useI18n();
  const session = useSession();
  const logout = useLogout();
  const [aboutOpen, setAboutOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  // the palette is loaded once it is first opened, or when the browser is idle
  const [paletteUsed, setPaletteUsed] = useState(false);
  useEffect(() => {
    if (paletteOpen) setPaletteUsed(true);
  }, [paletteOpen]);
  useEffect(() => {
    const idle = window.requestIdleCallback ?? ((callback: () => void) => window.setTimeout(callback, 1500));
    const cancel = window.cancelIdleCallback ?? window.clearTimeout;
    const handle = idle(() => void loadPalette());
    return () => cancel(handle);
  }, []);
  useUnauthorizedHandler();

  const navigate = useNavigate();
  const authorizedApps = session.authorizedApps;
  const { pathname } = useLocation();
  useEffect(() => {
    // the chosen start page replaces the landing page once per visit
    if (startApplied) return;
    startApplied = true;
    const target = startPath(startPage(), authorizedApps);
    if (pathname === '/' && target && target !== '/') void navigate(target, { replace: true });
  }, [pathname, authorizedApps, navigate]);
  // Back or Forward closes the palette; a command closes it itself, and a lazy page
  // that arrives later must not close a palette opened again meanwhile
  const navigationType = useNavigationType();
  const [palettePath, setPalettePath] = useState(pathname);
  if (palettePath !== pathname) {
    setPalettePath(pathname);
    if (navigationType === 'POP') setPaletteOpen(false);
  }
  useEffect(() => {
    // `G` and then a key goes to a section; the sequence is awaited for a moment
    let goPending = 0;
    const listener = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteOpen(true);
        return;
      }
      if (event.ctrlKey || event.metaKey || event.altKey || isTyping(event)) {
        return;
      }
      if (document.querySelector('[role="dialog"], [role="menu"]')) {
        return;
      }
      const key = event.key.toLowerCase();
      if (goPending && Date.now() - goPending < SEQUENCE_TIMEOUT) {
        goPending = 0;
        const target = GO_TO[key];
        if (target && (!target.app || authorizedApps.includes(target.app))) {
          // the key completes the sequence; pages must not act on it as well
          event.preventDefault();
          event.stopImmediatePropagation();
          void navigate(target.path);
        } else if (/^[a-z]$/.test(key)) {
          // an unknown sequence does nothing, rather than the page acting on its second key
          event.preventDefault();
          event.stopImmediatePropagation();
        }
        return;
      }
      goPending = 0;
      if (event.key === '?') {
        setShortcutsOpen(true);
      } else if (key === 'g' && !event.shiftKey) {
        goPending = Date.now();
      }
    };
    // capturing: the second key of a sequence is handled before the pages see it
    window.addEventListener('keydown', listener, true);
    return () => window.removeEventListener('keydown', listener, true);
  }, [navigate, authorizedApps]);

  const canUse = (app: string) => session.authorizedApps.includes(app);
  const navigationPlugins = pluginsFor('shell.navigation');

  return (
    <div className="shell">
      <a
        className="skip-link"
        href="#main"
        onClick={(event) => {
          // the hash holds the route, so following the link would leave the page
          event.preventDefault();
          document.getElementById('main')?.focus();
        }}
      >
        {t('shell.skip')}
      </a>
      <header className="shell__header">
        <NavLink to="/" className="shell__brand" aria-label="Fasti BPM">
          <Wordmark />
        </NavLink>
        <OverflowRow className="shell__navrow" selector=".nav-link" label={t('nav.more')} watch={pathname}>
        <nav className="shell__nav" aria-label={t('shell.navigation')}>
          <NavLink to="/" end className="nav-link">
            {t('nav.home')}
          </NavLink>
          {(canUse('tasklist') || navigationPlugins.length > 0) && (
            <div className="nav-group" role="group" aria-label={t('nav.group.work')}>
              {canUse('tasklist') && (
                <>
                  <NavLink to="/tasklist" className="nav-link">
                    {t('nav.tasklist')}
                  </NavLink>
                  <NavLink to="/processes" className="nav-link">
                    {t('nav.processes')}
                  </NavLink>
                </>
              )}
              {navigationPlugins.map((plugin) => (
                <NavLink key={plugin.id} to={`/plugin/${plugin.id}`} className="nav-link">
                  {plugin.label(locale)}
                </NavLink>
              ))}
            </div>
          )}
          {canUse('cockpit') && (
            <div className="nav-group" role="group" aria-label={t('nav.group.operate')}>
              <NavLink to="/cockpit" className="nav-link">
                {t('nav.cockpit')}
              </NavLink>
              <CockpitMenu />
            </div>
          )}
          {canUse('admin') && (
            <div className="nav-group" role="group" aria-label={t('nav.group.administer')}>
              <NavLink to="/admin" className="nav-link">
                {t('nav.admin')}
              </NavLink>
            </div>
          )}
        </nav>
        </OverflowRow>
        <div className="shell__tools">
          <Tooltip label={t('palette.open')} shortcut="Ctrl K">
            <button
              type="button"
              className="search-btn"
              onClick={() => setPaletteOpen(true)}
              aria-label={t('palette.open')}
            >
              <Search aria-hidden size={15} />
              <span className="search-btn__text">{t('palette.placeholder')}</span>
              <Kbd>Ctrl K</Kbd>
            </button>
          </Tooltip>
          <TenantSwitch />
          <EngineMenu />
          <LanguageSwitch />
          <ThemeSwitch />
          <DropdownMenu.Root>
            <DropdownMenu.Trigger className="user-btn" aria-label={t('shell.userMenu')}>
              <span className="avatar" aria-hidden>
                {initials(session.displayName)}
              </span>
              <span className="user-btn__name">{session.displayName}</span>
              <ChevronDown aria-hidden size={14} />
            </DropdownMenu.Trigger>
            <DropdownMenu.Portal>
              <MenuContent>
                <DropdownMenu.Label className="menu__label">
                  {session.displayName}
                  <span className="menu__sublabel">{session.userId}</span>
                </DropdownMenu.Label>
                <DropdownMenu.Separator className="menu__separator" />
                <DropdownMenu.Item asChild className="menu__item">
                  <Link to="/preferences">
                    <Settings2 aria-hidden size={16} />
                    {t('userPrefs.title')}
                  </Link>
                </DropdownMenu.Item>
                <DropdownMenu.Item className="menu__item" onSelect={() => setShortcutsOpen(true)}>
                  <Keyboard aria-hidden size={16} />
                  {t('shortcuts.title')}
                </DropdownMenu.Item>
                <DropdownMenu.Item asChild className="menu__item">
                  <Link to="/">
                    <House aria-hidden size={16} />
                    {t('shell.home')}
                  </Link>
                </DropdownMenu.Item>
                <DropdownMenu.Item className="menu__item" onSelect={() => setAboutOpen(true)}>
                  <Info aria-hidden size={16} />
                  {t('about.title')}
                </DropdownMenu.Item>
                <DropdownMenu.Separator className="menu__separator" />
                <DropdownMenu.Item className="menu__item" onSelect={() => logout.mutate()}>
                  <LogOut aria-hidden size={16} />
                  {t('shell.logout')}
                </DropdownMenu.Item>
              </MenuContent>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
        </div>
      </header>
      <main id="main" className="shell__main" tabIndex={-1}>
        <Suspense fallback={<Loading label={t('state.loading')} />}>
          <Outlet />
        </Suspense>
      </main>

      {(paletteOpen || paletteUsed) && (
        <Suspense fallback={null}>
          <CommandPalette
            open={paletteOpen}
            onOpenChange={setPaletteOpen}
            onShowShortcuts={() => setShortcutsOpen(true)}
          />
        </Suspense>
      )}

      <Dialog open={shortcutsOpen} onOpenChange={setShortcutsOpen} title={t('shortcuts.title')}>
        {SHORTCUT_GROUPS.map(({ group, label }) => {
          const shortcuts = SHORTCUTS.filter(
            (shortcut) => shortcut.group === group && (!shortcut.app || canUse(shortcut.app)),
          );
          if (shortcuts.length === 0) {
            return null;
          }
          return (
            <section key={group} className="shortcuts__group" aria-label={t(label)}>
              <h3 className="shortcuts__title">{t(label)}</h3>
              <dl className="shortcuts">
                {shortcuts.map((shortcut) => (
                  <div key={shortcut.label} className="shortcuts__row">
                    <dt>{t(shortcut.label)}</dt>
                    <dd>
                      {shortcut.keys.map((key) => (
                        <Kbd key={key}>{key}</Kbd>
                      ))}
                    </dd>
                  </div>
                ))}
              </dl>
            </section>
          );
        })}
      </Dialog>

      <Dialog open={aboutOpen} onOpenChange={setAboutOpen} title={t('about.title')}>
        <div className="about">
          <Wordmark size={40} />
          <p>{t('about.description')}</p>
          <p className="about__meta">{t('about.version', { version: __APP_VERSION__ })}</p>
          <p className="about__meta">{t('about.builtOn')}</p>
          <p className="about__meta">{t('about.diagrams')}</p>
        </div>
      </Dialog>
    </div>
  );
}
