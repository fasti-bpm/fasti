/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['target/**', 'node/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.strict,
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      'no-console': 'error',
      // server-provided strings are never rendered as HTML
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'Do not render strings as HTML.',
        },
        {
          selector: "AssignmentExpression[left.property.name=/^(innerHTML|outerHTML)$/]",
          message: 'Do not render strings as HTML.',
        },
      ],
      '@typescript-eslint/no-non-null-assertion': 'error',
      // request<void>() names the type of an empty response
      '@typescript-eslint/no-invalid-void-type': 'off',
    },
  },
  {
    files: ['scripts/**/*.mjs', '*.config.{js,ts}'],
    languageOptions: { globals: globals.node },
  },
  {
    // end-to-end tests run in Node.js and hand small functions to the browser
    files: ['e2e/**/*.ts'],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
    rules: {
      // Playwright fixtures take an empty object pattern when they need no others
      'no-empty-pattern': 'off',
    },
  },
);
