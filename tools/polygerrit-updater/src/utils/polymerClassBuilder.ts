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
import * as codeUtils from './codeUtils';
import {LegacyLifecycleMethodName, LegacyLifecycleMethodsArray} from '../funcToClassConversion/polymerComponentParser';
import {SyntaxKind} from 'typescript';

enum PolymerClassMemberType {
  IsAccessor,
  Constructor,
  PolymerPropertiesAccessor,
  PolymerObserversAccessor,
  Method,
  ExistingLifecycleMethod,
  NewLifecycleMethod,
  GetAccessor,
}

type PolymerClassMember = PolymerClassIsAccessor | PolymerClassConstructor | PolymerClassExistingLifecycleMethod | PolymerClassNewLifecycleMethod | PolymerClassSimpleMember;

interface PolymerClassExistingLifecycleMethod {
  member: ts.MethodDeclaration;
  memberType: PolymerClassMemberType.ExistingLifecycleMethod;
  name: string;
  lifecycleOrder: number;
  originalPos: number;
}

interface PolymerClassNewLifecycleMethod {
  member: ts.MethodDeclaration;
  memberType: PolymerClassMemberType.NewLifecycleMethod;
  name: string;
  lifecycleOrder: number;
  originalPos: -1
}

interface PolymerClassIsAccessor {
  member: ts.GetAccessorDeclaration;
  memberType: PolymerClassMemberType.IsAccessor;
  originalPos: -1
}

interface PolymerClassConstructor {
  member: ts.ConstructorDeclaration;
  memberType: PolymerClassMemberType.Constructor;
  originalPos: -1
}

interface PolymerClassSimpleMember {
  memberType: PolymerClassMemberType.PolymerPropertiesAccessor | PolymerClassMemberType.PolymerObserversAccessor | PolymerClassMemberType.Method | PolymerClassMemberType.GetAccessor;
  member: ts.ClassElement;
  originalPos: number;
}

export interface PolymerClassBuilderResult {
  classDeclaration: ts.ClassDeclaration;
  generatedComments: string[];
}

export class PolymerClassBuilder {
  private readonly members: PolymerClassMember[] = [];
  public readonly constructorStatements: ts.Statement[] = [];
  private baseType: ts.ExpressionWithTypeArguments | undefined;
  private classJsDocComments: string[];

  public constructor(public readonly className: string) {
    this.classJsDocComments = [];
  }

