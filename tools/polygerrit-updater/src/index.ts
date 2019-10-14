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

import {LegacyLifecycleMethodName, LegacyLifecycleMethodsArray, LegacyPolymerComponent, LegacyReservedDeclarations, OrdinaryGetAccessors, OrdinaryMethods, OrdinaryPropertyAssignments, OrdinaryShorthandProperties, PolymerComponentParser, PolymerComponentType} from "./polymerComponentParser";
import * as fs from "fs";
import * as path from "path";
import * as utils from "./utils";
import * as ts from "typescript";
import {ClassElement, EmitHint, ExpressionWithTypeArguments, HeritageClause, PrinterOptions, ScriptTarget, SyntaxKind} from "typescript";
import * as tsUtils from "./tsUtils";
import {CommandLineParser} from "./commandLineParser";
import {PolymerClassBuilder} from './polymerClassBuilder';
import {LifecycleMethod, LifecycleMethodsBuilder} from './lifecycleMethodsBuilder';
import {PolymerClassFromLegacyComponentGenerator} from './polymerClassFromLegacyComponentGenerator';

interface UpdaterParameters {
  htmlFiles: Set<string>;
  jsFiles: Set<string>;
  out: string;
  inplace: boolean;
  writeOutput: boolean;
  rootDir: string;
}

interface InputFilesFilter {
  includeDir(path: string): boolean;
  includeFile(path: string): boolean;
}

function addFile(filePath: string, params: UpdaterParameters, filter: InputFilesFilter) {
  const parsedPath = path.parse(filePath);
  const ext = parsedPath.ext.toLowerCase();
  const relativePath = path.relative(params.rootDir, filePath);
  if(!filter.includeFile(relativePath)) return;
  if(relativePath.startsWith("../")) {
    throw new Error(`${filePath} is not in rootDir ${params.rootDir}`);
  }
  if(ext === ".html") {
    params.htmlFiles.add(relativePath);
  } if(ext === ".js") {
    params.jsFiles.add(relativePath);
  }
}

function addDirectory(dirPath: string, params: UpdaterParameters, recursive: boolean, filter: InputFilesFilter): void {
  const entries = fs.readdirSync(dirPath, {withFileTypes: true});
  for(const entry of entries) {
    const dirEnt = entry as fs.Dirent;
    const fullPath = path.join(dirPath, dirEnt.name);
    const relativePath = path.relative(params.rootDir, fullPath);
    if(dirEnt.isDirectory()) {
      if (!filter.includeDir(relativePath)) continue;
      if(recursive) {
        addDirectory(fullPath, params, recursive, filter);
      }
    }
    else if(dirEnt.isFile()) {
      addFile(fullPath, params, filter);
    } else {
      throw Error(`Unsupported dir entry '${entry.name}' in '${fullPath}'`);
    }
  }
}

async function updateLegacyComponent(component: LegacyPolymerComponent, params: UpdaterParameters) {
  const fullText = component.parsedFile.text;
  const {classDeclaration, componentRegistration, eventsComments} = PolymerClassFromLegacyComponentGenerator.generatePolymerV2ClassFromLegacyComponent(component);

  const polymerFuncCallStatement: ts.ExpressionStatement = tsUtils.assertNodeKind(component.polymerFuncCallExpr.parent, SyntaxKind.ExpressionStatement);
  const parentBlock: ts.Block = tsUtils.assertNodeKind(polymerFuncCallStatement.parent, SyntaxKind.Block);

  const updatedBlock = ts.getMutableClone(parentBlock);
  const index = parentBlock.statements.indexOf(polymerFuncCallStatement);
  if(index < 0) {
    throw new Error("Internal error! Couldn't find statement in its own parent");
  }

  const text = parentBlock.statements[index].getFullText();
  let classDeclarationWithComments = classDeclaration;
  const leadingComments: string[] = [];
  ts.forEachLeadingCommentRange(text, 0, (pos, end, kind, hasTrailingNewLine) => {
    classDeclarationWithComments = tsUtils.addReplacableCommentBeforeNode(classDeclarationWithComments, String(leadingComments.length));
    leadingComments.push(text.substring(pos, end));
  });

  const newStatements = Array.from(parentBlock.statements);
  newStatements.splice(index, 1, classDeclarationWithComments, componentRegistration);
  updatedBlock.statements = ts.createNodeArray(newStatements);

  const replaceResult = tsUtils.replaceNode(component.parsedFile, parentBlock, updatedBlock);
  const options: PrinterOptions = {
    removeComments: false,
    newLine: ts.NewLineKind.LineFeed,
  };
  const printer = ts.createPrinter(options);
  try {
    let newContent = tsUtils.applyNewLines(printer.printFile(replaceResult.transformed[0]));
    newContent = tsUtils.replaceComment(newContent, "eventsComments", "\n" + eventsComments.join("\n") + "\n");
    for(let i = 0; i < leadingComments.length; i++) {
      newContent = tsUtils.replaceComment(newContent, String(i), leadingComments[i]);
    }


    const afterReformat = restoreFormating(printer, component.parsedFile, newContent);
    const afterRemoveLongLines =
        afterReformat.replace("Polymer.LegacyDataMixin(Polymer.GestureEventListeners(Polymer.LegacyElementMixin(Polymer.Element)))", "Polymer.LegacyDataMixin(\nPolymer.GestureEventListeners(\nPolymer.LegacyElementMixin(\nPolymer.Element)))")
            .replace("Polymer.GestureEventListeners(Polymer.LegacyElementMixin(Polymer.Element))", "Polymer.GestureEventListeners(\nPolymer.LegacyElementMixin(\nPolymer.Element))");

    const originalComments = collectAllComments(component.parsedFile.getFullText());
    const newComments = collectAllComments(afterRemoveLongLines);
    const commentsProblems = [];
    for (const [text, count] of originalComments) {
      const newCount = newComments.get(text);
      if (!newCount) {
        commentsProblems.push(`Comment '${text}' is missing in the new content.`);
      }
      if (newCount != count) {
        commentsProblems.push(`Comment '${text}' appears ${newCount} times in the new file and ${count} times in the old file.`);
      }
    }

    for (const [text, newCount] of newComments) {
      if (!originalComments.has(text)) {
        commentsProblems.push(`Comment '${text}' appears only in the new content`);
      }
    }
    let commentsProblemStr = "";
    if(commentsProblems.length > 0) {
      commentsProblemStr = commentsProblems.join("-----------------------------\n");
      console.log(commentsProblemStr);
    }
    if(params.writeOutput) {
      const outDir = params.inplace ? params.rootDir : params.out;
      const fullOutPath = path.resolve(outDir, component.jsFile);
      const fullOutDir = path.dirname(fullOutPath);
      if(!fs.existsSync(fullOutDir)) {
        fs.mkdirSync(fullOutDir, { recursive: true, mode: fs.lstatSync(params.rootDir).mode });
      }
      let finalContent = afterRemoveLongLines;
      if(commentsProblemStr.length > 0) {
        finalContent = "//This file has the following problems with comments:\n" + commentsProblemStr + "\n" + finalContent
      }
      fs.writeFileSync(fullOutPath, finalContent);
    }
  }
  finally {
    replaceResult.dispose();
  }
}

