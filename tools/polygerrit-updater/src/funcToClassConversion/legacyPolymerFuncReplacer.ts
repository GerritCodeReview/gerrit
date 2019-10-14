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
import * as codeUtils from '../utils/codeUtils'
import {LegacyPolymerComponent} from './polymerComponentParser';
import {ClassBasedPolymerElement} from './polymerElementBuilder';

export class LegacyPolymerFuncReplaceResult {
  public constructor(
      private readonly transformationResult: ts.TransformationResult<ts.SourceFile>,
      public readonly leadingComments: string[]) {
  }
  public get file(): ts.SourceFile {
    return this.transformationResult.transformed[0];
  }
  public dispose() {
    this.transformationResult.dispose();
  }

}

export class LegacyPolymerFuncReplacer {
  private readonly callStatement: ts.ExpressionStatement;
  private readonly parentBlock: ts.Block;
  private readonly callStatementIndexInBlock: number;
  public constructor(private readonly legacyComponent: LegacyPolymerComponent) {
    this.callStatement = codeUtils.assertNodeKind(legacyComponent.polymerFuncCallExpr.parent, ts.SyntaxKind.ExpressionStatement);
    this.parentBlock = codeUtils.assertNodeKind(this.callStatement.parent, ts.SyntaxKind.Block);
    this.callStatementIndexInBlock = this.parentBlock.statements.indexOf(this.callStatement);
    if(this.callStatementIndexInBlock < 0) {
      throw new Error("Internal error! Couldn't find statement in its own parent");
    }
  }
  public replace(classBasedElement: ClassBasedPolymerElement): LegacyPolymerFuncReplaceResult {
    const classDeclarationWithComments = this.appendLeadingCommentToClassDeclaration(classBasedElement.classDeclaration);
    return new LegacyPolymerFuncReplaceResult(
        this.replaceLegacyPolymerFunction(classDeclarationWithComments.classDeclarationWithCommentsPlaceholder, classBasedElement.componentRegistration),
        classDeclarationWithComments.leadingComments);
  }
  private appendLeadingCommentToClassDeclaration(classDeclaration: ts.ClassDeclaration): {classDeclarationWithCommentsPlaceholder: ts.ClassDeclaration, leadingComments: string[]} {
    const text = this.callStatement.getFullText();
    let classDeclarationWithCommentsPlaceholder = classDeclaration;
    const leadingComments: string[] = [];
    ts.forEachLeadingCommentRange(text, 0, (pos, end, kind, hasTrailingNewLine) => {
      classDeclarationWithCommentsPlaceholder = codeUtils.addReplacableCommentBeforeNode(classDeclarationWithCommentsPlaceholder, String(leadingComments.length));
      leadingComments.push(text.substring(pos, end));
    });
    return {
      classDeclarationWithCommentsPlaceholder: classDeclarationWithCommentsPlaceholder,
      leadingComments: leadingComments
    }
  }
  private replaceLegacyPolymerFunction(classDeclaration: ts.ClassDeclaration, componentRegistration: ts.ExpressionStatement): ts.TransformationResult<ts.SourceFile> {
    const newStatements = Array.from(this.parentBlock.statements);
    newStatements.splice(this.callStatementIndexInBlock, 1, classDeclaration, componentRegistration);

    const updatedBlock = ts.getMutableClone(this.parentBlock);
    updatedBlock.statements = ts.createNodeArray(newStatements);
    return codeUtils.replaceNode(this.legacyComponent.parsedFile, this.parentBlock, updatedBlock);

  }
}