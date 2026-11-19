/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Save } from 'lucide-react';
import { notifyUnauthorized } from '../api/http';
import { SessionContext } from '../app/session';
import { config } from '../config';
import { formFrameUrl, formsSettings, type FormsSettings } from '../env';
import { useI18n } from '../i18n';
import { useTheme } from '../theme/theme';
import { Button, Loading, Notice } from '../ui';
import { FormActions } from './FormHost';
import { FormBroker } from './embedded/brokerHost';
import {
  acceptFrameEvent,
  postToFrame,
  type FrameMessage,
  type HostMessage,
} from './embedded/protocol';
import type { FormRendererProps } from './types';
import './forms.css';

/**
 * What the frame may do in mode `sandboxed`: scripts, forms, popups and
 * downloads, but no origin of its own (no `allow-same-origin`): the frame
 * has an opaque origin, so that it can neither use the session of the user
 * nor reach this page. No top navigation and no modal dialogs either.
 */
export const FRAME_SANDBOX = 'allow-scripts allow-forms allow-popups allow-downloads';

/**
 * What the frame may do in modes `same-origin` and `separate-origin`: in
 * addition, it keeps its origin (this origin, or the forms origin).
 */
export const FRAME_SANDBOX_WITH_ORIGIN = FRAME_SANDBOX + ' allow-same-origin';

export interface FrameSetup {
  sandbox: string;
  /** the origin messages of the frame must come from */
  frameOrigin: string;
  /** the target origin of messages to the frame */
  targetOrigin: string;
  /** whether the requests of the frame are brokered by this application */
  broker: boolean;
}

/** How the frame is embedded and addressed in a mode of the forms isolation. */
export function frameSetup(settings: FormsSettings, origin: string = window.location.origin): FrameSetup {
  switch (settings.isolation) {
    case 'same-origin':
      return { sandbox: FRAME_SANDBOX_WITH_ORIGIN, frameOrigin: origin, targetOrigin: origin, broker: false };
    case 'separate-origin':
      // a "separate" origin that is this origin would give the frame this
      // origin and its session: fall back to the opaque sandbox
      if (settings.origin && settings.origin !== origin) {
        const forms = settings.origin;
        return { sandbox: FRAME_SANDBOX_WITH_ORIGIN, frameOrigin: forms, targetOrigin: forms, broker: true };
      }
      return { sandbox: FRAME_SANDBOX, frameOrigin: 'null', targetOrigin: '*', broker: true };
    default:
      // an opaque origin is serialized as "null" and cannot be addressed
      return { sandbox: FRAME_SANDBOX, frameOrigin: 'null', targetOrigin: '*', broker: true };
  }
}

type Status = 'loading' | 'loaded' | 'error';

/**
 * Renders an embedded HTML form (form key `embedded:...`). The form contains
 * markup and scripts of whoever deployed the process and needs `eval` and
 * inline scripts, which the policy of this application forbids. It is
 * therefore rendered by the form frame, a separate page with the policy of
 * the classic applications, which uses the embedded forms SDK of the classic
 * Tasklist. This component only tells the frame what to render, sizes it,
 * and offers the buttons.
 */
