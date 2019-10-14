// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import * as ts from 'typescript';

export function assertNodeKind<T extends U, U extends ts.Node>(node: U, expectedKind: ts.SyntaxKind): T {
  if (node.kind !== expectedKind) {
    throw new Error(`Invlid node kind. Expected: ${ts.SyntaxKind[expectedKind]}, actual: ${ts.SyntaxKind[node.kind]}`);
  }
  return node as T;
}

export function assertNodeKindOrUndefined<T extends U, U extends ts.Node>(node: U | undefined, expectedKind: ts.SyntaxKind): T | undefined {
  if (!node) {
    return undefined;
  }
  return assertNodeKind<T, U>(node, expectedKind);
}

export function getPropertyAssignment(expression?: ts.ObjectLiteralElementLike): ts.PropertyAssignment | undefined {
  return assertNodeKindOrUndefined(expression, ts.SyntaxKind.PropertyAssignment);
}

export function getStringLiteralValue(expression?: ts.Expression): string | undefined {
  const literal: ts.StringLiteral | undefined = assertNodeKindOrUndefined(expression, ts.SyntaxKind.StringLiteral);
  return literal ? literal.text : undefined;
}

export function getBooleanLiteralValue(expression?: ts.Expression): boolean | undefined {
  if (!expression) {
    return undefined;
  }
  if (expression.kind === ts.SyntaxKind.TrueKeyword) {
    return true;
  }
  if (expression.kind === ts.SyntaxKind.FalseKeyword) {
    return false;
  }
  throw new Error(`Invalid expression kind - ${expression.kind}`);
}

export function getObjectLiteralExpression(expression?: ts.Expression): ts.ObjectLiteralExpression | undefined {
  return assertNodeKindOrUndefined(expression, ts.SyntaxKind.ObjectLiteralExpression);
}

export function getArrayLiteralExpression(expression?: ts.Expression): ts.ArrayLiteralExpression | undefined {
  return assertNodeKindOrUndefined(expression, ts.SyntaxKind.ArrayLiteralExpression);
}

export function replaceNode(file: ts.SourceFile, originalNode: ts.Node, newNode: ts.Node): ts.TransformationResult<ts.SourceFile> {
  const nodeReplacerTransformer: ts.TransformerFactory<ts.SourceFile> = (context: ts.TransformationContext) => {
    const visitor: ts.Visitor = (node) => {
      if(node === originalNode) {
        return newNode;
      }
      return ts.visitEachChild(node, visitor, context);
    };


    return source => ts.visitNode(source, visitor);
  };
  return ts.transform(file, [nodeReplacerTransformer]);
}

export type NameExpression = ts.Identifier | ts.ThisExpression | ts.PropertyAccessExpression;
export function createNameExpression(fullPath: string): NameExpression {
  const parts = fullPath.split(".");
  let result: NameExpression = parts[0] === "this" ? ts.createThis() : ts.createIdentifier(parts[0]);
  for(let i = 1; i < parts.length; i++) {
    result = ts.createPropertyAccess(result, parts[i]);
  }
  return result;
}

const generatedCommentText = "-Generated code - 9cb292bc-5d88-4c5e-88f4-49535c93beb9 -";
const generatedCommentRegExp = new RegExp("//" + generatedCommentText, 'g');
const replacableCommentText = "- Replacepoint - 9cb292bc-5d88-4c5e-88f4-49535c93beb9 -";

export function addNewLineAfterNode<T extends ts.Node>(node: T): T {
  const comment = ts.getSyntheticTrailingComments(node);
  if(comment && comment.some(c => c.text === generatedCommentText)) {
    return node;
  }
  return ts.addSyntheticTrailingComment(node, ts.SyntaxKind.SingleLineCommentTrivia, generatedCommentText, true);
}

export function applyNewLines(text: string): string {
  return text.replace(generatedCommentRegExp, "");

}
export function addReplacableCommentAfterNode<T extends ts.Node>(node: T, name: string): T {
  return ts.addSyntheticTrailingComment(node, ts.SyntaxKind.SingleLineCommentTrivia, replacableCommentText + name, true);
}

export function addReplacableCommentBeforeNode<T extends ts.Node>(node: T, name: string): T {
  return ts.addSyntheticLeadingComment(node, ts.SyntaxKind.SingleLineCommentTrivia, replacableCommentText + name, true);
}

export function replaceComment(text: string, commentName: string, newContent: string): string {
  return text.replace("//" + replacableCommentText + commentName, newContent);
}

export function createMethod(name: string, methodDecl: ts.MethodDeclaration | undefined, codeAtStart: ts.Statement[], codeAtEnd: ts.Statement[], callSuperMethod: boolean): ts.MethodDeclaration | undefined {
  if(!methodDecl && (codeAtEnd.length > 0 || codeAtEnd.length > 0)) {
    methodDecl = ts.createMethod([], [], undefined, name, undefined, [], [],undefined, ts.createBlock([]));
  }
  if(!methodDecl) {
    return;
  }
  if (!methodDecl.body) {
    throw new Error("Method must have a body");
  }
  if(methodDecl.parameters.length > 0) {
    throw new Error("Methods with parameters are not supported");
  }
  let newStatements = [...codeAtStart];
  if(callSuperMethod) {
    const superCall: ts.CallExpression = ts.createCall(ts.createPropertyAccess(ts.createSuper(), assertNodeKind(methodDecl.name, ts.SyntaxKind.Identifier) as ts.Identifier), [], []);
    const superCallExpression = ts.createExpressionStatement(superCall);
    newStatements.push(superCallExpression);
  }
  newStatements.push(...codeAtEnd);
  const newBody = ts.getMutableClone(methodDecl.body);

  newStatements = newStatements.map(m => addNewLineAfterNode(m));
  newStatements.splice(codeAtStart.length + 1, 0, ...newBody.statements);

  newBody.statements = ts.createNodeArray(newStatements);

  const newMethod = ts.getMutableClone(methodDecl);
  newMethod.body = newBody;

  return newMethod;
}