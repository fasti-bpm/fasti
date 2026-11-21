/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './plugins';
import { prepare } from './startup';
import { applyStoredTheme } from './theme/theme';
import { applyDensity } from './lib/userPrefs';
import { onPreferencesReplaced } from './lib/storage';
import './theme/tokens.css';
import './theme/base.css';
import './theme/ui.css';
import './theme/shell.css';
import './app/prefs.css';
import './theme/components.css';

applyStoredTheme();
applyDensity();
onPreferencesReplaced(() => applyDensity());

const root = document.getElementById('root');
if (root) {
  void prepare().then(() => {
    createRoot(root).render(
      <StrictMode>
        <App />
      </StrictMode>,
    );
  });
}
