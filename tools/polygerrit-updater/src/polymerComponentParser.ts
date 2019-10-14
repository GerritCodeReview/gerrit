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
import * as utils from "./utils";
import * as tsUtils from "./tsUtils";
import {SyntaxKind} from 'typescript';

export class PolymerComponentParser {
  public constructor(private readonly htmlFiles: Set<string>) {
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
    return ts.createSourceFile(jsFile, fs.readFileSync(jsFile).toString(), ts.ScriptTarget.ES2015, true);
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
      type: PolymerComponentType.Legacy,
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
        default:
          utils.unexpetedValue(name.kind);
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
          ordinaryShorthandProperties.set(name, val as ts.ShorthandPropertyAssignment)
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


    return {
      reservedDeclarations: {
        is: tsUtils.getStringLiteralValue(this.getLegacyPropertyInitializer(props, "is")),
        _legacyUndefinedCheck: tsUtils.getBooleanLiteralValue(this.getLegacyPropertyInitializer(props, "_legacyUndefinedCheck")),
        properties: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "properties")),
        behaviors: tsUtils.getArrayLiteralExpression(this.getLegacyPropertyInitializer(props, "behaviors")),
        observers: tsUtils.getArrayLiteralExpression(this.getLegacyPropertyInitializer(props, "observers")),
        listeners: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "listeners")),
        hostAttributes: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "hostAttributes")),
      },
      lifecycleMethods: legacyLifecycleMethods,
      ordinaryMethods: ordinaryMethods,
      ordinaryShorthandProperties: ordinaryShorthandProperties,
      ordinaryGetAccessors: ordinaryGetAccessors,
      ordinaryPropertyAssignments: ordinaryPropertyAssignments,
      //ordinaryMethods:
      /*attached: this.getLegacyMethodDeclaration(props, "attached"),
      detached: this.getLegacyMethodDeclaration(props, "detached"),
      ready: this.getLegacyMethodDeclaration(props, "ready"),
      created: this.getLegacyMethodDeclaration(props, "created"),
      beforeRegister: this.getLegacyMethodDeclaration(props, "beforeRegister"),
      registered: this.getLegacyMethodDeclaration(props, "registered"),
      attributeChanged: this.getLegacyMethodDeclaration(props, "registered"),*/
    };
  }

  private getLegacyPropertyInitializer(props: Map<String, ts.ObjectLiteralElementLike>, propName: string): ts.Expression | undefined {
    const property = props.get(propName);
    if (!property) {
      return undefined;
    }
    const assignment = tsUtils.getPropertyAssignment(property);
    if (!assignment) {
      return undefined;
    }
    return assignment.initializer;

  }

  private getLegacyMethodDeclaration(props: Map<String, ts.ObjectLiteralElementLike>, propName: string): ts.MethodDeclaration | undefined {
    const property = props.get(propName);
    if (!property) {
      return undefined;
    }
    return tsUtils.assertNodeKind(property, SyntaxKind.MethodDeclaration) as ts.MethodDeclaration;
  }

}

export type ParsedPolymerComponent = LegacyPolymerComponent;

export enum PolymerComponentType {
  Legacy
}

export interface LegacyPolymerComponent {
  type: PolymerComponentType.Legacy;
  jsFile: string;
  htmlFile: string;
  parsedFile: ts.SourceFile;
  polymerFuncCallExpr: ts.CallExpression;
  componentSettings: LegacyPolymerComponentSettings;
}

export interface LegacyReservedDeclarations {
  is?: string;
  _legacyUndefinedCheck?: boolean;
  properties?: ts.ObjectLiteralExpression;
  behaviors?: ts.ArrayLiteralExpression,
  observers? :ts.ArrayLiteralExpression,
  listeners? :ts.ObjectLiteralExpression,
  hostAttributes?: ts.ObjectLiteralExpression,
}

export const LegacyLifecycleMethodsArray = <const>["attached" , "detached",  "ready", "created", "beforeRegister", "registered", "attributeChanged"];
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
};

export interface LegacyPolymerComponentSettings {
  reservedDeclarations: LegacyReservedDeclarations;
  lifecycleMethods: LegacyLifecycleMethods,
  ordinaryMethods: OrdinaryMethods,
  ordinaryShorthandProperties: OrdinaryShorthandProperties,
  ordinaryGetAccessors: OrdinaryGetAccessors,
  ordinaryPropertyAssignments: OrdinaryPropertyAssignments,
  /*attached?: ts.MethodDeclaration,
  detached?: ts.MethodDeclaration,
  ready?: ts.MethodDeclaration,
  created?: ts.MethodDeclaration,
  beforeRegister?: ts.MethodDeclaration,
  registered?: ts.MethodDeclaration,
  attributeChanged?: ts.MethodDeclaration,*/
}