  public addIsAccessor(accessor: ts.GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.IsAccessor,
      originalPos: -1
    });
  }

  public addPolymerPropertiesAccessor(originalPos: number, accessor: ts.GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.PolymerPropertiesAccessor,
      originalPos: originalPos
    });
  }

  public addPolymerObserversAccessor(originalPos: number, accessor: ts.GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.PolymerObserversAccessor,
      originalPos: originalPos
    });
  }


  public addClassFieldInitializer(name: string | ts.Identifier, initializer: ts.Expression) {
    const assignment = ts.createAssignment(ts.createPropertyAccess(ts.createThis(), name), initializer);
    this.constructorStatements.push(codeUtils.addNewLineAfterNode(ts.createExpressionStatement(assignment)));
  }
  public addMethod(originalPos: number, method: ts.MethodDeclaration) {
    this.members.push({
      member: method,
      memberType: PolymerClassMemberType.Method,
      originalPos: originalPos
    });
  }

  public addGetAccessor(originalPos: number, accessor: ts.GetAccessorDeclaration) {
    this.members.push({
      member: accessor,
      memberType: PolymerClassMemberType.GetAccessor,
      originalPos: originalPos
    });
  }

  public addLifecycleMethod(name: LegacyLifecycleMethodName, originalPos: number, method: ts.MethodDeclaration) {
    const lifecycleOrder = LegacyLifecycleMethodsArray.indexOf(name);
    if(lifecycleOrder < 0) {
      throw new Error(`Invalid lifecycle name`);
    }
    if(originalPos >= 0) {
      this.members.push({
        member: method,
        memberType: PolymerClassMemberType.ExistingLifecycleMethod,
        originalPos: originalPos,
        name: name,
        lifecycleOrder: lifecycleOrder
      })
    } else {
      this.members.push({
        member: method,
        memberType: PolymerClassMemberType.NewLifecycleMethod,
        name: name,
        lifecycleOrder: lifecycleOrder,
        originalPos: -1
      })
    }
  }

  public setBaseType(type: ts.ExpressionWithTypeArguments) {
    if(this.baseType) {
      throw new Error("Class can have only one base type");
    }
    this.baseType = type;
  }

  public build(): PolymerClassBuilderResult {
    let heritageClauses: ts.HeritageClause[] = [];
    if (this.baseType) {
      const extendClause = ts.createHeritageClause(ts.SyntaxKind.ExtendsKeyword, [this.baseType]);
      heritageClauses.push(extendClause);
    }
    const finalMembers: PolymerClassMember[] = [];
    const isAccessors = this.members.filter(member => member.memberType === PolymerClassMemberType.IsAccessor);
    if(isAccessors.length !== 1) {
      throw new Error("Class must have exactly one 'is'");
    }
    finalMembers.push(isAccessors[0]);
    const constructorMember = this.createConstructor();
    if(constructorMember) {
      finalMembers.push(constructorMember);
    }

    const newLifecycleMethods: PolymerClassNewLifecycleMethod[] = [];
    this.members.forEach(member => {
      if(member.memberType === PolymerClassMemberType.NewLifecycleMethod) {
        newLifecycleMethods.push(member);
      }
    });

    const methodsWithKnownPosition = this.members.filter(member => member.originalPos >= 0);
    methodsWithKnownPosition.sort((a, b) => a.originalPos - b.originalPos);

    finalMembers.push(...methodsWithKnownPosition);


    for(const newLifecycleMethod of newLifecycleMethods) {
      //Number of methods is small - use brute force solution
      let closestNextIndex = -1;
      let closestNextOrderDiff: number = LegacyLifecycleMethodsArray.length;
      let closestPrevIndex = -1;
      let closestPrevOrderDiff: number = LegacyLifecycleMethodsArray.length;
      for (let i = 0; i < finalMembers.length; i++) {
        const member = finalMembers[i];
        if (member.memberType !== PolymerClassMemberType.NewLifecycleMethod && member.memberType !== PolymerClassMemberType.ExistingLifecycleMethod) {
          continue;
        }
        const orderDiff = member.lifecycleOrder - newLifecycleMethod.lifecycleOrder;
        if (orderDiff > 0) {
          if (orderDiff < closestNextOrderDiff) {
            closestNextIndex = i;
            closestNextOrderDiff = orderDiff;
          }
        } else if (orderDiff < 0) {
          if (orderDiff < closestPrevOrderDiff) {
            closestPrevIndex = i;
            closestPrevOrderDiff = orderDiff;
          }
        }
      }
      let insertIndex;
      if (closestNextIndex !== -1 || closestPrevIndex !== -1) {
        insertIndex = closestNextOrderDiff < closestPrevOrderDiff ?
            closestNextIndex : closestPrevIndex + 1;
      } else {
        insertIndex = Math.max(
            finalMembers.findIndex(m => m.memberType === PolymerClassMemberType.Constructor),
            finalMembers.findIndex(m => m.memberType === PolymerClassMemberType.IsAccessor),
            finalMembers.findIndex(m => m.memberType === PolymerClassMemberType.PolymerPropertiesAccessor),
            finalMembers.findIndex(m => m.memberType === PolymerClassMemberType.PolymerObserversAccessor),
        );
        if(insertIndex < 0) {
          insertIndex = finalMembers.length;
        } else {
          insertIndex++;//Insert after
        }
      }
      finalMembers.splice(insertIndex, 0, newLifecycleMethod);
    }
    //Asserts about finalMembers
    const nonConstructorMembers = finalMembers.filter(m => m.memberType !== PolymerClassMemberType.Constructor);

    if(nonConstructorMembers.length !== this.members.length) {
      throw new Error(`Internal error! Some methods are missed`);
    }
    let classDeclaration = ts.createClassDeclaration(undefined, undefined, this.className, undefined, heritageClauses, finalMembers.map(m => m.member))
    const generatedComments: string[] = [];
    if(this.classJsDocComments.length > 0) {
      const commentContent = '*\n' + this.classJsDocComments.map(line => `* ${line}`).join('\n') + '\n';
      classDeclaration = ts.addSyntheticLeadingComment(classDeclaration, ts.SyntaxKind.MultiLineCommentTrivia, commentContent, true);
      generatedComments.push(`/*${commentContent}*/`);
    }
    return {
      classDeclaration,
      generatedComments,
    };

  }

  private createConstructor(): PolymerClassConstructor | null {
    if(this.constructorStatements.length === 0) {
      return null;
    }
    let superCall: ts.CallExpression = ts.createCall(ts.createSuper(), [], []);
    const superCallExpression = ts.createExpressionStatement(superCall);
    const statements = [superCallExpression, ...this.constructorStatements];
    const constructorDeclaration = ts.createConstructor([], [], [], ts.createBlock(statements, true));

    return {
      memberType: PolymerClassMemberType.Constructor,
      member: constructorDeclaration,
      originalPos: -1
    };
  }

  public addClassJSDocComments(lines: string[]) {
    this.classJsDocComments.push(...lines);
  }
}