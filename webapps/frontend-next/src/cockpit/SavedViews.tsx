/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { BookmarkPlus, X } from 'lucide-react';
import { useI18n } from '../i18n';
import { Button, cx } from '../ui';
import { useRemembered } from '../ui/hooks';
import { MAX_SAVED_VIEWS } from '../lib/storage';

/** A named set of filters of a list, kept with the preferences of the user. */
export interface SavedView {
  name: string;
  params: Record<string, string>;
}

/** A view that comes with the application; its filters may depend on the time it is applied. */
export interface BuiltInView {
  id: string;
  label: string;
  params: () => Record<string, string>;
}

/** The URL parameter that names the view that is shown; any other change of a filter clears it. */
export const VIEW_PARAM = 'view';

/** The filters of the given names that are set. */
export function currentFilters(params: URLSearchParams, keys: readonly string[]): Record<string, string> {
  const filters: Record<string, string> = {};
  for (const key of keys) {
    const value = params.get(key);
    if (value) filters[key] = value;
  }
  return filters;
}

/** The changes of the URL that show a view: every filter of the list is replaced. */
export function viewChanges(keys: readonly string[], view: Record<string, string>, id: string): Record<string, string | null> {
  const changes: Record<string, string | null> = Object.fromEntries(keys.map((key) => [key, null]));
  return { ...changes, ...view, [VIEW_PARAM]: id };
}

/**
 * Views of a list: a few built in, and those the user saved. The saved ones
 * are preferences, so they follow the user to other browsers.
 */
export function SavedViews({
  storageKey,
  keys,
  builtIns,
  params,
  onApply,
}: {
  storageKey: string;
  keys: readonly string[];
  builtIns: BuiltInView[];
  params: URLSearchParams;
  onApply: (changes: Record<string, string | null>) => void;
}) {
  const { t } = useI18n();
  const [saved, setSaved] = useRemembered<SavedView[]>(storageKey, []);
  const [naming, setNaming] = useState(false);
  const [name, setName] = useState('');
  const active = params.get(VIEW_PARAM);
  const filters = currentFilters(params, keys);
  const views = Array.isArray(saved) ? saved : [];

  // the views are kept with the preferences, which the server keeps up to a size
  const full = views.length >= MAX_SAVED_VIEWS && !views.some((view) => view.name === name.trim());
  const save = () => {
    const trimmed = name.trim();
    if (!trimmed || full) return;
    setSaved([...views.filter((view) => view.name !== trimmed), { name: trimmed, params: filters }]);
    onApply({ [VIEW_PARAM]: `saved:${trimmed}` });
    setNaming(false);
    setName('');
  };

  return (
    <div className="views" role="group" aria-label={t('views.title')}>
      <span className="views__label">{t('views.title')}</span>
      {builtIns.map((view) => (
        <button
          key={view.id}
          type="button"
          className={cx('toggle', active === view.id && 'is-on')}
          aria-pressed={active === view.id}
          onClick={() => onApply(active === view.id ? viewChanges(keys, {}, '') : viewChanges(keys, view.params(), view.id))}
        >
          {view.label}
        </button>
      ))}
      {views.map((view) => {
        const id = `saved:${view.name}`;
        return (
          <span key={id} className={cx('views__saved', active === id && 'is-on')}>
            <button
              type="button"
              className={cx('toggle', active === id && 'is-on')}
              aria-pressed={active === id}
              onClick={() => onApply(active === id ? viewChanges(keys, {}, '') : viewChanges(keys, view.params, id))}
            >
              {view.name}
            </button>
            <button
              type="button"
              className="views__remove"
              aria-label={t('views.remove', { name: view.name })}
              onClick={() => {
                setSaved(views.filter((other) => other.name !== view.name));
                if (active === id) onApply({ [VIEW_PARAM]: null });
              }}
            >
              <X aria-hidden size={12} />
            </button>
          </span>
        );
      })}
      {naming ? (
        <form
          className="views__form"
          onSubmit={(event) => {
            event.preventDefault();
            save();
          }}
        >
          <input
            className="input views__input"
            aria-label={t('views.name')}
            placeholder={t('views.name')}
            value={name}
            autoFocus
            maxLength={60}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => event.key === 'Escape' && setNaming(false)}
          />
          <Button size="sm" onClick={() => setNaming(false)}>
            {t('action.cancel')}
          </Button>
          <Button size="sm" variant="primary" type="submit" disabled={!name.trim() || full}>
            {t('views.save')}
          </Button>
          {full && (
            <span className="muted" role="status">
              {t('views.limit', { count: MAX_SAVED_VIEWS })}
            </span>
          )}
        </form>
      ) : (
        <Button size="sm" icon={BookmarkPlus} disabled={Object.keys(filters).length === 0} onClick={() => setNaming(true)}>
          {t('views.saveCurrent')}
        </Button>
      )}
    </div>
  );
}
