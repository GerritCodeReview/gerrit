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

import * as ts from "typescript";
import * as fs from "fs";
import * as path from "path";
import { unexpectedValue } from "../utils/unexpectedValue";
import * as codeUtils from "../utils/codeUtils";
import {CommentsParser} from '../utils/commentsParser';

export class LegacyPolymerComponentParser {
  public constructor(private readonly rootDir: string, private readonly htmlFiles: Set<string>) {
  }
  public async parse(jsFile: string): Promise<ParsedPolymerComponent | null> {
    const sourceFile: ts.SourceFile  = this.parseJsFile(jsFile);
    const legacyComponent = this.tryParseLegacyComponent(sourceFile);
    if (legacyComponent) {
      return legacyComponent;
    }
    return null;
  }
  private parseJsFile(jsFile: string): ts.SourceFile {
    return ts.createSourceFile(jsFile, fs.readFileSync(path.resolve(this.rootDir, jsFile)).toString(), ts.ScriptTarget.ES2015, true);
  }

  private tryParseLegacyComponent(sourceFile: ts.SourceFile): ParsedPolymerComponent | null {
    const polymerFuncCalls: ts.CallExpression[] = [];

    function addPolymerFuncCall(node: ts.Node) {
      if(node.kind === ts.SyntaxKind.CallExpression) {
        const callExpression: ts.CallExpression = node as ts.CallExpression;
        if(callExpression.expression.kind === ts.SyntaxKind.Identifier) {
          const identifier = callExpression.expression as ts.Identifier;
          if(identifier.text === "Polymer") {
            polymerFuncCalls.push(callExpression);
          }
        }
      }
      ts.forEachChild(node, addPolymerFuncCall);
    }

    addPolymerFuncCall(sourceFile);


    if (polymerFuncCalls.length === 0) {
      return null;
    }
    if (polymerFuncCalls.length > 1) {
      throw new Error("Each .js file must contain only one Polymer component");
    }
    const parsedPath = path.parse(sourceFile.fileName);
    const htmlFullPath = path.format({
      dir: parsedPath.dir,
      name: parsedPath.name,
      ext: ".html"
    });
    if (!this.htmlFiles.has(htmlFullPath)) {
      throw new Error("Legacy .js component dosn't have associated .html file");
    }

    const polymerFuncCall = polymerFuncCalls[0];
    if(polymerFuncCall.arguments.length !== 1) {
      throw new Error("The Polymer function must be called with exactly one parameter");
    }
    const argument = polymerFuncCall.arguments[0];
    if(argument.kind !== ts.SyntaxKind.ObjectLiteralExpression) {
      throw new Error("The parameter for Polymer function must be ObjectLiteralExpression (i.e. '{...}')");
    }
    const infoArg = argument as ts.ObjectLiteralExpression;

    return {
      jsFile: sourceFile.fileName,
      htmlFile: htmlFullPath,
      parsedFile: sourceFile,
      polymerFuncCallExpr: polymerFuncCalls[0],
      componentSettings: this.parseLegacyComponentSettings(infoArg),
    };
  }

