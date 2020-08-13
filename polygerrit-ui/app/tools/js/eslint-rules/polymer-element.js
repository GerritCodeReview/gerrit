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

function isMethod(node, methodName) {
  return node.type == 'MethodDefinition' &&
      node.key &&
      node.key.type === 'Identifier' &&
      node.key.name === methodName;
}

function getDecorator(decorators, decoratorName) {
  if(!decorators) {
    return null;
  }
  return decorators.find(decorator => decorator.expression && decorator.expression.type === 'CallExpression' && decorator.expression.callee.type === 'Identifier' && decorator.expression.callee.name === decoratorName);
}

function getElementTagFromMethodIs(polymerMethodIs) {
  const returnStatement = polymerMethodIs.value.body.body[0];
  return returnStatement.type === 'ReturnStatement' && returnStatement.argument.type === 'Literal' && returnStatement.argument.value
}

function getElementTagFromCustomElementDecorator(decorator) {
  return decorator.expression.arguments.length === 1
      && decorator.expression.arguments[0].type === 'Literal'
      && decorator.expression.arguments[0].value;
}

function findClassMethodByName(classDeclaration, methodName) {
  return classDeclaration.body.body.find(member => isMethod(member, methodName))
}

class TypescriptFileValidator {
  constructor(programNode) {
    this.programNode = programNode;
    this.polymerElements = [];
    this.decoratorImports = new Map();
    this.requiredDecorators = new Set();
    this.lastDecoratorSpecifier = null;
    this.lastImportDeclaration = null;
    this.htmlElementMap = new Map();
  }

  registerDecoratorImport(importSpecifier, importName) {
    this.decoratorImports.set(importName, importSpecifier);
    this.lastDecoratorSpecifier = importSpecifier;
  }

  registerPolymerElementClass(classDeclaration) {
    const className = classDeclaration.id.name;

    const polymerMethodIs = findClassMethodByName(classDeclaration, 'is');
    const polymerPropertiesMethod = findClassMethodByName(classDeclaration, 'properties');
    const polymerObserversMethod = findClassMethodByName(classDeclaration, 'observers');
    const customElementDecorator = getDecorator(classDeclaration.decorators, 'customElement');

    const polymerElement = {
      className: className,
      classDeclaration: classDeclaration,
      methodIs: polymerMethodIs,
      methodProperties: polymerPropertiesMethod,
      customElementDecorator: customElementDecorator,
      elementTag: polymerMethodIs ?
          getElementTagFromMethodIs(polymerMethodIs) :
            customElementDecorator ?
                getElementTagFromCustomElementDecorator(customElementDecorator) : null,
      methodObservers: polymerObserversMethod,
    };
    this.polymerElements.push(polymerElement);
    this.requiredDecorators.add('customElement');
    if(polymerPropertiesMethod) {
      this.requiredDecorators.add('property');
    }
    if(polymerObserversMethod) {
      this.requiredDecorators.add('observe');
    }
    return polymerElement;
  }

  registerHTMLElementTagNameElement(tagName, className) {
    this.htmlElementMap.set(tagName, className);
  }

  onImportDeclaration(node) {
    this.lastImportDeclaration = node;
  }

  onProgramEnd(context) {
    this.fixDecorators(context);
    this.fixHtmlElementMap(context);
  }

  fixDecorators(context) {
    const decoratorsToAdd = [];
    for(const requiredDecorator of this.requiredDecorators) {
      if(!this.decoratorImports.has(requiredDecorator)) {
        decoratorsToAdd.push(requiredDecorator);
      }
    }
    if(decoratorsToAdd.length === 0) {
      return;
    }
    const decoratorsStr = decoratorsToAdd.join(', ');
    context.report({
      message: `Missed decorators import: ${decoratorsStr}`,
      node: this.programNode,
      fix: (fixer) => {
        if(!this.lastDecoratorSpecifier) {
          const importStatementText = `import {${decoratorsStr}} from '@polymer/decorators';`;
          if(this.lastImportDeclaration) {
            return fixer.insertTextAfter(this.lastImportDeclaration, importStatementText);
          }
          else {
            return fixer.insertTextBefore(this.programNode.body[0], importStatementText + '\n');
          }
        } else {
          return fixer.insertTextAfter(this.lastDecoratorSpecifier, `, ${decoratorsStr}`);
        }
      }
    });
  }

  fixHtmlElementMap(context) {
    const elementRegistration = [];
    for(const polymerElement of this.polymerElements) {
      if(!polymerElement.elementTag || this.htmlElementMap.has(polymerElement.elementTag)) {
        continue;
      }
      elementRegistration.push(`'${polymerElement.elementTag}': ${polymerElement.className};`);
    }
    if(elementRegistration.length > 0) {
      context.report({
        message: `File doesn't have correct HTMLElementTagNameMap`,
        node: this.programNode,
        fix: (fixer) => {
          const lastPolymerElement = this.polymerElements[this.polymerElements.length - 1].classDeclaration;
          return fixer. insertTextAfter(lastPolymerElement, `\n\ndeclare global {
  interface HTMLElementTagNameMap {
    ${elementRegistration.join('\n')}
  }
}`);
        }
      })
    }
  }
}

