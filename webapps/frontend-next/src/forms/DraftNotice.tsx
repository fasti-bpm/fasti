/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useI18n } from '../i18n';
import type { FormDraft } from './drafts';

/** Tells the user that the form shows a draft and lets them start over. */
export function DraftNotice({
  draft,
  disabled,
  onDiscard,
}: {
  draft: FormDraft<unknown> | null;
  /** while the form is being submitted, the values may not be replaced */
  disabled?: boolean;
  onDiscard: () => void;
}) {
  const { t, relative } = useI18n();
  if (!draft) {
    return null;
  }
  const prefill = draft.kind === 'variables';
  return (
    <div className="notice notice--info notice--actions form-draft" role="status">
      <span className="notice__body">
        {prefill ? t('form.draft.prefilled') : t('form.draft.restored', { time: relative(new Date(draft.savedAt)) })}
      </span>
      <button type="button" className="link" disabled={disabled} onClick={onDiscard}>
        {prefill ? t('form.draft.clearPrefill') : t('form.draft.discard')}
      </button>
    </div>
  );
}