export function EmbeddedForm({
  target,
  form,
  disabled,
  submitLabel,
  submitted,
  reportFailure,
}: FormRendererProps) {
  const { t } = useI18n();
  const { resolved: theme } = useTheme();
  const frame = useRef<HTMLIFrameElement>(null);
  const [status, setStatus] = useState<Status>('loading');
  const [loadError, setLoadError] = useState('');
  const [height, setHeight] = useState(0);
  const [valid, setValid] = useState(false);
  const [busy, setBusy] = useState(false);
  const [prevented, setPrevented] = useState(false);
  const [saved, setSaved] = useState(false);

  const userId = useContext(SessionContext)?.userId ?? null;
  const [setup] = useState(() => frameSetup(formsSettings));
  // one broker per form: its allow-list is tied to the task or start event
  const [broker] = useState(() => (setup.broker ? new FormBroker(target, form, userId) : null));
  const post = useCallback(
    (message: HostMessage) => postToFrame(frame.current?.contentWindow, message, setup.targetOrigin),
    [setup],
  );

  // the message listener is registered once; it reads the current props here
  const current = useRef({ target, disabled, busy, theme, submitted, reportFailure });
  useEffect(() => {
    current.current = { target, disabled, busy, theme, submitted, reportFailure };
  });

  const sendInit = useCallback(() => {
    const { target: currentTarget, disabled: currentDisabled, theme: currentTheme } =
      current.current;
    post({
      type: 'init',
      target: currentTarget,
      disabled: currentDisabled,
      theme: currentTheme,
      csrfCookieName: config.csrfCookieName,
      transport: broker ? 'broker' : 'direct',
      storedValues: broker ? broker.storedValues() : null,
    });
  }, [post, broker]);

  useEffect(() => {
    const handle = (message: FrameMessage) => {
      switch (message.type) {
        case 'ready':
          sendInit();
          break;
        case 'loaded':
          setStatus('loaded');
          // the frame takes changes of the state only once its form is running; a change
          // sent before (e.g. the task was claimed while the form was loading) is repeated
          post({ type: 'disabled', disabled: current.current.disabled || current.current.busy });
          break;
        case 'resize':
          setHeight(message.height);
          break;
        case 'state':
          setValid(message.valid);
          if (message.dirty) {
            setSaved(false);
          }
          break;
        case 'submitted':
          setBusy(false);
          current.current.submitted(message.processInstance ?? undefined);
          break;
        case 'failed':
          setBusy(false);
          if (message.prevented) {
            setPrevented(true);
          } else {
            current.current.reportFailure(new Error(message.message));
          }
          break;
        case 'error':
          setStatus('error');
          setLoadError(message.message);
          break;
        case 'unauthorized':
          setBusy(false);
          notifyUnauthorized();
          break;
        case 'request': {
          if (!broker) {
            break;
          }
          const id = message.id;
          void broker.request(message).then((response) => {
            post({
              type: 'response',
              id,
              status: response.status,
              contentType: response.contentType,
              body: response.text,
            });
          });
          break;
        }
        case 'download':
          broker?.download(message.url, message.filename).catch((error: unknown) => {
            current.current.reportFailure(error);
          });
          break;
        case 'storage':
          broker?.store(message.value);
          break;
      }
    };
    const listener = (event: MessageEvent) => {
      const message = acceptFrameEvent(event, frame.current?.contentWindow, setup.frameOrigin);
      if (message) {
        handle(message);
      }
    };
    window.addEventListener('message', listener);
    return () => window.removeEventListener('message', listener);
  }, [broker, post, sendInit, setup.frameOrigin]);

  useEffect(() => {
    post({ type: 'disabled', disabled: disabled || busy });
  }, [post, disabled, busy]);

  useEffect(() => {
    post({ type: 'theme', theme });
  }, [post, theme]);

  const onSubmit = () => {
    if (status !== 'loaded' || !valid || busy) {
      return;
    }
    setPrevented(false);
    setSaved(false);
    reportFailure(null);
    setBusy(true);
    post({ type: 'submit' });
  };

  const onSave = () => {
    post({ type: 'store' });
    setSaved(true);
  };

  return (
    <div className="form embedded-form">
      {status === 'loading' && <Loading label={t('form.loading')} />}
      {status === 'error' && (
        <Notice tone="danger" title={t('form.embedded.loadFailed')}>
          {loadError}
        </Notice>
      )}
      <iframe
        ref={frame}
        className="embedded-form__frame"
        data-status={status}
        title={t('form.embedded.title')}
        src={formFrameUrl()}
        sandbox={setup.sandbox}
        referrerPolicy="same-origin"
        style={{ height: status === 'loaded' ? height : 0 }}
        // the frame announces itself with `ready`; the load event is a
        // fallback in case that message arrived before the listener
        onLoad={sendInit}
      />
      {prevented && <Notice tone="warning">{t('form.embedded.prevented')}</Notice>}
      {saved && <Notice>{t('form.embedded.saved')}</Notice>}
      {status === 'loaded' && (
        <FormActions
          label={submitLabel}
          busy={busy}
          disabled={disabled || !valid}
          onSubmit={onSubmit}
        >
          <Button
            variant="ghost"
            icon={Save}
            disabled={disabled || busy}
            title={t('form.embedded.saveHint')}
            onClick={onSave}
          >
            {t('form.embedded.save')}
          </Button>
        </FormActions>
      )}
    </div>
  );
}
