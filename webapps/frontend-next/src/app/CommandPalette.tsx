/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import type { ComponentType, KeyboardEvent } from 'react';
import { useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Dialog as RadixDialog } from 'radix-ui';
import {
  Activity,
  AlertTriangle,
  Briefcase,
  CheckSquare,
  Cog,
  Building2,
  ExternalLink,
  Filter,
  FolderClock,
  FolderKanban,
  Hash,
  History,
  House,
  Info,
  Keyboard,
  Languages,
  Layers,
  ListChecks,
  LogOut,
  Moon,
  PackageOpen,
  Play,
  Search,
  ShieldCheck,
  Sun,
  Table2,
  UserRound,
  UsersRound,
  Workflow,
  ClipboardList,
  Inbox,
  Eraser,
  FileClock,
  PenTool,
  Users,
  X,
} from 'lucide-react';
import type { LucideProps } from 'lucide-react';
import { COCKPIT_GROUPS } from './navigation';
import { findById, searchEngine, type SearchHit } from '../api/cockpit';
import { caseInstancePath, historyPath } from '../cockpit/paths';
import { recentItems, rememberRecent } from '../lib/recent';
import { getTaskFilters } from '../api/engine';
import { classicAppUrl, classicWebapps } from '../env';
import { LOCALE_MANIFESTS, translate, useI18n, type MessageKey } from '../i18n';
import { useTheme } from '../theme/theme';
import { IconButton, cx } from '../ui';
import { useLogout, useSession } from './session';

interface Command {
  id: string;
  label: string;
  /** more words it is found by, e.g. its English name in another language */
  keywords?: string;
  group: string;
  icon: ComponentType<LucideProps>;
  run: () => void;
}

/**
 * Words a command is also found by, whatever the language of the interface:
 * people often know a section by its English name or by a word from another
 * product. Kept in English on purpose; the English names of the sections are added on top.
 */
const ALIASES: Record<string, string> = {
  home: 'start welcome dashboard',
  tasklist: 'inbox tasks todo work queue approvals',
  getNext: 'next work pull claim take assign me queue nhan viec tiep',
  processes: 'start process new request',
  '/cockpit': 'dashboard overview monitoring operate',
  '/cockpit/processes': 'definitions instances workflows',
  '/cockpit/decisions': 'dmn rules tables',
  '/cockpit/cases': 'cmmn case management plan items stages milestones',
  '/cockpit/case-history': 'cmmn cases closed finished archive',
  '/cockpit/incidents': 'errors failures problems alerts',
  '/cockpit/jobs': 'failed jobs retries timers',
  '/cockpit/external-tasks': 'workers topics',
  '/cockpit/workload': 'team load capacity board',
  '/cockpit/history': 'finished completed archive',
  '/cockpit/audit': 'log operations who changed',
  '/cockpit/modeler': 'designer editor bpmn diagram',
  '/cockpit/deployments': 'deploy upload versions resources',
  '/cockpit/batches': 'bulk async operations',
  '/cockpit/cleanup': 'history cleanup retention ttl',
  '/admin/users': 'people accounts members',
  '/admin/groups': 'teams roles',
  '/admin/tenants': 'organisations organizations',
  '/admin/authorizations': 'permissions rights access roles',
  '/admin/system': 'health status version about diagnostics',
  theme: 'dark light mode appearance',
  shortcuts: 'keyboard keys hotkeys help',
  logout: 'sign out log out exit',
};

/** The words of a command in English and its aliases, so a search in either finds it. */
function keywordsOf(id: string, ...keys: MessageKey[]): string {
  return [...keys.map((key) => translate('en', key)), ALIASES[id] ?? ''].join(' ');
}

const instanceLink = (id: string, tab?: string) =>
  `/cockpit/processes/instance/${encodeURIComponent(id)}${tab ? `?tab=${tab}` : ''}`;

/** Where a search result is shown. */
export function hitPath(hit: SearchHit): string {
  switch (hit.kind) {
    case 'instance':
      return instanceLink(hit.id);
    case 'finished':
      return historyPath(hit.id);
    case 'case':
      return caseInstancePath(hit.id);
    case 'task':
      return `/tasklist?task=${encodeURIComponent(hit.id)}`;
    case 'incident':
      return hit.instanceId ? instanceLink(hit.instanceId, 'incidents') : '/cockpit/incidents';
    case 'job':
      return hit.instanceId ? instanceLink(hit.instanceId, 'jobs') : '/cockpit/jobs';
    case 'deployment':
      return `/cockpit/deployments/${encodeURIComponent(hit.id)}`;
    case 'batch':
      return '/cockpit/batches';
  }
}

