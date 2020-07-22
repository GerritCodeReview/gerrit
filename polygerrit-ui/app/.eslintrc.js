/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Do not add any bazel-specific properties in this file to keep it clean.
// Please add such properties to the .eslintrc-bazel.js file
const path = require('path');

module.exports = {
  "extends": ["eslint:recommended", "google"],
  "parserOptions": {
    "ecmaVersion": 9,
    "sourceType": "module"
  },
  "env": {
    "browser": true,
    "es6": true
  },
  "rules": {
    "no-confusing-arrow": "error",
    "newline-per-chained-call": ["error", {"ignoreChainWithDepth": 2}],
    "arrow-body-style": ["error", "as-needed",
      {"requireReturnForObjectLiteral": true}],
    "arrow-parens": ["error", "as-needed"],
    "block-spacing": ["error", "always"],
    "brace-style": ["error", "1tbs", {"allowSingleLine": true}],
    "camelcase": "off",
    "comma-dangle": ["error", {
      "arrays": "always-multiline",
      "objects": "always-multiline",
      "imports": "always-multiline",
      "exports": "always-multiline",
      "functions": "never"
    }],
    "eol-last": "off",
    "indent": ["error", 2, {
      "MemberExpression": 2,
      "FunctionDeclaration": {"body": 1, "parameters": 2},
      "FunctionExpression": {"body": 1, "parameters": 2},
      "CallExpression": {"arguments": 2},
      "ArrayExpression": 1,
      "ObjectExpression": 1,
      "SwitchCase": 1
    }],
    "keyword-spacing": ["error", {"after": true, "before": true}],
    "lines-between-class-members": ["error", "always"],
    "max-len": [
      "error",
      80,
      2,
      {
        "ignoreComments": true,
        "ignorePattern": "^import .*;$"
      }
    ],
    "new-cap": ["error", {
      "capIsNewExceptions": ["Polymer", "GestureEventListeners"],
      "capIsNewExceptionPattern": "^.*Mixin$"
    }],
    "no-console": ["error", { allow: ["warn", "error", "info", "assert", "group", "groupEnd"] }],
    "no-multiple-empty-lines": ["error", {"max": 1}],
    "no-prototype-builtins": "off",
    "no-redeclare": "off",
    "no-restricted-syntax": [
      "error",
      {
        "selector": "ExpressionStatement > CallExpression > MemberExpression[object.name='test'][property.name='only']",
        "message": "Remove test.only."
      },
      {
        "selector": "ExpressionStatement > CallExpression > MemberExpression[object.name='suite'][property.name='only']",
        "message": "Remove suite.only."
      }
    ],
    // no-undef disables global variable.
    // "globals" declares allowed global variables.
    "no-undef": ["error"],
    "no-useless-escape": "off",
    "no-var": "error",
    "operator-linebreak": "off",
    "object-shorthand": ["error", "always"],
    "padding-line-between-statements": [
      "error",
      {
        "blankLine": "always",
        "prev": "class",
        "next": "*"
      },
      {
        "blankLine": "always",
        "prev": "*",
        "next": "class"
      }
    ],
    "prefer-arrow-callback": "error",
    "prefer-const": "error",
    "prefer-promise-reject-errors": "error",
    "prefer-spread": "error",
    "prefer-object-spread": "error",
    "quote-props": ["error", "consistent-as-needed"],
    "semi": ["error", "always"],
    "template-curly-spacing": "error",

    "require-jsdoc": 0,
    "valid-jsdoc": 0,
    "jsdoc/check-alignment": 2,
    "jsdoc/check-examples": 0,
    "jsdoc/check-indentation": 0,
    "jsdoc/check-param-names": 0,
    "jsdoc/check-syntax": 0,
    "jsdoc/check-tag-names": 0,
    "jsdoc/check-types": 0,
    "jsdoc/implements-on-classes": 2,
    "jsdoc/match-description": 0,
    "jsdoc/newline-after-description": 2,
    "jsdoc/no-types": 0,
    "jsdoc/no-undefined-types": 0,
    "jsdoc/require-description": 0,
    "jsdoc/require-description-complete-sentence": 0,
    "jsdoc/require-example": 0,
    "jsdoc/require-hyphen-before-param-description": 0,
    "jsdoc/require-jsdoc": 0,
    "jsdoc/require-param": 0,
    "jsdoc/require-param-description": 0,
    "jsdoc/require-param-name": 2,
    "jsdoc/require-param-type": 2,
    "jsdoc/require-returns": 0,
    "jsdoc/require-returns-check": 0,
    "jsdoc/require-returns-description": 0,
    "jsdoc/require-returns-type": 2,
    "jsdoc/valid-types": 2,
    "jsdoc/require-file-overview": ["error", {
      "tags": {
        "license": {
          "mustExist": true,
          "preventDuplicates": true
        }
      }
    }],
    "import/no-self-import": 2,
    // The no-cycle rule is slow, because it doesn't cache dependencies.
    // Disable it.
    "import/no-cycle": 0,
    "import/no-useless-path-segments": 2,
    "import/no-unused-modules": 2,
    "import/no-default-export": 2,
    // Custom rule from the //tools/js/eslint-rules directory.
    // See //tools/js/eslint-rules/README.md for details
    "goog-module-id": 2,
  },

  // List of allowed globals in all files
  "globals": {
    // Polygerrit global variables.
    // You must not add anything new in this list!
    // Instead export variables from modules
    // TODO(dmfilippov): Remove global variables from polygerrit
    // Global variables from 3rd party libraries.
    // You should not add anything in this list, always try to import
    // If import is not possible - you can extend this list
    "ShadyCSS": "readonly",
    "linkify": "readonly",
    "security": "readonly",
  },
  "overrides": [
    {
      // .js-only rules
      "files": ["**/*.js"],
      "rules": {
        // The rule is required for .js files only, because typescript compiler
        // always checks import.
        "import/no-unresolved": 2,
        "import/named": 2,
      },
      "globals": {
        "goog": "readonly",
      }
    },
    {
      "files": ["**/*.ts"],
      "extends": [require.resolve("gts/.eslintrc.json")],
      "rules": {
        // The following rules is required to match internal google rules
        "@typescript-eslint/restrict-plus-operands": "error",
      },
      "parserOptions": {
        "project": path.resolve(__dirname, "./tsconfig_eslint.json"),
      }
    },
    {
      "files": ["**/*.ts"],
      "excludedFiles": "*.d.ts",
      "rules": {
        // Custom rule from the //tools/js/eslint-rules directory.
        // See //tools/js/eslint-rules/README.md for details
        "ts-imports-js": 2,
      }
    },
    {
      "files": ["**/*.d.ts"],
      "rules": {
        // See details in the //tools/js/eslint-rules/report-ts-error.js file.
        "report-ts-error": "error",
      }
    },
    {
      "files": ["*.html", "test.js", "test-infra.js"],
      "rules": {
        "jsdoc/require-file-overview": "off"
      },
    },
    {
      "files": [
        "*.html",
        "common-test-setup.js",
        "common-test-setup-karma.js",
        "*_test.js",
        "a11y-test-utils.js",
      ],
      // Additional global variables allowed in tests
      "globals": {
        // Global variables from 3rd party test libraries/frameworks.
        // You can extend this list if you want to use other global
        // variables from these libraries and import is not possible
        "MockInteractions": "readonly",
        "_": "readonly",
        "axs": "readonly",
        "a11ySuite": "readonly",
        "assert": "readonly",
        "expect": "readonly",
        "fixture": "readonly",
        "flush": "readonly",
        "flushAsynchronousOperations": "readonly",
        "setup": "readonly",
        "sinon": "readonly",
        "stub": "readonly",
        "suite": "readonly",
        "suiteSetup": "readonly",
        "suiteTeardown": "readonly",
        "teardown": "readonly",
        "test": "readonly",
        "fixtureFromElement": "readonly",
        "fixtureFromTemplate": "readonly",
      }
    },
    {
      "files": "import-href.js",
      "globals": {
        "HTMLImports": "readonly",
      }
    },
    {
      "files": ["samples/**/*.js"],
      "globals": {
        // Settings for samples. You can add globals here if you want to use it
        "Gerrit": "readonly",
        "Polymer": "readonly",
      }
    },
    {
      "files": ["test/functional/**/*.js"],
      // Settings for functional tests. These scripts are node scripts.
      // Turn off "no-undef" to allow any global variable
      "env": {
        "browser": false,
        "node": true,
        "es6": false
      },
      "rules": {
        "no-undef": "off",
      }
    },
    {
      "files": ["*_html.js", "gr-icons.js", "*-theme.js", "*-styles.js"],
      "rules": {
        "max-len": "off"
      }
    },
    {
      "files": ["*_html.js"],
      "rules": {
        "prettier/prettier": ["error", {
          "bracketSpacing": false,
          "singleQuote": true,
        }]
      }
    }
  ],
  "plugins": [
    "html",
    "jsdoc",
    "import",
    "prettier"
  ],
  "settings": {
    "html/report-bad-indent": "error",
    "import/resolver": {
      "node": {},
      [path.resolve(__dirname, './.eslint-ts-resolver.js')]: {},
    },
  },
};
