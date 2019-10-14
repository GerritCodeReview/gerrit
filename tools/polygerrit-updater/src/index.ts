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

import {LegacyPolymerComponent, PolymerComponentParser, PolymerComponentType, LegacyLifecycleMethodsArray, OrdinaryMethods} from "./polymerComponentParser";
import * as fs from "fs";
import * as path from "path";
import * as utils from "./utils";
import * as ts from "typescript";
import {ClassElement, HeritageClause, SyntaxKind} from "typescript";
import * as tsUtils from "./tsUtils";

interface UpdaterParameters {
  htmlFiles: Set<string>;
  jsFiles: Set<string>;
}

interface InputFilesFilter {
  includeDir(fullPath: string): boolean;
  includeFile(fullPath: path.ParsedPath): boolean;
}

function addFilesRecursive(dirPath: string, params: UpdaterParameters, filter: InputFilesFilter): void {
  const entries = fs.readdirSync(dirPath, {withFileTypes: true});
  for(const entry of entries) {
    const dirEnt = entry as fs.Dirent;
    const fullPath = path.join(dirPath, dirEnt.name);
    if(dirEnt.isDirectory()) {
      if (!filter.includeDir(fullPath)) continue;
      addFilesRecursive(fullPath, params, filter);
    }
    else if(dirEnt.isFile()) {
      const parsedPath = path.parse(fullPath);
      if(!filter.includeFile(parsedPath)) continue;
      const ext = parsedPath.ext.toLowerCase();
      if(ext === ".html") {
        params.htmlFiles.add(fullPath);
      } if(ext === ".js") {
        params.jsFiles.add(fullPath);
      }
    } else {
      throw Error(`Unsupported dir entry '${entry.name}' in '${fullPath}'`);
    }
  }
}

class IncludeAllFilesFilter implements InputFilesFilter {
  includeDir(fullPath: string): boolean {
    return true;
  }

  includeFile(fullPath: path.ParsedPath): boolean {
    //return true;
    return fullPath.name.indexOf("gr-admin-view") >= 0;
  }
}

async function getParams(): Promise<UpdaterParameters> {
  const params: UpdaterParameters = {
    htmlFiles: new Set(),
    jsFiles: new Set(),
  };
  addFilesRecursive(path.resolve( "../../polygerrit-ui/app"), params, new IncludeAllFilesFilter());
  return params;
}

class TsTextBuilder {
  private content: string = "";
  public appendString(str: string): void {
    this.content += str;
  }
  public writeToFile(fileName: string): void {
    fs.writeFileSync(fileName, this.content);
  }
}

async function updateLegacyComponent(component: LegacyPolymerComponent) {
  const builder: TsTextBuilder = new TsTextBuilder();
  const fullText = component.parsedFile.text;
  builder.appendString(fullText.substring(0, component.polymerFuncCallExpr.getStart()));
  const {classDeclaration, componentRegistration} = generatePolymerV2ClassFromLegacyComponent(component);

  // //ts.visitEachChild(, child => classDeclaration, );
  const polymerFuncCallStatement: ts.ExpressionStatement = tsUtils.assertNodeKind(component.polymerFuncCallExpr.parent, SyntaxKind.ExpressionStatement);
  const parentBlock: ts.Block = tsUtils.assertNodeKind(polymerFuncCallStatement.parent, SyntaxKind.Block);

  const updatedBlock = ts.getMutableClone(parentBlock);
  const index = parentBlock.statements.indexOf(polymerFuncCallStatement);
  if(index < 0) {
    throw new Error("Internal error! Couldn't find statement in its own parent");
  }
  const newStatements = Array.from(parentBlock.statements);
  newStatements.splice(index, 1, classDeclaration, componentRegistration);
  updatedBlock.statements = ts.createNodeArray(newStatements);

  const replaceResult = tsUtils.replaceNode(component.parsedFile, parentBlock, updatedBlock);

  const printer = ts.createPrinter();
  try {
    fs.writeFileSync(component.jsFile /*+ ".new"*/, printer.printFile(replaceResult.transformed[0]));
  }
  finally {
    replaceResult.dispose();
  }
}

