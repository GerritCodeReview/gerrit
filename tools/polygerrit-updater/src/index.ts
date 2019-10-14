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

import {LegacyLifecycleMethodsArray, LegacyPolymerComponent, PolymerComponentParser, PolymerComponentType} from "./polymerComponentParser";
import * as fs from "fs";
import * as path from "path";
import * as utils from "./utils";
import * as ts from "typescript";
import {ClassElement, EmitHint, HeritageClause, PrinterOptions, SyntaxKind} from "typescript";
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
    //return fullPath.name.indexOf("gr-diff") >= 0;
    const exclude = ["gr-reporting", "gr-dom-hooks"].some(name => fullPath.name.indexOf(name) >= 0);
    return !exclude;
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
  const options: PrinterOptions = {
    removeComments: false,
    newLine: ts.NewLineKind.LineFeed,
  };
  const printer = ts.createPrinter(options);
  try {
    const newContent =tsUtils.applyNewLines(printer.printFile(replaceResult.transformed[0]));
    const afterReformat = restoreFormating(printer, component.parsedFile, newContent);
    //printer.printFile(replaceResult.transformed[0]);
    //printer.printNode(EmitHint.Unspecified);
    const afterRemoveLongLines = afterReformat.replace("Polymer.LegacyDataMixin(Polymer.GestureEventListeners(Polymer.LegacyElementMixin(Polymer.Element))))", "Polymer.LegacyDataMixin(\nPolymer.GestureEventListeners(\nPolymer.LegacyElementMixin(\nPolymer.Element))))")
    fs.writeFileSync(component.jsFile /*+ ".new"*/, afterRemoveLongLines);
  }
  finally {
    replaceResult.dispose();
  }
}

function restoreFormating(printer: ts.Printer, originalFile: ts.SourceFile, newContent: string): string {
  const newFile = ts.createSourceFile(originalFile.fileName, newContent, originalFile.languageVersion, true, ts.ScriptKind.JS);
  //ts.createSourceFile(jsFile, fs.readFileSync(jsFile).toString(), ts.ScriptTarget.ES2015, true);
  const textMap = new Map<ts.SyntaxKind, Map<string, Set<string>>>();
  collectAllStrings(printer, originalFile, textMap);

  const replacements: Replacement[] = [];
  collectReplacements(printer, newFile, textMap, replacements);
  replacements.sort((a, b) => b.start - a.start);
  let result = newFile.getFullText();
  let prevReplacement: Replacement | null = null;
  for(const replacement of replacements) {
    if(prevReplacement) {
      if(replacement.start + replacement.length > prevReplacement.start) {
        throw new Error('Internal error! Replacements must not intersect');
      }
    }
    result = result.substring(0, replacement.start) + replacement.newText + result.substring(replacement.start + replacement.length);
    prevReplacement = replacement;
  }
  return result;
}

function addIfNotExists(map: Map<ts.SyntaxKind, Map<string, Set<string>>>, kind: ts.SyntaxKind, formattedText: string, originalText: string) {
  let mapForKind = map.get(kind);
  if(!mapForKind) {
    mapForKind = new Map();
    map.set(kind, mapForKind);
  }

  let existingOriginalText = mapForKind.get(formattedText);
  if(!existingOriginalText) {
    existingOriginalText = new Set<string>();
    mapForKind.set(formattedText, existingOriginalText);
    //throw new Error(`Different formatting of the same string exists. Kind: ${ts.SyntaxKind[kind]}.\nFormatting 1:\n${originalText}\nFormatting2:\n${existingOriginalText}\n `);
  }
  existingOriginalText.add(originalText);
}

function getReplacement(printer: ts.Printer, node: ts.Node, map: Map<ts.SyntaxKind, Map<string, Set<string>>>): Replacement | undefined {
  const replacementsForKind = map.get(node.kind);
  if(!replacementsForKind) {
    return;
  }
  // Use printer instead of getFullText to "isolate" node content.
  // node.getFullText returns text with indents from the original file.
  const newText = printer.printNode(EmitHint.Unspecified, node, node.getSourceFile());
  /*if(newText.indexOf("(!editingOld)") >= 0) {
    console.log("New text!!!!");
  }*/
  const originalSet = replacementsForKind.get(newText);
  if(!originalSet || originalSet.size === 0) {
    return;
  }
  if(originalSet.size > 2) {
    console.log(`Multiple replacements possible`);
    return;
  }
  const replacementText: string = originalSet.values().next().value;
  const nodeText = node.getFullText();
  return {
    start: node.pos,
    length: nodeText.length,//Do not use newText here!
    newText: replacementText,
  }
}