function checkPolymerElement(context, polymerElement) {
  if(polymerElement.methodIs) {
    context.report({
      message: 'Method is() is not allowed. Use @customElement instead',
      node: polymerElement.methodIs,
      fix: function(fixer) {
        const result = [];
        if(!polymerElement.customElementDecorator) {
          result.push(fixer.insertTextBefore(polymerElement.classDeclaration, `@customElement('${polymerElement.elementTag}')`));
        }
        result.push(fixer.remove(polymerElement.methodIs));

        return result;
      },
    });
  }
  if(polymerElement.methodProperties) {
    context.report({
      message: 'Method properties() is not allowed. Use decorators instead',
      node: polymerElement.methodProperties,
      fix: getPropertiesFixer(context, polymerElement.methodProperties),
    });
  }
  if(polymerElement.methodObservers) {
    context.report({
      message: 'Method observers() is not allowed. Use decorators instead',
      node: polymerElement.methodObservers,
      fix: getObserversFixer(context, polymerElement.methodObservers, polymerElement.classDeclaration),
    });
  }
}

function getPropertiesFixer(context, method) {
  const returnStatement = method.value.body.body[0];
  const objectExpression = returnStatement.argument;
  const properties = objectExpression.properties;
  const tsProperties = properties.map(property => {
    if(property.key.type !== 'Identifier') {return null;}
    const name = property.key.name;
    const tsProperty = generateTsProperty(context, name, property.value);
    if(!tsProperty) {return null;}
    return {
      name: name,
      tsProperty: tsProperty
    }
  });
  if(tsProperties.some(item => !item)) {
    return;
  }
  const newText = tsProperties.map(prop => prop.tsProperty).join('\n\n');
  return (fixer) => {
    return fixer.replaceText(method, newText);
  }
}

function getObserversFixer(context, method, classDeclaration) {
  const returnStatement = method.value.body.body[0];
  const arrayExpression = returnStatement.argument;
  const elements = arrayExpression.elements;
  const fixers = [];
  for(const element of elements) {
    const observerString = getStringValue(element);
    if(!observerString) {
      console.error(`Unsupported observer:\n${context.getSource(element)}`);
      return;
    }
    const observerInfo = parseObserver(observerString);
    if(!observerInfo) {
      return;
    }
    const observeHandler = findClassMethodByName(classDeclaration, observerInfo.methodName);
    if(!observeHandler) {
      return;
    }
    const decorator = `@observe(${observerInfo.args.map(arg => `'${arg}'`).join(', ')})\n`;
    fixers.push({observeHandler, decorator});
  }

  return (fixer) => {
    const allFixes = fixers.map(fix => fixer.insertTextBefore(fix.observeHandler, fix.decorator));
    allFixes.push(fixer.remove(method));
    return allFixes;
  }
}

function getStringValue(expression) {
  if(expression.type === 'Literal') {
    return expression.value;
  }
  if(expression.type === 'BinaryExpression') {
    return getBinaryExpressionStringValue(expression);
  }
}

function getBinaryExpressionStringValue(binExpr) {
  if(binExpr.operator !== '+') {
    return;
  }
  const left = getStringValue(binExpr.left);
  const right = getStringValue(binExpr.right);
  return left + right;
}

function parseObserver(observerStr) {
  observerStr = observerStr.trim();
  const argStart = observerStr.indexOf('(');
  const argEnd = observerStr.indexOf(')');
  if(argStart < 0 || argEnd < 0) {
    return;
  }
  const methodName = observerStr.slice(0, argStart);
  const args = observerStr.slice(argStart + 1, argEnd).split(',').map(arg => arg.trim());
  if(args.length === 0) {
    return;
  }
  return {
    methodName: methodName,
    args: args,
  }
}

function generateTsProperty(context, name, propertyValue) {
  if(propertyValue.type === 'Identifier') {
    return `@property({type: ${propertyValue.name}})\n${name}${getTsPropertyTypeFromPolymerTypeAndDefaultValue(propertyValue.name)};`;
  }
  if(propertyValue.type === 'ObjectExpression') {
    let initializer = null;
    let polymerType = null;
    const keyValues = [];
    for(const p of propertyValue.properties) {
      if(p.key.type === 'Identifier' && p.key.name === 'value') {
        if(p.value.type === 'Literal') {
          initializer = context.getSource(p.value);
        } else if(p.value.type === 'FunctionExpression') {
          initializer = getInitializeFromFunctionExpressionBody(context, p.value.body.body, p.value.body);
          if(!initializer) {
            console.error(`Can't fix property '${name}'`);
            return;
          }
        } else if(p.value.type === 'ArrowFunctionExpression') {
          initializer = getInitializeFromArrowFunctionExpressionBody(context,
              p.value.body, p.value);
          if (!initializer) {
            console.error(`Can't fix property '${name}'`);
            return;
          }
        } else {
          console.error(`Can't fix property '${name}, value type is ${p.value.type}'`);
          return;
        }
      } else {
        if(p.key.type === 'Identifier' && p.key.name === 'type') {
          polymerType = context.getSource(p.value);
        }
        keyValues.push(context.getSource(p));
      }
    }
    const decoratorStr = `@property({${keyValues.join(', ')}})`;
    const tsType = getTsPropertyTypeFromPolymerTypeAndDefaultValue(polymerType, initializer);
    const propertyInit = initializer ? `${name}${tsType} = ${initializer}` : `${name}${tsType}`;
    return `${decoratorStr}\n${propertyInit};`;
  }
}

