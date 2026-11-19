/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useState } from 'react';
import { useI18n, LOCALE_MANIFESTS, type Locale, type MessageKey } from '../i18n';
import {
  START_PAGES,
  afterComplete as storedAfterComplete,
  setAfterComplete,
  setTimeDisplay,
  timeDisplay as storedTimeDisplay,
  type AfterComplete,
  type TimeDisplay,
  density as storedDensity,
  setDensity,
  setStartPage,
  startPage as storedStartPage,
  startPath,
  type Density,
  type StartPage,
} from '../lib/userPrefs';
import { formatUtc } from '../lib/dates';
import { useNotifyPreference } from '../tasklist/notifications';
import { CardFieldsSettings } from '../tasklist/CardFieldsSettings';
import { useTheme, type ThemeChoice } from '../theme/theme';
import { Notice } from '../ui';
import { useToast } from '../ui/toast';
import { useSession } from './session';

const THEMES: { value: ThemeChoice; label: MessageKey }[] = [
  { value: 'system', label: 'prefs.theme.system' },
  { value: 'light', label: 'prefs.theme.light' },
  { value: 'dark', label: 'prefs.theme.dark' },
];

const START_LABELS: Record<StartPage, MessageKey> = {
  home: 'nav.home',
  tasklist: 'nav.tasklist',
  processes: 'nav.processes',
  cockpit: 'nav.cockpit',
  admin: 'nav.admin',
};

function Choice<T extends string>({
  legend,
  name,
  value,
  options,
  onChange,
}: {
  legend: string;
  name: string;
  value: T;
  options: { value: T; label: string; lang?: string }[];
  onChange: (value: T) => void;
}) {
  return (
    <fieldset className="prefs__group">
      <legend className="prefs__legend">{legend}</legend>
      <div className="prefs__options">
        {options.map((option) => (
          <label key={option.value} className="option" lang={option.lang}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

/** What a user sets for themselves: language, theme, start page, notices and density. */
export function PreferencesPage() {
  const { t, locale, setLocale, dateTime, relative } = useI18n();
  const { choice, setChoice } = useTheme();
  const session = useSession();
  const toast = useToast();
  const [notify, toggleNotify] = useNotifyPreference();
  const [start, setStart] = useState<StartPage>(storedStartPage);
  const [dense, setDense] = useState<Density>(storedDensity);
  const [after, setAfter] = useState<AfterComplete>(storedAfterComplete);
  const [times, setTimes] = useState<TimeDisplay>(storedTimeDisplay);
  const sample = new Date(Date.now() - 3 * 60 * 60 * 1000);
  const permission = typeof Notification === 'undefined' ? 'unsupported' : Notification.permission;
  const starts = START_PAGES.filter((page) => startPath(page, session.authorizedApps) !== null);

  return (
    <div className="page prefs">
      <header className="prefs__header">
        <h1 className="prefs__title">{t('userPrefs.title')}</h1>
        <p className="muted">{t('userPrefs.subtitle')}</p>
      </header>
      <section className="prefs__card">
        <div className="prefs__group">
          <label className="prefs__legend" htmlFor="prefs-locale">
            {t('prefs.language')}
          </label>
          {/* thirteen languages read better as a list than as a row of radios */}
          <select
            id="prefs-locale"
            className="input prefs__select"
            value={locale}
            onChange={(event) => setLocale(event.target.value as Locale)}
          >
            {LOCALE_MANIFESTS.map(({ tag, nativeName }) => (
              <option key={tag} value={tag} lang={tag}>
                {nativeName}
              </option>
            ))}
          </select>
        </div>
        <Choice<ThemeChoice>
          legend={t('prefs.theme')}
          name="theme"
          value={choice}
          options={THEMES.map((theme) => ({ value: theme.value, label: t(theme.label) }))}
          onChange={setChoice}
        />
        <Choice<Density>
          legend={t('userPrefs.density')}
          name="density"
          value={dense}
          options={[
            { value: 'comfortable', label: t('userPrefs.density.comfortable') },
            { value: 'compact', label: t('userPrefs.density.compact') },
          ]}
          onChange={(value) => {
            setDensity(value);
            setDense(value);
          }}
        />
        <Choice<TimeDisplay>
          legend={t('userPrefs.times')}
          name="times"
          value={times}
          options={[
            { value: 'local', label: t('userPrefs.times.local', { example: dateTime(sample) }) },
            { value: 'relative', label: t('userPrefs.times.relative', { example: relative(sample) }) },
            { value: 'utc', label: t('userPrefs.times.utc', { example: formatUtc(sample, locale) }) },
          ]}
          onChange={(value) => {
            setTimeDisplay(value);
            setTimes(value);
          }}
        />
        {session.authorizedApps.includes('tasklist') && (
          <Choice<AfterComplete>
            legend={t('userPrefs.afterComplete')}
            name="afterComplete"
            value={after}
            options={[
              { value: 'next', label: t('userPrefs.afterComplete.next') },
              { value: 'list', label: t('userPrefs.afterComplete.list') },
            ]}
            onChange={(value) => {
              setAfterComplete(value);
              setAfter(value);
            }}
          />
        )}
        <div className="prefs__group">
          <label className="prefs__legend" htmlFor="prefs-start">
            {t('userPrefs.start')}
          </label>
          <select
            id="prefs-start"
            className="input prefs__select"
            value={start}
            onChange={(event) => {
              const value = event.target.value as StartPage;
              setStartPage(value);
              setStart(value);
              toast.show({ title: t('userPrefs.saved') });
            }}
          >
            {starts.map((page) => (
              <option key={page} value={page}>
                {t(START_LABELS[page])}
              </option>
            ))}
          </select>
          <p className="field__hint">{t('userPrefs.startHint')}</p>
        </div>
        <fieldset className="prefs__group">
          <legend className="prefs__legend">{t('userPrefs.notifications')}</legend>
          <label className="option">
            <input type="checkbox" checked={notify} onChange={toggleNotify} />
            {t('userPrefs.notifyTasks')}
          </label>
          {notify && permission === 'denied' && <Notice tone="warning">{t('userPrefs.notifyBlocked')}</Notice>}
          {permission === 'unsupported' && <p className="field__hint">{t('userPrefs.notifyUnsupported')}</p>}
        </fieldset>
        <p className="field__hint">{t('userPrefs.local')}</p>
      </section>
      {session.authorizedApps.includes('tasklist') && (
        <section className="prefs__card">
          <CardFieldsSettings />
        </section>
      )}
    </div>
  );
}