function collectReplacements(printer: ts.Printer, node: ts.Node, map: Map<ts.SyntaxKind, Map<string, Set<string>>>, replacements: Replacement[]) {
  if(node.kind === ts.SyntaxKind.ThisKeyword || node.kind === ts.SyntaxKind.Identifier || node.kind === ts.SyntaxKind.StringLiteral || node.kind === ts.SyntaxKind.NumericLiteral) {
    return;
  }
  const replacement = getReplacement(printer, node, map);
  if(replacement) {
    replacements.push(replacement);
    return;
  }
  ts.forEachChild(node, child => collectReplacements(printer, child, map, replacements));
}
interface Replacement {
  start: number;
  length: number;
  newText: string;
}

function collectAllStrings(printer: ts.Printer, node: ts.Node, map: Map<ts.SyntaxKind, Map<string, Set<string>>>) {
  /*if(node.kind === ts.SyntaxKind.Identifier || node.kind === ts.SyntaxKind.ThisKeyword || node.kind === ts.SyntaxKind.PropertyAccessExpression || node.kind === ts.SyntaxKind.Parameter) {
    return;
  }*/
  const formattedText = printer.printNode(EmitHint.Unspecified, node, node.getSourceFile())
  const originalText = node.getFullText();
  addIfNotExists(map, node.kind, formattedText, originalText);
  ts.forEachChild(node, child => collectAllStrings(printer, child, map));
}