function getInitializeFromBlockStatementBody(context, body) {
  if(body.length === 1 && body[0].type === 'ReturnStatement') {
    return context.getSource(body[0].argument);
  }
}

function getInitializeFromFunctionExpressionBody(context, body, functionExpression) {
  const initilizer = getInitializeFromBlockStatementBody(context, body);
  if(initilizer) {
    return initilizer;
  }
  return `(function()${context.getSource(functionExpression)})()`;
}

function getInitializeFromArrowFunctionExpressionBody(context, body, arrowFunctionExpression) {
  if(body.type === 'MemberExpression' || body.type === 'NewExpression') {
    return context.getSource(body);
  }
  if(body.type === 'BlockStatement') {
    const initializer = getInitializeFromBlockStatementBody(context, body.body);
    if(initializer) {
      return initializer;
    }
  }
  return `(${context.getSource(arrowFunctionExpression)})()`;
}

function getTsPropertyTypeFromPolymerTypeAndDefaultValue(polymerType, initializer) {
  const nullableSuffix = initializer === 'null' ? '| null' : '';
  const optionalPrefix = initializer? '' : '?';
  if (polymerType === 'String') {
    return `${optionalPrefix}: string${nullableSuffix}`;
  }
  if (polymerType === 'Boolean') {
    return `${optionalPrefix}: boolean${nullableSuffix}`;
  }
  if (polymerType === 'Number') {
    return `${optionalPrefix}: number${nullableSuffix}`;
  }

  return `${optionalPrefix}: unknown${nullableSuffix}`;
}

function isExtendPolymerElement(superClass) {
  if(superClass.type === 'CallExpression') {
    return arguments.length === 1 && isExtendPolymerElement(superClass.arguments[0]);
  }
  if(superClass.type === 'Identifier') {

    return superClass.name === 'PolymerElement';
  }
  return false;
}

function isPolymerElement(classDeclaration) {
  if(!classDeclaration.superClass) {
    return false;
  }
  return isExtendPolymerElement(classDeclaration.superClass) &&
    classDeclaration.id.type === 'Identifier';

}

module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Check that polymer element is defined correctly in a ts file',
      category: 'Typescript Polymer',
      recommended: false,
    },
    fixable: "code",
    schema: [],
  },
  create: function (context) {
    let fileValidator;
    return {
      Program: function(node) {
        fileValidator = new TypescriptFileValidator(node);
      },
      'TSModuleDeclaration[id.type="Identifier"][id.name="global"] > TSModuleBlock > TSInterfaceDeclaration[id.type="Identifier"][id.name="HTMLElementTagNameMap"] > TSInterfaceBody > TSPropertySignature[key.type="Literal"][typeAnnotation.type="TSTypeAnnotation"][typeAnnotation.typeAnnotation.type="TSTypeReference"][typeAnnotation.typeAnnotation.typeName.type="Identifier"]': function(node) {
        const tagName = node.key.value;
        const className = node.typeAnnotation.typeAnnotation.typeName.type;
        fileValidator.registerHTMLElementTagNameElement(tagName, className);
      },
      ImportDeclaration: function(node) {
        fileValidator.onImportDeclaration(node);
      },
      'ImportDeclaration[source.type="Literal"][source.value="@polymer/decorators"] > ImportSpecifier[local.type="Identifier"]': function(node) {
        fileValidator.registerDecoratorImport(node, node.local.name);
      },
      'ExpressionStatement > CallExpression[callee.property.name="define"][callee.object.name="customElements"]': function (node) {
        context.report({
          message: 'customElements.define is not allowed in ts files. Use customElements instead',
          node: node,
          fix: function(fixer) {
            return fixer.remove(node);
          }
        })
      },

      'Program:exit': function(node) {
        fileValidator.onProgramEnd(context, node);
        fileValidator = null;
      },
      ClassDeclaration: function(node) {
        if(isPolymerElement(node)) {
          const polymerElement = fileValidator.registerPolymerElementClass(node);
          checkPolymerElement(context, polymerElement);
        }
      }
    };
  },
};
