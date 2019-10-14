import * as ts from "typescript";

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