function generatePolymerV2ClassFromLegacyComponent(component: LegacyPolymerComponent): { classDeclaration: ts.ClassDeclaration, componentRegistration: ts.ExpressionStatement} {
  const legacySettings = component.componentSettings;
  const reservedDeclarations = legacySettings.reservedDeclarations;
  if(!reservedDeclarations.is) {
    throw new Error("Component doesn't have 'is' property");
  }
  //We want to keep the order of members, if possible. pos is the original position for a member
  const members: {pos: number, member: ClassElement}[] = [];
  //Place 'is' at the top
  members.push({pos: -1, member: createIsAccessor(reservedDeclarations.is)});

  const className = generateClassNameFromTagName(reservedDeclarations.is);
  if(reservedDeclarations.properties) {
    const returnStatement = ts.createReturn(reservedDeclarations.properties);
    const block = ts.createBlock([returnStatement]);
    const propertiesAccessor = ts.createGetAccessor(undefined, [ts.createModifier(SyntaxKind.StaticKeyword)], "properties", [], undefined, block);
    members.push({pos: reservedDeclarations.properties.pos, member: propertiesAccessor});
  }
  let baseClass = ts.createExpressionWithTypeArguments([], ts.createPropertyAccess(ts.createIdentifier("Polymer"), "Element"));
  baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(tsUtils.createNameExpression("Polymer.LegacyElementMixin"), [], [baseClass.expression]));
  baseClass = ts.createExpressionWithTypeArguments([], ts.createCall(tsUtils.createNameExpression("Polymer.GestureEventListeners"), [], [baseClass.expression]));

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
    members.push({pos: reservedDeclarations.observers.pos, member: propertiesAccessor});
  }
  for(const name of LegacyLifecycleMethodsArray) {
    let codeAtMethodStart: ts.Statement[] = [];
    let codeAtMethodEnd: ts.Statement[] = [];
    if(name === "created" && reservedDeclarations.listeners) {
      for(const listener of reservedDeclarations.listeners.properties) {
        const propertyAssignment = tsUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment) as ts.PropertyAssignment;
        if(!propertyAssignment.name) {
          throw new Error("Listener must have event name");
        }
        let eventNameLiteral: ts.StringLiteral;
        if(propertyAssignment.name.kind === ts.SyntaxKind.StringLiteral) {
          eventNameLiteral = propertyAssignment.name;
        } else if(propertyAssignment.name.kind === ts.SyntaxKind.Identifier) {
          eventNameLiteral = ts.createStringLiteral(propertyAssignment.name.text);
        } else {
          throw new Error(`Unsupported type ${ts.SyntaxKind[propertyAssignment.name.kind]}`);
        }
        const handlerLiteral = tsUtils.assertNodeKind(propertyAssignment.initializer, ts.SyntaxKind.StringLiteral) as ts.StringLiteral;
        const handlerImpl = legacySettings.ordinaryMethods.get(handlerLiteral.text);
        if(!handlerImpl) {
          throw new Error(`Can't find event handler '${handlerLiteral.text}'`);
        }
        const eventHandlerAccess = ts.createPropertyAccess(ts.createThis(), handlerLiteral.text);
        //ts.forEachChild(handler)
        const args: ts.Identifier[] = handlerImpl.parameters.map((arg) => tsUtils.assertNodeKind(arg.name, ts.SyntaxKind.Identifier));
        const eventHandlerCall = ts.createCall(eventHandlerAccess, [], args);
        const arrowFunc = ts.createArrowFunction([], [], handlerImpl.parameters, undefined, undefined, eventHandlerCall);

        //See https://polymer-library.polymer-project.org/3.0/docs/devguide/gesture-events for a list of events
        if(["down", "up", "tap", "track"].indexOf(eventNameLiteral.text) >= 0) {
          const methodCall = ts.createCall(tsUtils.createNameExpression("Polymer.Gestures.addListener"), [], [ts.createThis(), eventNameLiteral, arrowFunc]);
          codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));

        }
        else {
          const methodCall = ts.createCall(ts.createPropertyAccess(ts.createThis(), "addEventListener"), [], [eventNameLiteral, arrowFunc]);
          codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
        }
      }
    }

    if(name === "ready" && reservedDeclarations.hostAttributes) {
      for(const listener of reservedDeclarations.hostAttributes.properties) {
        const propertyAssignment = tsUtils.assertNodeKind(listener, ts.SyntaxKind.PropertyAssignment) as ts.PropertyAssignment;
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
        codeAtMethodEnd.push(ts.createExpressionStatement(methodCall));
      }
      //codeAtMethodStart
    }
    //if(!legacySettings.lifecycleMethods.hasOwnProperty(name) && !codeAtMethodStart && !codeAtMethodEnd) continue;
    const existingMethod = legacySettings.lifecycleMethods.get(name);
    const newMethod = createLifecycleMethod(name, existingMethod, codeAtMethodStart, codeAtMethodEnd);
    if(newMethod) {
      members.push({pos: existingMethod ? existingMethod.pos : 999999999, member:newMethod});
    }
  }
  for(const [name, method] of legacySettings.ordinaryMethods) {
    members.push({pos: method.pos, member: method});
  }
  for(const [name, accessor] of legacySettings.ordinaryGetAccessors) {
    members.push({pos: accessor.pos, member: accessor});
  }

  if(legacySettings.ordinaryShorthandProperties) {
    console.log("Warning! File has ordinaryShorthandProperties");
    for (const [name, property] of legacySettings.ordinaryShorthandProperties) {
      const returnStatement = ts.createReturn(property.name);
      const block = ts.createBlock([returnStatement]);
      const getAccessor = ts.createGetAccessor([], [], property.name, [], undefined, block);
      members.push({pos: property.pos, member: getAccessor});

    }
  }

  if(legacySettings.ordinaryPropertyAssignments) {
    console.log("Warning! File has ordinaryPropertyAssignments");
    for (const [name, property] of legacySettings.ordinaryPropertyAssignments) {
      const returnStatement = ts.createReturn(property.initializer);
      const block = ts.createBlock([returnStatement]);
      const getAccessor = ts.createGetAccessor([], [], property.name, [], undefined, block);
      members.push({pos: property.pos, member: getAccessor});
    }
  }
  members.sort((a, b) => a.pos - b.pos);

  const callExpression = ts.createCall(ts.createPropertyAccess(ts.createIdentifier("customElements"), "define"), undefined, [ts.createPropertyAccess(ts.createIdentifier(className), "is"), ts.createIdentifier(className)]);
  return {
    classDeclaration: ts.createClassDeclaration(undefined, undefined, className, undefined, heritageClauses, members.map(m => m.member)),
    componentRegistration: ts.createExpressionStatement(callExpression),
  };
}

function createLifecycleMethod(name: string, methodDecl: ts.MethodDeclaration | undefined, codeAtStart: ts.Statement[], codeAtEnd: ts.Statement[]): ts.MethodDeclaration | undefined {
  if(!methodDecl && (codeAtEnd.length > 0 || codeAtEnd.length > 0)) {
    methodDecl = ts.createMethod([], [], undefined, name, undefined, [], [],undefined, ts.createBlock([]));
  }
  if(!methodDecl) {
    return;
  }
  if (!methodDecl.body) {
    throw new Error("Method must have a body");
  }
  if(methodDecl.parameters.length > 0) {
    throw new Error("Methods with parameters are not supported");
  }
  let superMethodCall = ts.createExpressionStatement(ts.createCall(ts.createPropertyAccess(ts.createSuper(), tsUtils.assertNodeKind(methodDecl.name, ts.SyntaxKind.Identifier) as ts.Identifier), [], []));
  const newBody = ts.getMutableClone(methodDecl.body);

  const newStatements = [...codeAtStart, superMethodCall, ...codeAtEnd].map(m => tsUtils.addNewLineAfterNode(m));
  newStatements.splice(codeAtStart.length + 1, 0, ...newBody.statements);

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
    console.log(`Processing ${jsFile}`);
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