function writeUpdatedFile(file: ts.SourceFile) {

}

function collectAllComments(content: string): Map<string, number> {
  const comments = tsUtils.collectAllComments(content);
  const result = new Map<string, number>();
  for(const comment of comments) {
    const count = result.get(comment);
    if(count) {
      result.set(comment, count + 1);
    }
    else {
      result.set(comment, 1);
    }
  }
  return result;
}

function restoreFormating(printer: ts.Printer, originalFile: ts.SourceFile, newContent: string): string {
  const newFile = ts.createSourceFile(originalFile.fileName, newContent, originalFile.languageVersion, true, ts.ScriptKind.JS);
  //ts.createSourceFile(jsFile, fs.readFileSync(jsFile).toString(), ts.ScriptTarget.ES2015, true);
  const textMap = new Map<ts.SyntaxKind, Map<string, Set<string>>>();
  const comments = new Set<string>();
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

async function main() {
  const params: UpdaterParameters = await getParams();
  if(params.jsFiles.size === 0) {
    console.log("No files found");
    return;
  }
  const componentParser = new PolymerComponentParser(params.rootDir, params.htmlFiles)
  for(const jsFile of params.jsFiles) {
    console.log(`Processing ${jsFile}`);
    const polymerComponent = await componentParser.parse(jsFile);
    if(!polymerComponent) {
      continue;
    }
    switch(polymerComponent.type) {
      case PolymerComponentType.Legacy:
        await updateLegacyComponent(polymerComponent, params);
        break;
      default:
        utils.unexpetedValue(polymerComponent.type);

    }
  }
}

interface CommandLineParameters {
  src: string[];
  recursive: boolean;
  excludes: string[];
  out: string;
  inplace: boolean;
  noOutput: boolean;
  rootDir: string;
}

async function getParams(): Promise<UpdaterParameters> {
  const parser = new CommandLineParser({
    src: CommandLineParser.createStringArrayOption("src", ".js file or folder to process", []),
    recursive: CommandLineParser.createBooleanOption("r", "process folder recursive", false),
    excludes: CommandLineParser.createStringArrayOption("exclude", "List of file prefixes to exclude. If relative file path starts with one of the prefixes, it will be excluded", []),
    out: CommandLineParser.createStringOption("out", "Output folder.", null),
    rootDir: CommandLineParser.createStringOption("root", "Root directory for src files", "/"),
    inplace: CommandLineParser.createBooleanOption("i", "Update files inplace", false),
    noOutput: CommandLineParser.createBooleanOption("noout", "Do everything, but do not write anything to files", false),
  });
  const commandLineParams: CommandLineParameters = parser.parse(process.argv) as CommandLineParameters;

  const params: UpdaterParameters = {
    htmlFiles: new Set(),
    jsFiles: new Set(),
    writeOutput: !commandLineParams.noOutput,
    inplace: commandLineParams.inplace,
    out: commandLineParams.out,
    rootDir: path.resolve(commandLineParams.rootDir)
  };

  if(params.writeOutput && !params.inplace && !params.out) {
    throw new Error("You should specify output directory (--out directory_name)");
  }

  const filter = new ExcludeFilesFilter(commandLineParams.excludes);
  for(const srcPath of commandLineParams.src) {
    const resolvedPath = path.resolve(params.rootDir, srcPath);
    if(fs.lstatSync(resolvedPath).isFile()) {
      addFile(resolvedPath, params, filter);
    } else {
      addDirectory(resolvedPath, params, commandLineParams.recursive, filter);
    }
  }
  return params;
}

class ExcludeFilesFilter implements InputFilesFilter {
  public constructor(private readonly excludes: string[]) {
  }
  includeDir(path: string): boolean {
    return this.excludes.every(exclude => !path.startsWith(exclude));
  }

  includeFile(path: string): boolean {
    return this.excludes.every(exclude => !path.startsWith(exclude));
  }
}

main().then(() => {
  process.exit(0);
}).catch(e => {
  console.error(e);
  process.exit(1);
});