const HIT_ICONS: Record<SearchHit['kind'], ComponentType<LucideProps>> = {
  instance: Hash,
  finished: History,
  case: FolderKanban,
  task: CheckSquare,
  incident: AlertTriangle,
  job: Cog,
  deployment: PackageOpen,
  batch: Layers,
};

const HIT_LABELS: Record<SearchHit['kind'], MessageKey> = {
  instance: 'palette.kind.instance',
  finished: 'palette.kind.finished',
  case: 'palette.kind.case',
  task: 'palette.kind.task',
  incident: 'palette.kind.incident',
  job: 'palette.kind.job',
  deployment: 'palette.kind.deployment',
  batch: 'palette.kind.batch',
};

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/gi, 'd')
    .toLowerCase();
}

export function CommandPalette({
  open,
  onOpenChange,
  onShowShortcuts,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onShowShortcuts: () => void;
}) {
  const { t, locale, setLocale } = useI18n();
  const { resolved, setChoice } = useTheme();
  const session = useSession();
  const logout = useLogout();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const [term, setTerm] = useState('');
  useEffect(() => {
    const timer = window.setTimeout(() => setTerm(query.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [query]);
  const tasklist = session.authorizedApps.includes('tasklist');
  const cockpit = session.authorizedApps.includes('cockpit');
  const filters = useQuery({
    queryKey: ['filters'],
    queryFn: ({ signal }) => getTaskFilters(signal),
    enabled: open && tasklist,
    staleTime: 60_000,
  });
  const hits = useQuery({
    queryKey: ['palette-search', term, cockpit, tasklist],
    queryFn: async ({ signal }) => {
      const options = { instances: cockpit, tasks: tasklist, signal };
      const [exact, similar] = await Promise.all([findById(term, options), searchEngine(term, options)]);
      return { exact, similar };
    },
    enabled: open && term.length >= 2 && (cockpit || tasklist),
    staleTime: 10_000,
  });
  const searching = open && query.trim().length >= 2 && (cockpit || tasklist) && (term !== query.trim() || hits.isFetching);

  const commands = useMemo<Command[]>(() => {
    const list: Command[] = [];
    const go = t('palette.group.go');
    const actions = t('palette.group.actions');
    list.push({ id: 'home', label: t('nav.home'), keywords: keywordsOf('home', 'nav.home'), group: go, icon: House, run: () => navigate('/') });
    if (tasklist) {
      list.push(
        {
          id: 'tasklist',
          label: t('nav.tasklist'),
          keywords: keywordsOf('tasklist', 'nav.tasklist'),
          group: go,
          icon: ListChecks,
          run: () => navigate('/tasklist'),
        },
        {
          id: 'getNext',
          label: t('getNext.button'),
          keywords: keywordsOf('getNext', 'getNext.button', 'shortcuts.getNext'),
          group: actions,
          icon: Inbox,
          run: () => navigate('/tasklist?next=1'),
        },
        {
          id: 'processes',
          label: t('nav.processes'),
          keywords: keywordsOf('processes', 'nav.processes'),
          group: go,
          icon: Play,
          run: () => navigate('/processes'),
        },
      );
      for (const filter of filters.data ?? []) {
        list.push({
          id: `filter-${filter.id}`,
          label: filter.name,
          group: t('palette.group.filters'),
          icon: Filter,
          run: () => navigate(`/tasklist?filter=${encodeURIComponent(filter.id)}`),
        });
      }
    }
    if (cockpit) {
      const monitoring = t('nav.cockpit');
      // the same sections as the menus, so the palette never misses one
      const icons: Record<string, ComponentType<LucideProps>> = {
        '/cockpit': Activity,
        '/cockpit/processes': Workflow,
        '/cockpit/decisions': Table2,
        '/cockpit/cases': FolderKanban,
        '/cockpit/case-history': FolderClock,
        '/cockpit/incidents': AlertTriangle,
        '/cockpit/jobs': Cog,
        '/cockpit/external-tasks': Briefcase,
        '/cockpit/workload': Users,
        '/cockpit/history': History,
        '/cockpit/decision-history': FileClock,
        '/cockpit/audit': ClipboardList,
        '/cockpit/modeler': PenTool,
        '/cockpit/deployments': PackageOpen,
        '/cockpit/batches': Layers,
        '/cockpit/cleanup': Eraser,
      };
      const pages: [string, MessageKey, ComponentType<LucideProps>][] = COCKPIT_GROUPS.flatMap((group) =>
        group.sections.map((section): [string, MessageKey, ComponentType<LucideProps>] => [section.to, section.label, icons[section.to] ?? Activity]),
      );
      for (const [path, label, icon] of pages) {
        list.push({
          id: `cockpit-${path}`,
          label: `${monitoring}: ${t(label)}`,
          keywords: keywordsOf(path, 'nav.cockpit', label),
          group: go,
          icon,
          run: () => navigate(path),
        });
      }
    }
    if (session.authorizedApps.includes('admin')) {
      const administration = t('nav.admin');
      const pages: [string, MessageKey, ComponentType<LucideProps>][] = [
        ['/admin/users', 'admin.nav.users', UserRound],
        ['/admin/groups', 'admin.nav.groups', UsersRound],
        ['/admin/tenants', 'admin.nav.tenants', Building2],
        ['/admin/authorizations', 'admin.nav.authorizations', ShieldCheck],
        ['/admin/system', 'admin.nav.system', Info],
      ];
      for (const [path, label, icon] of pages) {
        list.push({
          id: `admin-${path}`,
          label: `${administration}: ${t(label)}`,
          keywords: keywordsOf(path, 'nav.admin', label),
          group: go,
          icon,
          run: () => navigate(path),
        });
      }
    }
    for (const app of ['cockpit', 'admin'] as const) {
      if (classicWebapps.enabled && session.authorizedApps.includes(app)) {
        list.push({
          id: app,
          label: `${t(app === 'cockpit' ? 'nav.cockpit' : 'nav.admin')} (${t('nav.classic')})`,
          group: go,
          icon: ExternalLink,
          run: () => window.location.assign(classicAppUrl(app)),
        });
      }
    }
    list.push(
      {
        id: 'theme',
        label: resolved === 'dark' ? t('palette.lightTheme') : t('palette.darkTheme'),
        keywords: keywordsOf('theme', resolved === 'dark' ? 'palette.lightTheme' : 'palette.darkTheme'),
        group: actions,
        icon: resolved === 'dark' ? Sun : Moon,
        run: () => setChoice(resolved === 'dark' ? 'light' : 'dark'),
      },
      ...LOCALE_MANIFESTS.filter((manifest) => manifest.tag !== locale).map((manifest) => ({
        id: `language-${manifest.tag}`,
        label: t('palette.language', { language: manifest.nativeName }),
        // found by the English name of the language as well, e.g. "German" for Deutsch
        keywords: `${translate('en', 'palette.language', { language: manifest.nativeName })} ${manifest.englishName} ${manifest.tag}`,
        group: actions,
        icon: Languages,
        run: () => setLocale(manifest.tag),
      })),
      {
        id: 'shortcuts',
        label: t('shortcuts.title'),
        keywords: keywordsOf('shortcuts', 'shortcuts.title'),
        group: actions,
        icon: Keyboard,
        run: onShowShortcuts,
      },
      {
        id: 'logout',
        label: t('shell.logout'),
        keywords: keywordsOf('logout', 'shell.logout'),
        group: actions,
        icon: LogOut,
        run: () => logout.mutate(),
      },
    );
    return list;
  }, [t, tasklist, cockpit, filters.data, session.authorizedApps, resolved, locale, navigate, setChoice, setLocale, onShowShortcuts, logout]);

  const matches = useMemo(() => {
    const text = query.trim();
    const needle = normalize(text);
    if (!needle) {
      const recent = t('palette.group.recent');
      return [
        ...recentItems().map((item): Command => ({
          id: `recent-${item.path}`,
          label: `${item.title} · ${t(HIT_LABELS[item.kind as SearchHit['kind']] ?? 'palette.kind.instance')}`,
          group: recent,
          icon: HIT_ICONS[item.kind as SearchHit['kind']] ?? Hash,
          run: () => navigate(item.path),
        })),
        ...commands,
      ];
    }
    // every word typed must occur, in the label or in the keywords
    const words = needle.split(/\s+/).filter(Boolean);
    const found = commands.filter((command) => {
      const haystack = normalize(`${command.label} ${command.keywords ?? ''}`);
      return words.every((word) => haystack.includes(word));
    });
    const toCommand = (hit: SearchHit, group: string): Command => {
      const path = hitPath(hit);
      return {
        id: `hit-${hit.kind}-${hit.id}`,
        label: [hit.title, hit.detail].filter(Boolean).join(' · '),
        group,
        icon: HIT_ICONS[hit.kind],
        run: () => {
          rememberRecent({ kind: hit.kind, id: hit.id, title: hit.title, path });
          navigate(path);
        },
      };
    };
    // what the engine found for the text, if it has answered for what is typed now
    const answer = hits.data && term === text ? hits.data : null;
    if (answer) {
      const exactIds = new Set(answer.exact.map((hit) => `${hit.kind}-${hit.id}`));
      for (const hit of answer.exact) {
        found.push(toCommand(hit, t(HIT_LABELS[hit.kind])));
      }
      for (const hit of answer.similar) {
        if (exactIds.has(`${hit.kind}-${hit.id}`)) continue;
        found.push(toCommand(hit, hit.kind === 'instance' ? t('palette.group.instances') : t('palette.group.tasks')));
      }
    }
    // what was typed may be the id of an instance or the key of a process; an
    // instance that was found is not offered a second time
    if (cockpit && !/\s/.test(text)) {
      const open = t('palette.group.open');
      if (!answer?.exact.some((hit) => hit.kind === 'instance' || hit.kind === 'finished' || hit.kind === 'case')) {
        found.push({
          id: 'open-instance',
          label: t('palette.openInstance', { id: text }),
          group: open,
          icon: Hash,
          run: () => navigate(`/cockpit/processes/instance/${encodeURIComponent(text)}`),
        });
      }
      found.push(
        {
          id: 'open-definition',
          label: t('palette.openDefinition', { key: text }),
          group: open,
          icon: Workflow,
          run: () => navigate(`/cockpit/processes/key/${encodeURIComponent(text)}`),
        },
      );
    }
    return found;
  }, [commands, query, cockpit, t, navigate, hits.data, term]);

  // where focus was before the palette opened; it goes back there unless a command went elsewhere
  const opener = useRef<HTMLElement | null>(null);
  const ran = useRef(false);
  useEffect(() => {
    if (open) {
      opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      ran.current = false;
      setQuery('');
      setActive(0);
    }
  }, [open]);

  const run = (command: Command | undefined) => {
    if (command) {
      ran.current = true;
      onOpenChange(false);
      command.run();
    }
  };

  const activeId = matches[active]?.id;
  useEffect(() => {
    if (activeId) document.getElementById(`palette-${activeId}`)?.scrollIntoView({ block: 'nearest' });
  }, [activeId]);

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((index) => Math.max(0, Math.min(index + 1, matches.length - 1)));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      run(matches[active]);
    }
  };

  let lastGroup = '';
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="dialog__overlay" />
        <RadixDialog.Content
          className="palette"
          aria-describedby={undefined}
          onCloseAutoFocus={(event) => {
            event.preventDefault();
            if (!ran.current && opener.current?.isConnected) opener.current.focus();
          }}
        >
          <RadixDialog.Title className="sr-only">{t('palette.open')}</RadixDialog.Title>
          <div className="palette__input">
            <Search aria-hidden size={18} />
            <input
              role="combobox"
              aria-expanded
              aria-controls="palette-list"
              aria-activedescendant={matches[active] ? `palette-${matches[active].id}` : undefined}
              aria-label={t('palette.open')}
              placeholder={t('palette.placeholder')}
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setActive(0);
              }}
              onKeyDown={onKeyDown}
            />
            <RadixDialog.Close asChild>
              <IconButton icon={X} label={t('action.close')} className="palette__close" />
            </RadixDialog.Close>
          </div>
          <ul id="palette-list" role="listbox" className="palette__list">
            {matches.length === 0 && !searching && <li className="palette__empty">{t('palette.empty')}</li>}
            {matches.map((command, index) => {
              const heading = command.group !== lastGroup ? command.group : null;
              lastGroup = command.group;
              const CommandIcon = command.icon;
              return (
                <li key={command.id} role="presentation">
                  {heading && <div className="palette__group">{heading}</div>}
                  <div
                    id={`palette-${command.id}`}
                    role="option"
                    aria-selected={index === active}
                    className={cx('palette__item', index === active && 'is-active')}
                    onMouseMove={() => setActive(index)}
                    onClick={() => run(command)}
                  >
                    <CommandIcon aria-hidden size={16} />
                    {command.label}
                  </div>
                </li>
              );
            })}
          </ul>
          <div className="palette__status" role="status">
            {searching ? t('palette.searching') : hits.isError && term === query.trim() ? t('palette.searchFailed') : ''}
          </div>
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
