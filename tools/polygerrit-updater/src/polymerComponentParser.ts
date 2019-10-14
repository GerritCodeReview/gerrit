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
import {Expression} from 'typescript';

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
    const props: Map<String, ts.ObjectLiteralElementLike> = new Map();
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
    return {
      is: tsUtils.getStringLiteralValue(this.getLegacyPropertyInitializer(props, "is")),
      _legacyUndefinedCheck: tsUtils.getBooleanLiteralValue(this.getLegacyPropertyInitializer(props, "_legacyUndefinedCheck")),
      properties: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "properties")),
      behaviors: tsUtils.getArrayLiteralExpression(this.getLegacyPropertyInitializer(props, "behvaiors")),
      observers: tsUtils.getArrayLiteralExpression(this.getLegacyPropertyInitializer(props, "observers")),
      listeners: tsUtils.getArrayLiteralExpression(this.getLegacyPropertyInitializer(props, "listeners")),
      hostAttributes: tsUtils.getObjectLiteralExpression(this.getLegacyPropertyInitializer(props, "hostAttributes")),
    };
  }

  private getLegacyPropertyInitializer(props: Map<String, ts.ObjectLiteralElementLike>, propName: string): Expression | undefined {
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

export interface LegacyPolymerComponentSettings {
  is?: string;
  _legacyUndefinedCheck?: boolean;
  properties?: ts.ObjectLiteralExpression;
  behaviors?: ts.ArrayLiteralExpression,
  observers? :ts.ArrayLiteralExpression,
  listeners? :ts.ArrayLiteralExpression,
  hostAttributes?: ts.ObjectLiteralExpression,
}
