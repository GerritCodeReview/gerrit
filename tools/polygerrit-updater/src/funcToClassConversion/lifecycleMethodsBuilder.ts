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
import * as codeUtils from '../utils/codeUtils';
import {LegacyLifecycleMethodName, OrdinaryMethods} from './polymerComponentParser';

interface LegacyLifecycleMethodContent {
  codeAtMethodStart: ts.Statement[];
  existingMethod?: ts.MethodDeclaration;
  codeAtMethodEnd: ts.Statement[];
}

export interface LifecycleMethod {
  originalPos: number;//-1 - no original method exists
  method: ts.MethodDeclaration;
  name: LegacyLifecycleMethodName;
}

export class LifecycleMethodsBuilder {
  private readonly methods: Map<LegacyLifecycleMethodName, LegacyLifecycleMethodContent> = new Map();

  private getMethodContent(name: LegacyLifecycleMethodName): LegacyLifecycleMethodContent {
    if(!this.methods.has(name)) {
      this.methods.set(name, {
        codeAtMethodStart: [],
        codeAtMethodEnd: []
      });
    }
    return this.methods.get(name)!;
  }

  public addListeners(legacyListeners: ts.ObjectLiteralExpression, legacyOrdinaryMethods: OrdinaryMethods) {
    for(const listener of legacyListeners.properties) {
      const propertyAssignment = codeUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment) as ts.PropertyAssignment;
      if(!propertyAssignment.name) {
        throw new Error("Listener must have event name");
      }
      let eventNameLiteral: ts.StringLiteral;
      let commentsToRestore: string[] = [];
      if(propertyAssignment.name.kind === ts.SyntaxKind.StringLiteral) {
        //We don't loose comment in this case, because we keep literal as is
        eventNameLiteral = propertyAssignment.name;
      } else if(propertyAssignment.name.kind === ts.SyntaxKind.Identifier) {
        eventNameLiteral = ts.createStringLiteral(propertyAssignment.name.text);
        commentsToRestore = codeUtils.getLeadingComments(propertyAssignment);
      } else {
        throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.name.kind]}`);
      }

      const handlerLiteral = codeUtils.assertNodeKind(propertyAssignment.initializer, ts.SyntaxKind.StringLiteral) as ts.StringLiteral;
      const handlerImpl = legacyOrdinaryMethods.get(handlerLiteral.text);
      if(!handlerImpl) {
        throw new Error(`Can't find event handler '${handlerLiteral.text}'`);
      }
      const eventHandlerAccess = ts.createPropertyAccess(ts.createThis(), handlerLiteral.text);
      //ts.forEachChild(handler)
      const args: ts.Identifier[] = handlerImpl.parameters.map((arg) => codeUtils.assertNodeKind(arg.name, ts.SyntaxKind.Identifier));
      const eventHandlerCall = ts.createCall(eventHandlerAccess, [], args);
      let arrowFunc = ts.createArrowFunction([], [], handlerImpl.parameters, undefined, undefined, eventHandlerCall);
      arrowFunc = codeUtils.addNewLineBeforeNode(arrowFunc);

      const methodContent = this.getMethodContent("created");
      //See https://polymer-library.polymer-project.org/3.0/docs/devguide/gesture-events for a list of events
      if(["down", "up", "tap", "track"].indexOf(eventNameLiteral.text) >= 0) {
        const methodCall = ts.createCall(codeUtils.createNameExpression("Polymer.Gestures.addListener"), [], [ts.createThis(), eventNameLiteral, arrowFunc]);
        methodContent.codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
      }
      else {
        let methodCall = ts.createCall(ts.createPropertyAccess(ts.createThis(), "addEventListener"), [], [eventNameLiteral, arrowFunc]);
        methodCall = codeUtils.restoreLeadingComments(methodCall, commentsToRestore);
        methodContent.codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
      }
    }
  }

  public addHostAttributes(legacyHostAttributes: ts.ObjectLiteralExpression) {
    for(const listener of legacyHostAttributes.properties) {
      const propertyAssignment = codeUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment) as ts.PropertyAssignment;
      if(!propertyAssignment.name) {
        throw new Error("Listener must have event name");
      }
      let attributeNameLiteral: ts.StringLiteral;
      if(propertyAssignment.name.kind === ts.SyntaxKind.StringLiteral) {
        attributeNameLiteral = propertyAssignment.name;
      } else if(propertyAssignment.name.kind === ts.SyntaxKind.Identifier) {
        attributeNameLiteral = ts.createStringLiteral(propertyAssignment.name.text);
      } else {
        throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.name.kind]}`);
      }
      let attributeValueLiteral: ts.StringLiteral | ts.NumericLiteral;
      if(propertyAssignment.initializer.kind === ts.SyntaxKind.StringLiteral) {
        attributeValueLiteral = propertyAssignment.initializer as ts.StringLiteral;
      } else if(propertyAssignment.initializer.kind === ts.SyntaxKind.NumericLiteral) {
        attributeValueLiteral = propertyAssignment.initializer as ts.NumericLiteral;
      } else {
        throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.initializer.kind]}`);
      }
      const methodCall = ts.createCall(ts.createPropertyAccess(ts.createThis(), "_ensureAttribute"), [], [attributeNameLiteral, attributeValueLiteral]);
      this.getMethodContent("ready").codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
    }
  }

  public addLegacyLifecycleMethod(name: LegacyLifecycleMethodName, method: ts.MethodDeclaration) {
    const content = this.getMethodContent(name);
    if(content.existingMethod) {
      throw new Error(`Legacy lifecycle method ${name} already added`);
    }
    content.existingMethod = method;
  }

  public buildNewMethods(): LifecycleMethod[] {
    const result = [];
    for(const [name, content] of this.methods) {
      const newMethod = this.createLifecycleMethod(name, content.existingMethod, content.codeAtMethodStart, content.codeAtMethodEnd);
      if(!newMethod) continue;
      result.push({
        name,
        originalPos: content.existingMethod ? content.existingMethod.pos : -1,
        method: newMethod
      })
    }
    return result;
  }

  private createLifecycleMethod(name: string, methodDecl: ts.MethodDeclaration | undefined, codeAtStart: ts.Statement[], codeAtEnd: ts.Statement[]): ts.MethodDeclaration | undefined {
    return codeUtils.createMethod(name, methodDecl, codeAtStart, codeAtEnd, true);
  }
}