/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import { defineConfig, type Plugin } from 'vitest/config';
import react from '@vitejs/plugin-react';
import packageJson from './package.json' with { type: 'json' };

/**
 * The index page is not served as a static file: the web application reads
 * it, replaces a few placeholders and sends the result (see
 * ProcessEnginesFilter). This plugin prepares the built page for that:
 *
 * - asset URLs are made absolute below `$APP_ROOT`, because the page is
 *   served from `{appRoot}/app/next/{engine}/` while the assets are located
 *   in `{appRoot}/app/next/assets/`,
 * - every script and module preload gets the `$CSP_NONCE` placeholder, which
 *   the server replaces with the nonce attribute of the response.
 *
 * The page must stay ASCII only and must not contain Maven filter tokens:
 * the WAR build filters HTML files and the server reads the page with the
 * platform encoding.
 */
function servletTemplate(): Plugin {
  return {
    name: 'fasti-servlet-template',
    apply: 'build',
    transformIndexHtml: {
      order: 'post',
      handler(html) {
        const result = html
          .replace(/(src|href)="\.\/assets\//g, '$1="$APP_ROOT/app/next/assets/')
          .replace(/<script(?=[\s>])/g, '<script $CSP_NONCE')
          .replace(/<link rel="modulepreload"/g, '<link $CSP_NONCE rel="modulepreload"');
        // eslint-disable-next-line no-control-regex
        if (/[^\x00-\x7F]/.test(result) || /\$\{|@[\w.]+@/.test(result)) {
          throw new Error('index.html must be ASCII only and free of Maven filter tokens');
        }
        return result;
      },
    },
  };
}

/**
 * Style sheets are served without a charset, so a browser may read them in a
 * legacy encoding. Icon fonts put private-use characters into `content`;
 * written as CSS escapes they survive any decoding.
 */
function asciiStyles(): Plugin {
  return {
    name: 'fasti-ascii-styles',
    apply: 'build',
    generateBundle(_options, bundle) {
      for (const file of Object.values(bundle)) {
        if (file.type === 'asset' && file.fileName.endsWith('.css') && typeof file.source === 'string') {
          // eslint-disable-next-line no-control-regex
          file.source = file.source.replace(/[^\x00-\x7F]/gu, (char) => `\\${char.codePointAt(0)?.toString(16)} `);
        }
      }
    },
  };
}

export default defineConfig(({ command }) => ({
  // relative URLs: chunks and styles resolve their dependencies relative to
  // themselves, so the bundle works below any context and application path.
  // The development server uses the path of a default installation, so that
  // the cookies of the proxied web application are visible to the page.
  base: command === 'serve' ? '/fasti/app/next/default/' : './',
  plugins: [react(), servletTemplate(), asciiStyles()],
  define: { __APP_VERSION__: JSON.stringify(packageJson.version) },
  build: {
    outDir: 'target/webapp/app/next',
    emptyOutDir: true,
    assetsDir: 'assets',
    target: 'es2022',
    sourcemap: false,
    chunkSizeWarningLimit: 1200,
  },
  server: {
    port: 5173,
    // OPTIONS requests must reach the engine, which answers them with the allowed actions
    cors: false,
    proxy: {
      // development only: a locally running distribution
      '/fasti/api': { target: 'http://localhost:18080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    // the end-to-end tests under e2e/ run with Playwright against a server
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    restoreMocks: true,
    // full-app tests take a few seconds when the files share the processor
    testTimeout: 20_000,
  },
}));
