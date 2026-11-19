/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import type { ReactNode } from 'react';
import { Maximize, PanelRight, Redo2, Undo2, ZoomIn, ZoomOut } from 'lucide-react';
import { useI18n } from '../i18n';
import { IconButton } from '../ui';

/** The commands every editor offers above its canvas. */
export function EditorToolbar({
  canUndo,
  canRedo,
  onUndo,
  onRedo,
  onZoom,
  panel,
  onPanel,
  children,
}: {
  canUndo: boolean;
  canRedo: boolean;
  onUndo: () => void;
  onRedo: () => void;
  /** only editors with a canvas that can be zoomed */
  onZoom?: (step: 1 | -1 | 0) => void;
  /** whether the properties panel is shown, for editors that have one */
  panel?: boolean;
  onPanel?: () => void;
  children?: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <div className="editor-toolbar" role="toolbar" aria-label={t('modeler.toolbar')}>
      <IconButton icon={Undo2} label={t('modeler.undo')} shortcut="Ctrl Z" disabled={!canUndo} onClick={onUndo} />
      <IconButton icon={Redo2} label={t('modeler.redo')} shortcut="Ctrl Y" disabled={!canRedo} onClick={onRedo} />
      {onZoom && (
        <>
          <span className="editor-toolbar__divider" aria-hidden />
          <IconButton icon={ZoomOut} label={t('modeler.zoomOut')} onClick={() => onZoom(-1)} />
          <IconButton icon={ZoomIn} label={t('modeler.zoomIn')} onClick={() => onZoom(1)} />
          <IconButton icon={Maximize} label={t('modeler.fit')} onClick={() => onZoom(0)} />
        </>
      )}
      <span className="editor-toolbar__spacer" />
      {children}
      {onPanel && (
        <IconButton
          icon={PanelRight}
          label={panel ? t('props.hide') : t('props.show')}
          aria-pressed={panel}
          className={panel ? 'is-active' : undefined}
          onClick={onPanel}
        />
      )}
    </div>
  );
}
