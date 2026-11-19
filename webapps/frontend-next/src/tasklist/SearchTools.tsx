/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import type { FormEvent } from 'react';
import { DropdownMenu } from 'radix-ui';
import { Bookmark, BookmarkPlus, Columns3, Link2, Trash2, X } from 'lucide-react';
import { useI18n } from '../i18n';
import { Button, Dialog, Field, IconButton } from '../ui';
import { useToast } from '../ui/toast';
import type { Criterion } from './search';
import { normaliseCardVariables, type SavedSearch } from './searchLinks';
import { MenuContent } from '../ui/menu';

/**
 * The menu of the search: copy a link to it, save it under a name, apply
 * a saved one and choose the process variables shown on the task cards.
 */
export function SearchTools({
  link,
  criteria,
  onlyMine,
  saved,
  onSaved,
  onApply,
  cardVariables,
  onCardVariables,
}: {
  link: string;
  criteria: Criterion[];
  onlyMine: boolean;
  saved: SavedSearch[];
  onSaved: (saved: SavedSearch[]) => void;
  onApply: (search: SavedSearch) => void;
  cardVariables: string[];
  onCardVariables: (names: string[]) => void;
}) {
  const { t } = useI18n();
  const toast = useToast();
  const [dialog, setDialog] = useState<'save' | 'manage' | 'fields' | null>(null);
  const [name, setName] = useState('');
  const [fields, setFields] = useState('');

  const copy = () => {
    void navigator.clipboard
      ?.writeText(link)
      .then(() => toast.show({ title: t('searchTools.copied') }))
      .catch(() => toast.show({ tone: 'error', title: t('searchTools.copyFailed') }));
  };

  const save = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    onSaved([...saved.filter((search) => search.name !== trimmed), { name: trimmed, criteria, onlyMine }]);
    toast.show({ title: t('searchTools.savedAs', { name: trimmed }) });
    setDialog(null);
  };

  return (
    <>
      <DropdownMenu.Root>
        <DropdownMenu.Trigger asChild>
          <IconButton icon={Bookmark} label={t('searchTools.menu')} />
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <MenuContent>
            <DropdownMenu.Item className="menu__item" onSelect={copy}>
              <Link2 aria-hidden size={15} />
              {t('searchTools.copyLink')}
            </DropdownMenu.Item>
            <DropdownMenu.Item
              className="menu__item"
              onSelect={() => {
                setName('');
                setDialog('save');
              }}
            >
              <BookmarkPlus aria-hidden size={15} />
              {t('searchTools.save')}
            </DropdownMenu.Item>
            <DropdownMenu.Item
              className="menu__item"
              onSelect={() => {
                setFields(cardVariables.join(', '));
                setDialog('fields');
              }}
            >
              <Columns3 aria-hidden size={15} />
              {t('searchTools.fields')}
            </DropdownMenu.Item>
            {saved.length > 0 && (
              <>
                <DropdownMenu.Separator className="menu__separator" />
                <DropdownMenu.Label className="menu__label">{t('searchTools.saved')}</DropdownMenu.Label>
                {saved.map((search) => (
                  <DropdownMenu.Item key={search.name} className="menu__item" onSelect={() => onApply(search)}>
                    {search.name}
                  </DropdownMenu.Item>
                ))}
                <DropdownMenu.Item className="menu__item" onSelect={() => setDialog('manage')}>
                  {t('searchTools.manage')}
                </DropdownMenu.Item>
              </>
            )}
          </MenuContent>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>

      <Dialog open={dialog === 'save'} onOpenChange={(open) => !open && setDialog(null)} title={t('searchTools.save')}>
        <form className="confirm" onSubmit={save}>
          <p className="confirm__text">
            {criteria.length || onlyMine ? t('searchTools.saveText') : t('searchTools.saveEmpty')}
          </p>
          <Field label={t('searchTools.name')}>
            {(props) => (
              <input
                {...props}
                className="input"
                maxLength={80}
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            )}
          </Field>
          <div className="confirm__actions">
            <Button onClick={() => setDialog(null)}>{t('action.cancel')}</Button>
            <Button type="submit" variant="primary" disabled={!name.trim()}>
              {t('action.save')}
            </Button>
          </div>
        </form>
      </Dialog>

      <Dialog
        open={dialog === 'manage'}
        onOpenChange={(open) => !open && setDialog(null)}
        title={t('searchTools.manage')}
      >
        <ul className="saved-searches">
          {saved.map((search) => (
            <li key={search.name}>
              <span>{search.name}</span>
              <span className="muted">{t('searchTools.criteria', { count: search.criteria.length })}</span>
              <IconButton
                icon={Trash2}
                label={t('searchTools.remove', { name: search.name })}
                onClick={() => onSaved(saved.filter((candidate) => candidate.name !== search.name))}
              />
            </li>
          ))}
        </ul>
      </Dialog>

      <Dialog
        open={dialog === 'fields'}
        onOpenChange={(open) => !open && setDialog(null)}
        title={t('searchTools.fields')}
        description={t('searchTools.fieldsText')}
      >
        <form
          className="confirm"
          onSubmit={(event) => {
            event.preventDefault();
            onCardVariables(normaliseCardVariables(fields.split(',')));
            setDialog(null);
          }}
        >
          <Field label={t('searchTools.fieldNames')} hint={t('searchTools.fieldNamesHint')}>
            {(props) => (
              <input {...props} className="input" value={fields} onChange={(event) => setFields(event.target.value)} />
            )}
          </Field>
          {cardVariables.length > 0 && (
            <div className="chips">
              {cardVariables.map((variable) => (
                <span key={variable} className="chip">
                  {variable}
                  <IconButton
                    className="icon-btn--sm"
                    icon={X}
                    label={t('searchTools.remove', { name: variable })}
                    onClick={() => onCardVariables(cardVariables.filter((candidate) => candidate !== variable))}
                  />
                </span>
              ))}
            </div>
          )}
          <div className="confirm__actions">
            <Button onClick={() => setDialog(null)}>{t('action.cancel')}</Button>
            <Button type="submit" variant="primary">
              {t('action.save')}
            </Button>
          </div>
        </form>
      </Dialog>
    </>
  );
}
