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

// While we are migrating to typescript, gerrit can have .d.ts files.
// The option "skipLibCheck" is set to true  In the tsconfig.json.
// This is required, because we want to skip type checking in node_modules
// directory - some .d.ts files in 3rd-party modules are incorrect.
// Unfortunately, this options also excludes our own .d.ts files from type
// checking. This rule reports all .ts errors in a file as tslint errors.

function getMassageTextFromChain(chainNode, prefix) {
  let nestedMessages = prefix + chainNode.messageText;
  if (chainNode.next && chainNode.next.length > 0) {
    nestedMessages += "\n";
    for (const node of chainNode.next) {
      nestedMessages +=
          getMassageTextFromChain(node, prefix + " ");
      if(!nestedMessages.endsWith('\n')) {
        nestedMessages += "\n";
      }
    }
  }
  return nestedMessages;
}

function getMessageText(diagnostic) {
  if (typeof diagnostic.messageText === 'string') {
    return diagnostic.messageText;
  }
  return getMassageTextFromChain(diagnostic.messageText, "");
}

function getDiagnosticStartAndEnd(diagnostic) {
  if(diagnostic.start) {
    const file = diagnostic.file;
    const start = file.getLineAndCharacterOfPosition(diagnostic.start);
    const length = diagnostic.length ? diagnostic.length : 0;
    return {
      start,
      end: file.getLineAndCharacterOfPosition(diagnostic.start + length),
    };
  }
  return {
    start: {line:0, character: 0},
    end: {line:0, character: 0},
  }
}

module.exports = {
  meta: {
    type: "problem",
    docs: {
      description: "Reports all typescript problems as linter problems",
      category: ".d.ts",
      recommended: false
    },
    schema: [],
  },
  create: function (context) {
    const program = context.parserServices.program;
    return {
      Program: function(node) {
        const sourceFile =
            context.parserServices.esTreeNodeToTSNodeMap.get(node);
        const allDiagnostics = [
            ...program.getDeclarationDiagnostics(sourceFile),
            ...program.getSemanticDiagnostics(sourceFile)];
        for(const diagnostic of allDiagnostics) {
          const {start, end } = getDiagnosticStartAndEnd(diagnostic);
          context.report({
            message: getMessageText(diagnostic),
            loc: {
              start: {
                line: start.line + 1,
                column: start.character,
              },
              end: {
                line: end.line + 1,
                column: end.character,
              }
            }
          });
        }
      },
    };
  }
};
