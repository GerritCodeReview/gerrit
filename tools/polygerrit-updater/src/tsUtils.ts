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


enum CommentScannerState {
  Text,
  SingleLineComment,
  MultLineComment
}
export function collectAllComments(text: string): string[] {
  const result: string[] = [];
  let state = CommentScannerState.Text;
  let pos = 0;
  function readSingleLineComment() {
    const startPos = pos;
    while(pos < text.length && text[pos] !== '\n') {
      pos++;
    }
    return text.substring(startPos, pos);
  }
  function readMultiLineComment() {
    const startPos = pos;
    while(pos < text.length) {
      if(pos < text.length - 1 && text[pos] === '*' && text[pos + 1] === '/') {
        pos += 2;
        break;
      }
      pos++;
    }
    return text.substring(startPos, pos);
  }

  function skipString(lastChar: string) {
    pos++;
    while(pos < text.length) {
      if(text[pos] === lastChar) {
        pos++;
        return;
      } else if(text[pos] === '\\') {
        pos+=2;
        continue;
      }
      pos++;
    }
  }


  while(pos < text.length - 1) {
    if(text[pos] === '/' && text[pos + 1] === '/') {
      result.push(readSingleLineComment());
    } else if(text[pos] === '/' && text[pos + 1] === '*') {
      result.push(readMultiLineComment());
    } else if(text[pos] === "'") {
      skipString("'");
    } else if(text[pos] === '"') {
      skipString('"');
    } else if(text[pos] === '`') {
      skipString('`');
    } else if(text[pos] == '/') {
      skipString('/');
    } {
      pos++;
    }

  }
  return result;
}