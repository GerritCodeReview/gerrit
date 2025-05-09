import path from 'path';
import js from '@eslint/js';
import google from 'eslint-config-google';

import * as gts from 'gts';
import jsdoc from 'eslint-plugin-jsdoc';
import importPlugin from 'eslint-plugin-import';
import regex from 'eslint-plugin-regex';
import typescriptEslint from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser'
import lit from 'eslint-plugin-lit';
import {FlatCompat} from '@eslint/eslintrc';
import {fileURLToPath} from 'node:url';
import prettier from 'eslint-plugin-prettier';

//import nodePlugin from 'eslint-plugin-n';

/*import jsdoc from 'eslint-plugin-jsdoc';
import importPlugin from 'eslint-plugin-import';
import lit from 'eslint-plugin-lit';
import prettier from 'eslint-plugin-prettier';
import regex from 'eslint-plugin-regex';
import html from 'eslint-plugin-html';*/


/* Bazel config start */
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
/* Bazel config end */

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
      'node_modules_licenses',
      '!.eslintrc-bazel.js'

    ],
  },
  ...compat.extends(runFilesDir ? `${runFilesDir}/npm/node_modules/gts/` : '../../node_modules/gts/'),
  js.configs.recommended,
  //nodePlugin.configs["flat/recommended-script"],
  //...compat.extends('../../node_modules/gts/.eslintrc.json'),
 google,
  //importPlugin.flatConfigs.recommended,
  //jsdoc.configs['flat/recommended'],
  {
    plugins: {
      import: importPlugin,
      jsdoc: jsdoc,
      prettier: prettier,
      regex: regex
    },
  },
  lit.configs["flat/recommended"],
  //...compat.extends('../../node_modules/gts/'),
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
      semi: ['error', 'always'],
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
    files: ['**/api/*.ts'],
    rules: {
      'regex/invalid': ['error', [{
        regex: 'export interface',
        message: 'All interfaces in the api/ dir must have "declare"',
        replacement: 'export declare interface',
      }]],
    },
  },
  /*...compat.extends(
    runFilesDir ? `${runFilesDir}/npm/node_modules/gts/` : '../../node_modules/gts/',
    "../../node_modules/gts/.eslintrc.json",
    //'plugin:@typescript-eslint/eslint-recommended',
    //'plugin:@typescript-eslint/recommended'
  ).map(config => ({
    ...config,*/
  {
    files: ['**/*.ts'],
    //...typescriptEslint.configs.recommendedTypeChecked,
    //...typescriptEslint.configs.stylisticTypeChecked,
    //...compat.extends('../../node_modules/gts/'),
    //...gts,
    //...gts,
    rules: {
      'no-constant-binary-expression': 'off',
      //...typescriptEslint.configs.recommendedTypeChecked.rules,
      //...gts.rules,
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
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', caughtErrors: 'none' }],
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
        //tsconfigRootDir: __dirname,
        //createDefaultProgram: true,
        //projectService: true,
        //tsconfigRootDir: __dirname,
        //sourceType: 'module',
        project: path.resolve(__dirname, './tsconfig_eslint.json'),
      },
    },
  },
  //})),
  {
    files: ['*.html', 'test.js', 'test-infra.js'],
    rules: {
      'jsdoc/require-file-overview': 'off',
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