  private parseLegacyComponentSettings(info: ts.ObjectLiteralExpression): LegacyPolymerComponentSettings {
    const props: Map<string, ts.ObjectLiteralElementLike> = new Map();
    for(const property of info.properties) {
      const name = property.name;
      if (name === undefined) {
        throw new Error("Property name is not defined");
      }
      switch(name.kind) {
        case ts.SyntaxKind.Identifier:
        case ts.SyntaxKind.StringLiteral:
          if (props.has(name.text)) {
            throw new Error(`Property ${name.text} appears more than once`);
          }
          props.set(name.text, property);
          break;
        case ts.SyntaxKind.ComputedPropertyName:
          continue;
        default:
          unexpectedValue(ts.SyntaxKind[name.kind]);
      }
    }

    if(props.has("_noAccessors")) {
      throw new Error("_noAccessors is not supported");
    }

    const legacyLifecycleMethods: LegacyLifecycleMethods = new Map();
    for(const name of LegacyLifecycleMethodsArray) {
      const methodDecl = this.getLegacyMethodDeclaration(props, name);
      if(methodDecl) {
        legacyLifecycleMethods.set(name, methodDecl);
      }
    }

    const ordinaryMethods: OrdinaryMethods = new Map();
    const ordinaryShorthandProperties: OrdinaryShorthandProperties = new Map();
    const ordinaryGetAccessors: OrdinaryGetAccessors = new Map();
    const ordinaryPropertyAssignments: OrdinaryPropertyAssignments = new Map();
    for(const [name, val] of props) {
      if(RESERVED_NAMES.hasOwnProperty(name)) continue;
      switch(val.kind) {
        case ts.SyntaxKind.MethodDeclaration:
          ordinaryMethods.set(name, val as ts.MethodDeclaration);
          break;
        case ts.SyntaxKind.ShorthandPropertyAssignment:
          ordinaryShorthandProperties.set(name, val as ts.ShorthandPropertyAssignment);
          break;
        case ts.SyntaxKind.GetAccessor:
          ordinaryGetAccessors.set(name, val as ts.GetAccessorDeclaration);
          break;
        case ts.SyntaxKind.PropertyAssignment:
          ordinaryPropertyAssignments.set(name, val as ts.PropertyAssignment);
          break;
        default:
          throw new Error(`Unsupported element kind: ${ts.SyntaxKind[val.kind]}`);
      }
      //ordinaryMethods.set(name, tsUtils.assertNodeKind(val, ts.SyntaxKind.MethodDeclaration) as ts.MethodDeclaration);
    }

    const eventsComments: string[] = this.getEventsComments(info.getFullText());

    return {
      reservedDeclarations: {
        is: this.getStringLiteralValueWithComments(this.getLegacyPropertyInitializer(props, "is")),
        _legacyUndefinedCheck: this.getBooleanLiteralValueWithComments(this.getLegacyPropertyInitializer(props, "_legacyUndefinedCheck")),
        properties: this.getObjectLiteralExpressionWithComments(this.getLegacyPropertyInitializer(props, "properties")),
        behaviors: this.getArrayLiteralExpressionWithComments(this.getLegacyPropertyInitializer(props, "behaviors")),
        observers: this.getArrayLiteralExpressionWithComments(this.getLegacyPropertyInitializer(props, "observers")),
        listeners: this.getObjectLiteralExpressionWithComments(this.getLegacyPropertyInitializer(props, "listeners")),
        hostAttributes: this.getObjectLiteralExpressionWithComments(this.getLegacyPropertyInitializer(props, "hostAttributes")),
        keyBindings: this.getObjectLiteralExpressionWithComments(this.getLegacyPropertyInitializer(props, "keyBindings")),
      },
      eventsComments: eventsComments,
      lifecycleMethods: legacyLifecycleMethods,
      ordinaryMethods: ordinaryMethods,
      ordinaryShorthandProperties: ordinaryShorthandProperties,
      ordinaryGetAccessors: ordinaryGetAccessors,
      ordinaryPropertyAssignments: ordinaryPropertyAssignments,
    };
  }

  private convertLegacyProeprtyInitializer<T>(initializer: LegacyPropertyInitializer | undefined, converter: (exp: ts.Expression) => T): DataWithComments<T> | undefined {
    if(!initializer) {
      return undefined;
    }
    return {
      data: converter(initializer.data),
      leadingComments: initializer.leadingComments,
    }
  }

  private getObjectLiteralExpressionWithComments(initializer: LegacyPropertyInitializer | undefined): DataWithComments<ts.ObjectLiteralExpression> | undefined {
    return this.convertLegacyProeprtyInitializer(initializer,
        expr => codeUtils.getObjectLiteralExpression(expr));
  }

  private getStringLiteralValueWithComments(initializer: LegacyPropertyInitializer | undefined): DataWithComments<string> | undefined {
    return this.convertLegacyProeprtyInitializer(initializer,
        expr => codeUtils.getStringLiteralValue(expr));
  }

  private getBooleanLiteralValueWithComments(initializer: LegacyPropertyInitializer | undefined): DataWithComments<boolean> | undefined {
    return this.convertLegacyProeprtyInitializer(initializer,
        expr => codeUtils.getBooleanLiteralValue(expr));
  }


