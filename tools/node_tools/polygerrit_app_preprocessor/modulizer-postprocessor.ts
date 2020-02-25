// Copyright (C) 2020 The Android Open Source Project
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

import {Project, ts} from "ts-morph";
import {fail} from "../utils/common";
import * as path from "path";
import {readMultilineParamFile} from "../utils/command-line";

const root = process.argv[2];
const files = readMultilineParamFile(process.argv[3]);

const modulizerOutRoot = path.join(root, "modulizer_out");

interface NodeTransformer {
  transformNode(node: ts.Node, relativePathToSourceFile: string): ts.Node | undefined;
}

class ImportHrefDeclarationTransformer implements NodeTransformer{
  // Transforms
  // import { importHref as importHref$0 } from "...";
  // to
  // import { importHref } from "../relative_path/scripts/import_href.js";

  private static readonly importHrefPath = path.join(root, "scripts/import-href.js");

  private isSupportedImportHrefDeclaration(node: ts.ImportDeclaration): boolean {
    // Returns true if import has exactly the following form:
    // import { importHref as importHref$0 } from "....";
    // (path is not important)

    if(!node.importClause) {
      return false;
    }
    if(!node.importClause.namedBindings|| !ts.isNamedImports(node.importClause.namedBindings)) {
      return false;
    }
    const namedImports = node.importClause.namedBindings;
    if (namedImports.elements.length !== 1) {
      return false;
    }
    if(namedImports.elements.length !== 1) {
      return false;
    }
    const firstNamedImport = namedImports.elements[0];
    if(!firstNamedImport.propertyName) {
      return false;
    }
    return firstNamedImport.name.text === "importHref$0" && firstNamedImport.propertyName.text === "importHref";
  }

  public transformNode(node: ts.Node, relativePathToSourceFile: string): ts.ImportDeclaration | undefined {
    if(!ts.isImportDeclaration(node)) {
      return undefined;
    }
    if (!ts.isStringLiteral(node.moduleSpecifier)) {
      fail(`Internal error`);
    }

    const moduleSpecifierText = node.moduleSpecifier.text;
    console.log(moduleSpecifierText);
    if(moduleSpecifierText !== "@polymer/polymer/lib/utils/import-href.js") {
      return undefined;
    }

    if(!this.isSupportedImportHrefDeclaration(node)) {
      fail(`Unsupported import. Expected:
  import { importHref as importHref$0 } from "...";
Actual:
  ${node.getText()}`);
    }

    const relativePath = path.relative(path.dirname(path.join(root, relativePathToSourceFile)), ImportHrefDeclarationTransformer.importHrefPath);
    const namedBindings = ts.createNamedImports([ts.createImportSpecifier(undefined, ts.createIdentifier("importHref"))]);
    return ts.updateImportDeclaration(node, node.decorators, node.modifiers, ts.createImportClause(undefined, namedBindings), ts.createStringLiteral(relativePath));
  }
}

class ImportHrefCallExpressionTransformer implements NodeTransformer {
  // Transforms
  // (this.importHref || importHref$0)(...arguments..)
  // to
  // this.importHref(...arguments...)

  private isImportHrefCallExpression(node: ts.CallExpression): boolean {
    // Return true if call has the form:
    // (this.importHref || importHref$0)(.....)
    // (arguments are not important)

    if(!ts.isParenthesizedExpression(node.expression)) {
      return false;
    }
    if(!ts.isBinaryExpression(node.expression.expression)) {
      return false;
    }
    const binareExprNode = node.expression.expression;

    if(binareExprNode.operatorToken.kind !== ts.SyntaxKind.BarBarToken) {
      return false;
    }

    if(!ts.isPropertyAccessExpression(binareExprNode.left) ||
        !ts.isIdentifier(binareExprNode.right)) {
      return false;
    }
    if(binareExprNode.left.getText() !== "this.importHref") {
      return false;
    }
    if(binareExprNode.right.getText() !== "importHref$0") {
      return false;
    }
    return true;
  }

  public transformNode(node: ts.Node): ts.CallExpression | undefined {
    if(!ts.isCallExpression(node) || !this.isImportHrefCallExpression(node)) {
      return undefined;
    }
    return ts.updateCall(node, ts.createIdentifier("importHref"), node.typeArguments, node.arguments);
  }
}

class ImportPolymerLegacyDeclarationTransformer implements NodeTransformer {
  // Transforms
  // import '/node_modules/@polymer/polymer/polymer-legacy.js';
  // to
  // import '"../relative_path/scripts/bundled-polymer.js"'

  private static readonly bundledPolymerPath = path.join(root, "scripts/bundled-polymer.js");

  public transformNode(node: ts.Node, relativePathToSourceFile: string): ts.ImportDeclaration | undefined {
    if(!ts.isImportDeclaration(node)) {
      return undefined;
    }

    if(node.getText() !== "import '/node_modules/@polymer/polymer/polymer-legacy.js';") {
      return undefined;
    }
    const relativePath = path.relative(path.dirname(path.join(root, relativePathToSourceFile)), ImportPolymerLegacyDeclarationTransformer.bundledPolymerPath);
    return ts.updateImportDeclaration(node, node.decorators, node.modifiers, node.importClause, ts.createStringLiteral(relativePath));
  }
}

class TsFileUpdater {
  private static readonly Transformers = [
      new ImportHrefCallExpressionTransformer(),
      new ImportHrefDeclarationTransformer(),
      new ImportHrefCallExpressionTransformer()
  ];
  public updateFile(relativePath: string) {
    const project = new Project();
    const sourceFile = project.addSourceFileAtPath(path.join(modulizerOutRoot, relativePath));

    const updatedSourceFile = sourceFile.transform(traversal => {
      const node = traversal.visitChildren();
      for (const transformer of TsFileUpdater.Transformers) {
        const transformedNode = transformer.transformNode(node, relativePath);
        if (transformedNode) {
          return transformedNode;
        }
      }
      return node;
    });

    updatedSourceFile.saveSync();
  }
}

const tsFileUpdater = new TsFileUpdater();
for(const file of files) {
  console.log(`Updating ${file}`);
  tsFileUpdater.updateFile(file);
}