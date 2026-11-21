/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { ListFilter, Plus } from 'lucide-react';
import type { TaskFilter } from '../api/types';
import { useI18n } from '../i18n';
import { Button, EmptyState, ErrorState, Loading, Skeleton, cx } from '../ui';

const COLOR = /^#[0-9a-f]{3,8}$/i;

export interface FilterListProps {
  filters: TaskFilter[] | undefined;
  error: unknown;
  selectedId: string | null;
  onSelect: (filterId: string) => void;
  onRetry: () => void;
  canCreate: boolean;
  creating: boolean;
  onCreate: () => void;
}

/** The task filters of the user as a list; each one with the number of its tasks. */
export function FilterList(props: FilterListProps) {
  const { t } = useI18n();
  const { filters, error, selectedId, onSelect } = props;

  if (error) {
    return <ErrorState error={error} onRetry={props.onRetry} />;
  }
  if (!filters) {
    return (
      <Loading label={t('filters.loading')}>
        <Skeleton lines={5} className="skeleton--rows" />
      </Loading>
    );
  }
  if (filters.length === 0) {
    return (
      <EmptyState
        icon={ListFilter}
        title={t('filters.empty')}
        action={
          props.canCreate && (
            <Button icon={Plus} variant="primary" busy={props.creating} onClick={props.onCreate}>
              {t('filters.createAll')}
            </Button>
          )
        }
      >
        {props.canCreate ? t('filters.emptyHint') : t('filters.emptyNoPermission')}
      </EmptyState>
    );
  }
  return (
    <ul className="filters" aria-label={t('filters.title')}>
      {filters.map((filter) => {
        const color = filter.properties?.color;
        return (
          <li key={filter.id}>
            <button
              type="button"
              className={cx('filter', filter.id === selectedId && 'is-selected')}
              aria-current={filter.id === selectedId ? 'true' : undefined}
              onClick={() => onSelect(filter.id)}
            >
              <span
                className="filter__dot"
                style={color && COLOR.test(color) ? { background: color } : undefined}
                aria-hidden
              />
              <span className="filter__text">
                <span className="filter__name">{filter.name}</span>
                {filter.properties?.description && (
                  <span className="filter__description">{filter.properties.description}</span>
                )}
              </span>
              {filter.itemCount != null && (
                <span className="count" aria-label={t('filters.count', { count: filter.itemCount })}>
                  {filter.itemCount}
                </span>
              )}
            </button>
          </li>
        );
      })}
    </ul>
  );
}

/** The same choice as a compact control for narrow screens. */
export function FilterSelect({
  filters,
  selectedId,
  onSelect,
  onUse,
}: Pick<FilterListProps, 'filters' | 'selectedId' | 'onSelect'> & {
  /** the user turns to the menu: its entries are about to be seen */
  onUse?: () => void;
}) {
  const { t } = useI18n();
  if (!filters || filters.length === 0) {
    return null;
  }
  return (
    <label className="filter-select">
      <span className="sr-only">{t('filters.title')}</span>
      <ListFilter aria-hidden size={16} />
      <select
        className="input"
        value={selectedId ?? ''}
        onFocus={onUse}
        onPointerDown={onUse}
        onChange={(event) => onSelect(event.target.value)}
      >
        {filters.map((filter) => (
          <option key={filter.id} value={filter.id}>
            {filter.itemCount != null ? `${filter.name} (${filter.itemCount})` : filter.name}
          </option>
        ))}
      </select>
    </label>
  );
}
