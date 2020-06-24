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

const path = require('path');
const fs = require('fs');

function checkImportValid(context, node) {
  const file = context.getFilename();
  const importSource = node.source.value;

  if(importSource.startsWith('/')) {
    return {
      message: 'Do not use absolute path for import.',
    };
  }
  if(!importSource.startsWith('./') && !importSource.startsWith('../')) {
    // Import from node_modules - nothing to check
    return null;
  }

  const targetFile = path.resolve(path.dirname(file), importSource);
  if(path.extname(targetFile) !== '') {
    return {
      message: 'Do not specify extensions for import path.'
    };
  }

  if(fs.existsSync(targetFile + ".ts")) {
    // .ts file exists - nothing to check
    return null;
  }

  const jsFileExists = fs.existsSync(targetFile + '.js');
  const dtsFileExists = fs.existsSync(targetFile + '.d.ts');

  if(jsFileExists && !dtsFileExists) {
    return {
      message: `The '${importSource}.d.ts' file doesn't exist.`
    };
  }

  if(!jsFileExists && dtsFileExists) {
    return {
      message: `The '${importSource}.js' file doesn't exist.`
    };
  }
  // If both files (.js and .d.ts) don't exist, the error is reported by
  // the typescript compiler. Do not report anything from the rule.
  return null;
}

module.exports = {
  meta: {
    type: "problem",
    docs: {
      description: "Check that TS file can import specific JS file",
      category: "TS imports JS errors",
      recommended: false
    },
    schema: [],
  },
  create: function (context) {
    return {
      Program: function(node) {
        const filename = context.getFilename();
        if(filename.endsWith('.ts') && !filename.endsWith('.d.ts')) {
          return;
        }
        context.report({
          message: 'The rule must be used only with .ts files. ' +
              'Check eslint settings.',
          node: node,
        });
      },
      ImportDeclaration: function (node) {
        const importProblem = checkImportValid(context, node);
        if(importProblem) {
          context.report({
            message: importProblem.message,
            node: node.source,
          });
        }
      }
    };
  }
};
