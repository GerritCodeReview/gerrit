/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Do not add any bazel-specific properties in this file to keep it clean.
// Please add such properties to the .eslintrc-bazel.js file
const path = require('path');

module.exports = {
  extends: ['eslint:recommended', 'google'],
  parserOptions: {
    ecmaVersion: 9,
    sourceType: 'module',
  },
  env: {
    browser: true,
    es6: true,
  },
  rules: {
    // https://eslint.org/docs/rules/no-confusing-arrow
    'no-confusing-arrow': 'error',
    // https://eslint.org/docs/rules/newline-per-chained-call
    'newline-per-chained-call': ['error', {ignoreChainWithDepth: 2}],
    // https://eslint.org/docs/rules/arrow-body-style
    'arrow-body-style': ['error', 'as-needed',
      {requireReturnForObjectLiteral: true}],
    // https://eslint.org/docs/rules/arrow-parens
    'arrow-parens': ['error', 'as-needed'],
    // https://eslint.org/docs/rules/block-spacing
    'block-spacing': ['error', 'always'],
    // https://eslint.org/docs/rules/brace-style
    'brace-style': ['error', '1tbs', {allowSingleLine: true}],
    // https://eslint.org/docs/rules/camelcase
    'camelcase': 'off',
    // https://eslint.org/docs/rules/comma-dangle
    'comma-dangle': ['error', {
      arrays: 'always-multiline',
      objects: 'always-multiline',
      imports: 'always-multiline',
      exports: 'always-multiline',
      functions: 'never',
    }],
    // https://eslint.org/docs/rules/eol-last
    'eol-last': 'off',
    'guard-for-in': 'error',
    // https://eslint.org/docs/rules/indent
    'indent': ['error', 2, {
      MemberExpression: 2,
      FunctionDeclaration: {body: 1, parameters: 2},
      FunctionExpression: {body: 1, parameters: 2},
      CallExpression: {arguments: 2},
      ArrayExpression: 1,
      ObjectExpression: 1,
      SwitchCase: 1,
    }],
    // https://eslint.org/docs/rules/keyword-spacing
    'keyword-spacing': ['error', {after: true, before: true}],
    // https://eslint.org/docs/rules/lines-between-class-members
    'lines-between-class-members': ['error', 'always'],
    // https://eslint.org/docs/rules/max-len
    'max-len': [
      'error',
      80,
      2,
      {
        ignoreComments: true,
        ignorePattern: '^import .*;$',
      },
    ],
    // https://eslint.org/docs/rules/new-cap
    'new-cap': ['error', {
      capIsNewExceptions: ['Polymer'],
      capIsNewExceptionPattern: '^.*Mixin$',
    }],
    // https://eslint.org/docs/rules/no-console
    'no-console': [
      'error',
      {
        allow: [
          'warn', 'error', 'info', 'debug', 'assert', 'group', 'groupEnd',
        ],
      },
    ],
    // https://eslint.org/docs/rules/no-multiple-empty-lines
    'no-multiple-empty-lines': ['error', {max: 1}],
    // https://eslint.org/docs/rules/no-prototype-builtins
    'no-prototype-builtins': 'off',
    // https://eslint.org/docs/rules/no-redeclare
    'no-redeclare': 'off',
    // https://eslint.org/docs/rules/no-trailing-spaces
    'no-trailing-spaces': 'error',
    // https://eslint.org/docs/rules/no-irregular-whitespace
    'no-irregular-whitespace': 'error',
    // https://eslint.org/docs/rules/array-callback-return
    'array-callback-return': ['error', {allowImplicit: true}],
    // https://eslint.org/docs/rules/no-restricted-syntax
    'no-restricted-syntax': [
      'error',
      {
        selector: 'ExpressionStatement > CallExpression > ' +
            'MemberExpression[object.name=\'test\'][property.name=\'only\']',
        message: 'Remove test.only.',
      },
      {
        selector: 'ExpressionStatement > CallExpression > ' +
            'MemberExpression[object.name=\'suite\'][property.name=\'only\']',
        message: 'Remove suite.only.',
      },
    ],
    // no-undef disables global variable.
    // "globals" declares allowed global variables.
    // https://eslint.org/docs/rules/no-undef
    'no-undef': ['error'],
    // https://eslint.org/docs/rules/no-useless-escape
    'no-useless-escape': 'off',
    // https://eslint.org/docs/rules/no-var
    'no-var': 'error',
    // https://eslint.org/docs/rules/operator-linebreak
    'operator-linebreak': 'off',
    // https://eslint.org/docs/rules/object-shorthand
    'object-shorthand': ['error', 'always'],
    // https://eslint.org/docs/rules/padding-line-between-statements
    'padding-line-between-statements': [
      'error',
      {
        blankLine: 'always',
        prev: 'class',
        next: '*',
      },
      {
        blankLine: 'always',
        prev: '*',
        next: 'class',
      },
    ],
    // https://eslint.org/docs/rules/prefer-arrow-callback
    'prefer-arrow-callback': 'error',
    // https://eslint.org/docs/rules/prefer-const
    'prefer-const': 'error',
    // https://eslint.org/docs/rules/prefer-promise-reject-errors
    'prefer-promise-reject-errors': 'error',
    // https://eslint.org/docs/rules/prefer-spread
    'prefer-spread': 'error',
    // https://eslint.org/docs/rules/prefer-object-spread
    'prefer-object-spread': 'error',
    // https://eslint.org/docs/rules/quote-props
    'quote-props': ['error', 'consistent-as-needed'],
    // https://eslint.org/docs/rules/semi
    'semi': ['error', 'always'],
    // https://eslint.org/docs/rules/template-curly-spacing
    'template-curly-spacing': 'error',

    // https://eslint.org/docs/rules/require-jsdoc
    'require-jsdoc': 0,
    // https://eslint.org/docs/rules/valid-jsdoc
    'valid-jsdoc': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-check-alignment
    'jsdoc/check-alignment': 2,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-check-examples
    'jsdoc/check-examples': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-check-indentation
    'jsdoc/check-indentation': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-check-param-names
    'jsdoc/check-param-names': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-check-syntax
    'jsdoc/check-syntax': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-check-tag-names
    'jsdoc/check-tag-names': ['error', {
      definedTags: ['attr', 'lit', 'mixinFunction', 'mixinClass', 'polymer'],
    }],
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-check-types
    'jsdoc/check-types': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-implements-on-classes
    'jsdoc/implements-on-classes': 2,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-match-description
    'jsdoc/match-description': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-newline-after-description
    'jsdoc/newline-after-description': 2,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-no-types
    'jsdoc/no-types': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-no-undefined-types
    'jsdoc/no-undefined-types': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-description
    'jsdoc/require-description': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-description-complete-sentence
    'jsdoc/require-description-complete-sentence': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-example
    'jsdoc/require-example': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-hyphen-before-param-description
    'jsdoc/require-hyphen-before-param-description': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-jsdoc
    'jsdoc/require-jsdoc': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-param
    'jsdoc/require-param': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-param-description
    'jsdoc/require-param-description': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-param-name
    'jsdoc/require-param-name': 2,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-returns
    'jsdoc/require-returns': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-returns-check
    'jsdoc/require-returns-check': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-returns-description
    'jsdoc/require-returns-description': 0,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-valid-types
    'jsdoc/valid-types': 2,
    // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-file-overview
    'jsdoc/require-file-overview': ['error', {
      tags: {
        license: {
          mustExist: true,
          preventDuplicates: true,
        },
      },
    }],
    // https://github.com/benmosher/eslint-plugin-import/blob/master/docs/rules/no-self-import.md
    'import/no-self-import': 2,
    // The no-cycle rule is slow, because it doesn't cache dependencies.
    // Disable it.
    // https://github.com/benmosher/eslint-plugin-import/blob/master/docs/rules/no-cycle.md
    'import/no-cycle': 0,
    // https://github.com/benmosher/eslint-plugin-import/blob/master/docs/rules/no-useless-path-segments.md
    'import/no-useless-path-segments': 2,
    // https://github.com/benmosher/eslint-plugin-import/blob/master/docs/rules/no-unused-modules.md
    'import/no-unused-modules': 2,
    // https://github.com/benmosher/eslint-plugin-import/blob/master/docs/rules/no-default-export.md
    'import/no-default-export': 2,
    // Prevents certain identifiers being used.
    // Prefer flush() over flushAsynchronousOperations().
    'id-blacklist': ['error', 'flushAsynchronousOperations'],
    'regex/invalid': [
      'error', [{
        // eslint-disable-next-line regex/invalid
        regex: 'Licensed under',
        message: 'Please use SPDX license headers.',
      }],
    ],
  },

  // List of allowed globals in all files
  globals: {
    // Polygerrit global variables.
    // You must not add anything new in this list!
    // Instead export variables from modules
    // TODO(dmfilippov): Remove global variables from polygerrit
    // Global variables from 3rd party libraries.
    // You should not add anything in this list, always try to import
    // If import is not possible - you can extend this list
    ShadyCSS: 'readonly',
    linkify: 'readonly',
    security: 'readonly',
  },
  overrides: [
    {
      files: ['.eslintrc.js', '.eslintrc-bazel.js'],
      env: {
        browser: false,
        es6: true,
        node: true,
      },
    },
    {
      // .js-only rules
      files: ['**/*.js'],
      rules: {
        // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-param-type
        'jsdoc/require-param-type': 2,
        // https://github.com/gajus/eslint-plugin-jsdoc#eslint-plugin-jsdoc-rules-require-returns-type
        'jsdoc/require-returns-type': 2,
        // The rule is required for .js files only, because typescript compiler
        // always checks import.
        'import/no-unresolved': 2,
        'import/named': 2,
      },
      globals: {
        goog: 'readonly',
      },
    },
    {
      files: ['**/api/*.ts'],
      rules: {
        'regex/invalid': [
          'error', [{
            regex: 'export interface',
            message: 'All interfaces in the api/ dir must have "declare"',
            replacement: 'export declare interface',
          }],
        ],
      },
    },
    {
      files: ['**/*.ts'],
      extends: [require.resolve('gts/.eslintrc.json')],
      rules: {
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
        '@typescript-eslint/no-explicit-any': 'error',
        // See https://github.com/GoogleChromeLabs/shadow-selection-polyfill/issues/9
        '@typescript-eslint/ban-ts-comment': 'off',
        // The following rules is required to match internal google rules
        '@typescript-eslint/restrict-plus-operands': 'error',
        '@typescript-eslint/no-unnecessary-type-assertion': 'error',
        'require-await': 'off',
        '@typescript-eslint/require-await': 'error',
        '@typescript-eslint/no-confusing-void-expression': [
          'error',
          {ignoreArrowShorthand: true},
        ],
        '@typescript-eslint/no-unused-vars': [
          'error',
          {argsIgnorePattern: '^_'},
        ],
        // https://github.com/mysticatea/eslint-plugin-node/blob/master/docs/rules/no-unsupported-features/node-builtins.md
        'node/no-unsupported-features/node-builtins': 'off',
        // Disable no-invalid-this for ts files, because it incorrectly reports
        // errors in some cases (see https://github.com/typescript-eslint/typescript-eslint/issues/491)
        // At the same tigit llme, we are using typescript in a strict mode and
        // it catches almost all errors related to invalid usage of this.
        'no-invalid-this': 'off',

        'node/no-extraneous-import': 'off',

        // Typescript already checks for undef
        'no-undef': 'off',

        'jsdoc/no-types': 2,
      },
      parserOptions: {
        project: path.resolve(__dirname, './tsconfig_eslint.json'),
      },
    },
    {
      files: [
        '*_test.ts',
        'test-utils.ts',
      ],
      rules: {
        '@typescript-eslint/no-explicit-any': 'off',
        '@typescript-eslint/require-await': 'off',
      },
    },
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
        MockInteractions: 'readonly',
        _: 'readonly',
        axs: 'readonly',
        a11ySuite: 'readonly',
        assert: 'readonly',
        expect: 'readonly',
        fixture: 'readonly',
        flush: 'readonly',
        setup: 'readonly',
        sinon: 'readonly',
        stub: 'readonly',
        suite: 'readonly',
        suiteSetup: 'readonly',
        suiteTeardown: 'readonly',
        teardown: 'readonly',
        test: 'readonly',
        fixtureFromElement: 'readonly',
        fixtureFromTemplate: 'readonly',
      },
    },
    {
      files: 'import-href.js',
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
      files: ['test/functional/**/*.js'],
      // Settings for functional tests. These scripts are node scripts.
      // Turn off "no-undef" to allow any global variable
      env: {
        browser: false,
        node: true,
        es6: false,
      },
      rules: {
        'no-undef': 'off',
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
  ],
  plugins: [
    'html',
    'jsdoc',
    'import',
    'lit',
    'prettier',
    'regex',
  ],
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
    },
  },
};
