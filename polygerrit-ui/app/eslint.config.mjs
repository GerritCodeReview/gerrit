/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import path from 'path';
import js from '@eslint/js';
import google from 'eslint-config-google';
import jsdoc from 'eslint-plugin-jsdoc';
import importPlugin from 'eslint-plugin-import';
import regex from 'eslint-plugin-regex';
import typescriptEslint from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import lit from 'eslint-plugin-lit';
import { FlatCompat } from '@eslint/eslintrc';
import { fileURLToPath } from 'node:url';
import prettier from 'eslint-plugin-prettier';
import eslintConfigPrettier from 'eslint-config-prettier';
//import stylistic from '@stylistic/eslint-plugin'
import html from 'eslint-plugin-html';

const runFilesDir = process.env['RUNFILES_DIR'];

const bazelImportResolver = runFilesDir
  ? {
      settings: {
        'import/resolver': {
          node: {
            paths: [
              `${runFilesDir}/ui_npm/node_modules`,
              `${runFilesDir}/ui_dev_npm/node_modules`,
            ],
          },
        },
      },
    }
  : {};

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all,
});

export default [
  {
    ignores: [
      '**/node_modules',
      '**/rollup.config.js',
      'node_modules_licenses'
    ],
  },
  //...compat.extends('eslint:recommended', 'google'),
  //...compat.extends(runFilesDir ? `${runFilesDir}/npm/node_modules/gts/.eslintrc.json` : '../../node_modules/gts/.eslintrc.json'),
 js.configs.recommended,
  //...compat.extends('eslint-config-google'),
 google,
 //eslintConfigPrettier,
  {
    plugins: {
      html: html,
      lit: lit,
      import: importPlugin,
      jsdoc: jsdoc,
      prettier: prettier,
      regex: regex,
      //'@stylistic': stylistic
    },
  },
  {
    languageOptions: {
      ecmaVersion: 9,
      sourceType: 'module',
      globals: {
        browser: true,
        es6: true,
      },
    },
    rules: {
      'no-confusing-arrow': 'error',
      'newline-per-chained-call': ['error', { ignoreChainWithDepth: 2 }],
      'arrow-body-style': ['error', 'as-needed', { requireReturnForObjectLiteral: true }],
      'arrow-parens': ['error', 'as-needed'],
      'block-spacing': ['error', 'always'],
      'brace-style': ['error', '1tbs', { allowSingleLine: true }],
      camelcase: 'off',
      'comma-dangle': ['error', {
        arrays: 'always-multiline',
        objects: 'always-multiline',
        imports: 'always-multiline',
        exports: 'always-multiline',
        functions: 'never',
      }],
      'eol-last': 'off',
      'guard-for-in': 'error',
      indent: ['error', 2, {
        MemberExpression: 2,
        FunctionDeclaration: { body: 1, parameters: 2 },
        FunctionExpression: { body: 1, parameters: 2 },
        CallExpression: { arguments: 2 },
        ArrayExpression: 1,
        ObjectExpression: 1,
        SwitchCase: 1,
      }],
      'keyword-spacing': ['error', { after: true, before: true }],
      'lines-between-class-members': ['error', 'always'],
      'max-len': ['error', 80, 2, {
        ignoreComments: true,
        ignorePattern: '^import .*;$',
      }],
      'new-cap': ['error', {
        capIsNewExceptions: ['Polymer'],
        capIsNewExceptionPattern: '^.*Mixin$',
      }],
      'no-console': ['error', {
        allow: ['warn', 'error', 'info', 'debug', 'assert', 'group', 'groupEnd'],
      }],
      'no-multiple-empty-lines': ['error', { max: 1 }],
      'no-prototype-builtins': 'off',
      'no-redeclare': 'off',
      'no-trailing-spaces': 'error',
      'no-irregular-whitespace': 'error',
      'array-callback-return': ['error', { allowImplicit: true }],
      'no-restricted-syntax': ['error',
        {
          selector: 'ExpressionStatement > CallExpression > MemberExpression[object.name="test"][property.name="only"]',
          message: 'Remove test.only.',
        },
        {
          selector: 'ExpressionStatement > CallExpression > MemberExpression[object.name="suite"][property.name="only"]',
          message: 'Remove suite.only.',
        },
      ],
      'no-undef': 'error',
      'no-useless-escape': 'off',
      'no-var': 'error',
      'operator-linebreak': 'off',
      'object-shorthand': ['error', 'always'],
      'padding-line-between-statements': [
        'error',
        { blankLine: 'always', prev: 'class', next: '*' },
        { blankLine: 'always', prev: '*', next: 'class' },
      ],
      'prefer-arrow-callback': 'error',
      'prefer-const': 'error',
      'prefer-promise-reject-errors': 'error',
      'prefer-spread': 'error',
      'prefer-object-spread': 'error',
      'quote-props': ['error', 'consistent-as-needed'],
      'semi': ['error', 'always'],
      'template-curly-spacing': 'error',
      'require-jsdoc': 'off',
      'valid-jsdoc': 'off',
      'jsdoc/check-alignment': 2,
      'jsdoc/check-examples': 0,
      'jsdoc/check-indentation': 0,
      'jsdoc/check-param-names': 0,
      'jsdoc/check-syntax': 0,
      'jsdoc/check-tag-names': ['error', {
        definedTags: ['attr', 'lit', 'mixinFunction', 'mixinClass', 'polymer'],
      }],
      'jsdoc/check-types': 0,
      'jsdoc/implements-on-classes': 2,
      'jsdoc/match-description': 0,
      'jsdoc/no-types': 0,
      'jsdoc/no-undefined-types': 0,
      'jsdoc/require-description': 0,
      'jsdoc/require-description-complete-sentence': 0,
      'jsdoc/require-example': 0,
      'jsdoc/require-hyphen-before-param-description': 0,
      'jsdoc/require-jsdoc': 0,
      'jsdoc/require-param': 0,
      'jsdoc/require-param-description': 0,
      'jsdoc/require-param-name': 2,
      'jsdoc/require-returns': 0,
      'jsdoc/require-returns-check': 0,
      'jsdoc/require-returns-description': 0,
      'jsdoc/valid-types': 2,
      'jsdoc/require-file-overview': ['error', {
        tags: {
          license: {
            mustExist: true,
            preventDuplicates: true,
          },
        },
      }],
      'import/no-self-import': 2,
      'import/no-cycle': 0,
      'import/no-useless-path-segments': 2,
      'import/no-unused-modules': 2,
      'import/no-default-export': 2,
      'regex/invalid': ['error', [{
        regex: 'Licensed under',
        message: 'Please use SPDX license headers.',
      }]],
    },
  },
  {
    files: ['eslint.config.mjs'],
    languageOptions: {
      globals: { node: true },
    },
  },
  {
    files: ['**/*.js'],
    rules: {
      'jsdoc/require-param-type': 2,
      'jsdoc/require-returns-type': 2,
      'import/named': 2,
    },
    languageOptions: {
      globals: {
        goog: 'readonly',
      },
    },
  },
  {
    files: ['**/*.mjs'],
    rules: {
      'jsdoc/require-param-type': 2,
      'jsdoc/require-returns-type': 2,
      'import/named': 2,
    },
    languageOptions: {
      globals: {
        goog: 'readonly',
      },
      ecmaVersion: 2021
    },
  },
  {
    files: ['**/api/*.ts'],
    rules: {
      'regex/invalid': ['error', [{
        regex: 'export interface',
        message: 'All interfaces in the api/ dir must have "declare"',
        replacement: 'export declare interface',
      }]],
    },
  },
  ...compat.extends(
    runFilesDir ? `${runFilesDir}/npm/node_modules/gts/.eslintrc.json` : '../../node_modules/gts/.eslintrc.json'
  ).map(config => ({
    ...config,
    files: ['**/*.ts'],
    rules: {
      'no-constant-binary-expression': 'off',
      'regex/invalid': ['error', [{
        regex: '\'lit/decorators\'',
        message: 'use \'lit/decorators.js\' instead',
        replacement: '\'lit/decorators.js\'',
      }, {
        regex: '\'lit/directives/([^.\']*)\'',
        message: 'use \'lit/directives/foo.js\' instead',
        replacement: {
          function: 'return "\'lit/directives/" + $[1] + ".js\'"',
        },
      }]],
      'no-restricted-imports': ['error', {
        name: 'lit-html/static',
        message: 'Use lit instead',
      }, {
        name: '@lit/reactive-element',
        message: 'Use lit instead',
      }, {
        name: '@polymer/decorators/lib/decorators',
        message: 'Use @polymer/decorators instead',
      }],
      '@typescript-eslint/no-empty-object-type': 'off',
      '@typescript-eslint/no-floating-promises': 'off',
      '@typescript-eslint/no-unsafe-function-type': 'off',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/ban-ts-comment': 'off',
      '@typescript-eslint/restrict-plus-operands': 'error',
      '@typescript-eslint/no-unnecessary-type-assertion': 'error',
      'require-await': 'off',
      '@typescript-eslint/require-await': 'error',
      '@typescript-eslint/no-confusing-void-expression': ['error', {
        ignoreArrowShorthand: true,
      }],
      "no-unused-vars": 'off',
      "@typescript-eslint/no-unused-vars": ['error', { argsIgnorePattern: '^_', caughtErrors: 'none' }],
      '@typescript-eslint/no-unused-expressions': 'off',
      '@typescript-eslint/no-unsafe-declaration-merging': 'off',
      'n/no-unsupported-features/es-builtins': 'off',
      'n/no-unsupported-features/node-builtins': 'off',
      'no-invalid-this': 'off',
      'n/no-extraneous-import': 'off',
      'no-undef': 'off',
      'jsdoc/no-types': 2,
    },
    plugins: {
      '@typescript-eslint': typescriptEslint,
    },
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        project: path.resolve(__dirname, './tsconfig_eslint.json'),
      },
    },
  })),
  {
    files: ['*.html', 'test.js', 'test-infra.js'],
    rules: {
      'jsdoc/require-file-overview': 'off',
    },
  },
  {
    files: [
      '*.html',
      '*_test.js',
      'a11y-test-utils.js',
    ],
    // Additional global variables allowed in tests
    globals: {
      // Global variables from 3rd party test libraries/frameworks.
      // You can extend this list if you want to use other global
      // variables from these libraries and import is not possible
      flush: 'readonly',
      setup: 'readonly',
      sinon: 'readonly',
      stub: 'readonly',
      suite: 'readonly',
      suiteSetup: 'readonly',
      suiteTeardown: 'readonly',
      teardown: 'readonly',
      test: 'readonly',
    },
  },
  {
    files: ['import-href.js'],
    globals: {
      HTMLImports: 'readonly',
    },
  },
  {
    files: ['samples/**/*.js'],
    globals: {
      // Settings for samples. You can add globals here if you want to use it
      Gerrit: 'readonly',
      Polymer: 'readonly',
    },
  },
  {
    files: ['*_html.js', 'gr-icons.js', '*-theme.js', '*-styles.js'],
    rules: {
      'max-len': 'off',
    },
  },
  {
    files: ['*_html.js'],
    rules: {
      'prettier/prettier': ['error', {
        bracketSpacing: false,
        singleQuote: true,
      }],
    },
  },
  {
    files: ['*.ts'],
    excludedFiles: '*_html.ts',
    rules: {
      'lit/attribute-value-entities': 'error',
      'lit/binding-positions': 'error',
      'lit/no-duplicate-template-bindings': 'error',
      'lit/no-invalid-escape-sequences': 'error',
      'lit/no-invalid-html': 'error',
      'lit/no-legacy-template-syntax': 'error',
      'lit/no-legacy-imports': 'error',
      'lit/no-private-properties': 'error',
      'lit/no-property-change-update': 'error',
      'lit/no-template-bind': 'error',
      'lit/no-useless-template-literals': 'error',
      'lit/no-value-attribute': 'error',
      'lit/prefer-static-styles': 'error',
      'lit/quoted-expressions': ['error', 'never'],
    },
  },
  {
    settings: {
      'html/report-bad-indent': 'error',
      'import/resolver': {
        node: {},
        [path.resolve(__dirname, './.eslint-ts-resolver.js')]: {},
      },
      'jsdoc': {
        tagNamePreference: {
          returns: 'return',
          file: 'fileoverview',
        },
      }
    }
  },
  bazelImportResolver,
];
