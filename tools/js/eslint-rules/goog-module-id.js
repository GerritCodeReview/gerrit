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

const fs = require('fs');
const path = require('path');
const jsExt = '.js';

class NonJsValidator {
  onProgramEnd(context, node) {
  }
  onGoogDeclareModuleId(context, node) {
    context.report({
      message: 'goog.declareModuleId is allowed only in .js files',
      node: node,
    });
  }
}

class JsOnlyValidator {
  onProgramEnd(context, node) {
  }
  onGoogDeclareModuleId(context, node) {
    context.report({
      message: 'goog.declareModuleId present, but .d.ts file doesn\'t exist. '
        + 'Either remove goog.declareModuleId or add the .d.ts file.',
      node: node,
    });
  }
}

class JsWithDtsValidator {
  constructor() {
    this._googDeclareModuleIdExists = false;
  }
  onProgramEnd(context, node) {
    if(!this._googDeclareModuleIdExists) {
      context.report({
        message: 'goog.declareModuleId(...) is missed. ' +
            'Either add it or remove the associated .d.ts file.',
        node: node,
      })
    }
  }
  onGoogDeclareModuleId(context, node) {
    if(this._googDeclareModuleIdExists) {
      context.report({
        message: 'Duplicated goog.declareModuleId.',
        node: node,
      });
      return;
    }

    const filename = context.getFilename();
    this._googDeclareModuleIdExists = true;

    const scope = context.getScope();
    if(scope.type !== 'global' && scope.type !== 'module') {
      context.report({
        message: 'goog.declareModuleId is allowed only at the root level.',
        node: node,
      });
      // no return - other problems are possible
    }
    if(node.arguments.length !== 1) {
      context.report({
        message: 'goog.declareModuleId must have exactly one parameter.',
        node: node,
      });
      if(node.arguments.length === 0) {
        return;
      }
    }

    const argument = node.arguments[0];
    if(argument.type !== 'Literal') {
      context.report({
        message: 'The argument for the declareModuleId method '
            + 'must be a string literal.',
        node: argument,
      });
      return;
    }
    const pathStart = '/polygerrit-ui/app/';
    const index = filename.lastIndexOf(pathStart);
    if(index < 0) {
      context.report({
        message: 'The file located outside of polygerrit-ui/app directory. ' +
          'Please check eslint config.',
        node: argument,
      });
      return;
    }
    const expectedName = 'polygerrit.' +
        filename.slice(index + pathStart.length, -jsExt.length)
            .replace('/', '.');
    if(argument.value !== expectedName) {
      context.report({
        message: `Invalid module id. It must be '${expectedName}'.`,
        node: argument,
        fix: function(fixer) {
          return fixer.replaceText(argument, `'${expectedName}'`);
        },
      });
    }
  }
}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Check that goog.declareModuleId is valid',
      category: 'TS imports JS errors',
      recommended: false,
    },
    fixable: "code",
    schema: [],
  },
  create: function (context) {
    let fileValidator;
    return {
      Program: function(node) {
        const filename = context.getFilename();
        if(filename.endsWith(jsExt)) {
          const dtsFilename = filename.slice(0, -jsExt.length) + ".d.ts";
          if(fs.existsSync(dtsFilename)) {
            fileValidator = new JsWithDtsValidator();
          } else {
            fileValidator = new JsOnlyValidator();
          }
        }
        else {
          fileValidator = new NonJsValidator();
        }
      },
      "Program:exit": function(node) {
        fileValidator.onProgramEnd(context, node);
        fileValidator = null;
      },
      'ExpressionStatement > CallExpression[callee.property.name="declareModuleId"][callee.object.name="goog"]': function(node) {
        fileValidator.onGoogDeclareModuleId(context, node);
      }
    };
  },
};