function generatePolymerV2ClassFromLegacyComponent(component: LegacyPolymerComponent): { classDeclaration: ts.ClassDeclaration, componentRegistration: ts.ExpressionStatement} {
  const legacySettings = component.componentSettings;
  const reservedDeclarations = legacySettings.reservedDeclarations;
  if(!reservedDeclarations.is) {
    throw new Error("Component doesn't have 'is' property");
  }
  const members: ClassElement[] = [];
  members.push(createIsAccessor(reservedDeclarations.is));

  const className = generateClassNameFromTagName(reservedDeclarations.is);
  if(reservedDeclarations.properties) {
    const returnStatement = ts.createReturn(reservedDeclarations.properties);
    const block = ts.createBlock([returnStatement]);
    const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(SyntaxKind.StaticKeyword)], "properties", [], undefined, block);
    members.push(propertiesAccessor);
  }
  let baseClass = ts.createExpressionWithTypeArguments([], ts.createPropertyAccess(ts.createIdentifier("Polymer"), "Element"));
  if (reservedDeclarations._legacyUndefinedCheck) {
    baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(ts.createPropertyAccess(ts.createIdentifier("Polymer"), "LegacyDataMixin"), [], [baseClass.expression]))
  }
  let heritageClauses: HeritageClause[] = [];
  if(reservedDeclarations.behaviors) {
    baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(ts.createPropertyAccess(ts.createIdentifier("Polymer"), "mixinBehaviors"), [], [reservedDeclarations.behaviors, baseClass.expression]))
  }
  const extendClause = ts.createHeritageClause(ts.SyntaxKind.ExtendsKeyword, [baseClass]);
  heritageClauses.push(extendClause);

  if(reservedDeclarations.observers) {
    const returnStatement = ts.createReturn(reservedDeclarations.observers);
    const block = ts.createBlock([returnStatement]);
    const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(SyntaxKind.StaticKeyword)], "observers", [], undefined, block);
    members.push(propertiesAccessor);
  }

  for(const name of LegacyLifecycleMethodsArray) {
    if(!legacySettings.lifecycleMethods.hasOwnProperty(name)) continue;
    members.push(createLifecycleMethod(legacySettings.lifecycleMethods.get(name)!));
  }
  for(const method of legacySettings.ordinaryMethods) {
    //members.splice(0, 0, legacySettings.ordinaryMethods.values());
    members.push(method[1]);
  }
  //const v = legacySettings.lifecycleMethods["a"];

  /*for(const name in legacySettings.ordinaryMethods) {
    if(!legacySettings.ordinaryMethods.hasOwnProperty(name)) continue;
    members.push(legacySettings.ordinaryMethods);
  }*/
  const callExpression = ts.createCall(ts.createPropertyAccess(ts.createIdentifier("customElements"), "define"), undefined, [ts.createPropertyAccess(ts.createIdentifier(className), "is"), ts.createIdentifier(className)]);
  return {
    classDeclaration: ts.createClassDeclaration(undefined, undefined, className, undefined, heritageClauses, members),
    componentRegistration: ts.createExpressionStatement(callExpression),
  };
}

function createLifecycleMethod(methodDecl: ts.MethodDeclaration): ts.MethodDeclaration {
  if (!methodDecl.body) {
    throw new Error("Method must have a body");
  }
  if(methodDecl.parameters.length > 0) {
    throw new Error("Methods with parameters are not supported");
  }
  const superMethodCall = ts.createExpressionStatement(ts.createCall(ts.createPropertyAccess(ts.createSuper(), tsUtils.assertNodeKind(methodDecl.name, ts.SyntaxKind.Identifier) as ts.Identifier), [], []));

  const newBody = ts.getMutableClone(methodDecl.body);
  const newStatements = Array.from(newBody.statements);
  newStatements.splice(0, 0, superMethodCall);
  newBody.statements = ts.createNodeArray(newStatements);

  const newMethod = ts.getMutableClone(methodDecl);
  newMethod.body = newBody;

  return newMethod;
}

function createIsAccessor(tagName: string): ts.GetAccessorDeclaration {
  const returnStatement = ts.createReturn(ts.createStringLiteral(tagName));
  const block = ts.createBlock([returnStatement]);
  return ts.createGetAccessor([], [ts.createModifier(SyntaxKind.StaticKeyword)], "is", [], undefined, block);
}

function generateClassNameFromTagName(tagName: string) {
  let result = "";
  let nextUppercase = true;
  for(const ch of tagName) {
    if (ch === '-') {
      nextUppercase = true;
      continue;
    }
    result += nextUppercase ? ch.toUpperCase() : ch;
    nextUppercase = false;
  }
  return result;
}

async function main() {
  const params: UpdaterParameters = await getParams();
  const componentParser = new PolymerComponentParser(params.htmlFiles)
  for(const jsFile of params.jsFiles) {
    const polymerComponent = await componentParser.parse(jsFile);
    if(!polymerComponent) {
      continue;
    }
    switch(polymerComponent.type) {
      case PolymerComponentType.Legacy:
        await updateLegacyComponent(polymerComponent);
        break;
      default:
        utils.unexpetedValue(polymerComponent.type);

    }
  }
}

main().then(() => {
  process.exit(0);
}).catch(e => {
  console.error(e);
  process.exit(1);
});