  private getArrayLiteralExpressionWithComments(initializer: LegacyPropertyInitializer | undefined): DataWithComments<ts.ArrayLiteralExpression> | undefined {
    return this.convertLegacyProeprtyInitializer(initializer,
        expr => codeUtils.getArrayLiteralExpression(expr));
  }

  private getLegacyPropertyInitializer(props: Map<String, ts.ObjectLiteralElementLike>, propName: string): LegacyPropertyInitializer | undefined {
    const property = props.get(propName);
    if (!property) {
      return undefined;
    }
    const assignment = codeUtils.getPropertyAssignment(property);
    if (!assignment) {
      return undefined;
    }
    const comments: string[] = codeUtils.getLeadingComments(property)
          .filter(c => !this.isEventComment(c));
    return {
      data: assignment.initializer,
      leadingComments: comments,
    };
  }

  private isEventComment(comment: string): boolean {
    return comment.indexOf('@event') >= 0;
  }

  private getEventsComments(polymerComponentSource: string): string[] {
    return CommentsParser.collectAllComments(polymerComponentSource)
        .filter(c => this.isEventComment(c));
  }

  private getLegacyMethodDeclaration(props: Map<String, ts.ObjectLiteralElementLike>, propName: string): ts.MethodDeclaration | undefined {
    const property = props.get(propName);
    if (!property) {
      return undefined;
    }
    return codeUtils.assertNodeKind(property, ts.SyntaxKind.MethodDeclaration) as ts.MethodDeclaration;
  }

}

export type ParsedPolymerComponent = LegacyPolymerComponent;

export interface LegacyPolymerComponent {
  jsFile: string;
  htmlFile: string;
  parsedFile: ts.SourceFile;
  polymerFuncCallExpr: ts.CallExpression;
  componentSettings: LegacyPolymerComponentSettings;
}

export interface LegacyReservedDeclarations {
  is?: DataWithComments<string>;
  _legacyUndefinedCheck?: DataWithComments<boolean>;
  properties?: DataWithComments<ts.ObjectLiteralExpression>;
  behaviors?: DataWithComments<ts.ArrayLiteralExpression>,
  observers? :DataWithComments<ts.ArrayLiteralExpression>,
  listeners? :DataWithComments<ts.ObjectLiteralExpression>,
  hostAttributes?: DataWithComments<ts.ObjectLiteralExpression>,
  keyBindings?: DataWithComments<ts.ObjectLiteralExpression>,
}

export const LegacyLifecycleMethodsArray = <const>["beforeRegister", "registered", "created", "ready", "attached" , "detached", "attributeChanged"];
export type LegacyLifecycleMethodName = typeof LegacyLifecycleMethodsArray[number];
export type LegacyLifecycleMethods = Map<LegacyLifecycleMethodName, ts.MethodDeclaration>;
export type OrdinaryMethods = Map<string, ts.MethodDeclaration>;
export type OrdinaryShorthandProperties = Map<string, ts.ShorthandPropertyAssignment>;
export type OrdinaryGetAccessors = Map<string, ts.GetAccessorDeclaration>;
export type OrdinaryPropertyAssignments = Map<string, ts.PropertyAssignment>;
export type ReservedName = LegacyLifecycleMethodName | keyof LegacyReservedDeclarations;
export const RESERVED_NAMES: {[x in ReservedName]: boolean} = {
  attached: true,
  detached: true,
  ready: true,
  created: true,
  beforeRegister: true,
  registered: true,
  attributeChanged: true,
  is: true,
  _legacyUndefinedCheck: true,
  properties: true,
  behaviors: true,
  observers: true,
  listeners: true,
  hostAttributes: true,
  keyBindings: true,
};

export interface LegacyPolymerComponentSettings {
  reservedDeclarations: LegacyReservedDeclarations;
  lifecycleMethods: LegacyLifecycleMethods,
  ordinaryMethods: OrdinaryMethods,
  ordinaryShorthandProperties: OrdinaryShorthandProperties,
  ordinaryGetAccessors: OrdinaryGetAccessors,
  ordinaryPropertyAssignments: OrdinaryPropertyAssignments,
  eventsComments: string[];
}

export interface DataWithComments<T> {
  data: T;
  leadingComments: string[];
}

type LegacyPropertyInitializer = DataWithComments<ts.Expression>;