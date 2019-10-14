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

import {LegacyPolymerComponent} from './polymerComponentParser';
import * as ts from 'typescript';
import * as codeUtils from '../utils/codeUtils';
import * as path from "path";
import * as fs from "fs";
import {LegacyPolymerFuncReplaceResult} from './legacyPolymerFuncReplacer';
import {CommentsParser} from '../utils/commentsParser';

export interface UpdatedFileWriterParameters {
  out: string;
  inplace: boolean;
  writeOutput: boolean;
  rootDir: string;
}

interface Replacement {
  start: number;
  length: number;
  newText: string;
}

export class UpdatedFileWriter {
  public constructor(private readonly component: LegacyPolymerComponent, private readonly params: UpdatedFileWriterParameters) {
  }

  public write(replaceResult: LegacyPolymerFuncReplaceResult, eventsComments: string[]) {
    const options: ts.PrinterOptions = {
      removeComments: false,
      newLine: ts.NewLineKind.LineFeed,
    };
    const printer = ts.createPrinter(options);
    let newContent = codeUtils.applyNewLines(printer.printFile(replaceResult.file));
    //ts printer doesn't keep original formatting of the file (spacing, new lines, comments, etc...).
    //The following code tries restore original formatting

    //newContent = tsUtils.replaceComment(newContent, "eventsComments", "\n" + eventsComments.join("\n") + "\n");
    newContent = this.restoreEventsComments(newContent, eventsComments);
    newContent = this.restoreLeadingComments(newContent, replaceResult.leadingComments);
    newContent = this.restoreFormating(printer, newContent);
    newContent = this.splitLongLines(newContent);
    newContent = this.addCommentsWarnings(newContent);

    if (this.params.writeOutput) {
      const outDir = this.params.inplace ? this.params.rootDir : this.params.out;
      const fullOutPath = path.resolve(outDir, this.component.jsFile);
      const fullOutDir = path.dirname(fullOutPath);
      if (!fs.existsSync(fullOutDir)) {
        fs.mkdirSync(fullOutDir, {
          recursive: true,
          mode: fs.lstatSync(this.params.rootDir).mode
        });
      }
      fs.writeFileSync(fullOutPath, newContent);
    }
  }

  private restoreEventsComments(content: string, eventsComments: string[]): string {
    return codeUtils.replaceComment(content, "eventsComments", "\n" + eventsComments.join("\n") + "\n");
  }

  private restoreLeadingComments(content: string, leadingComments: string[]): string {
    return leadingComments.reduce(
        (newContent, comment, commentIndex) =>
            codeUtils.replaceComment(newContent, String(commentIndex), comment),
        content);
  }

  private restoreFormating(printer: ts.Printer, newContent: string): string {
    const originalFile = this.component.parsedFile;
    const newFile = ts.createSourceFile(originalFile.fileName, newContent, originalFile.languageVersion, true, ts.ScriptKind.JS);
    const textMap = new Map<ts.SyntaxKind, Map<string, Set<string>>>();
    const comments = new Set<string>();
    this.collectAllStrings(printer, originalFile, textMap);

    const replacements: Replacement[] = [];
    this.collectReplacements(printer, newFile, textMap, replacements);
    replacements.sort((a, b) => b.start - a.start);
    let result = newFile.getFullText();
    let prevReplacement: Replacement | null = null;
    for (const replacement of replacements) {
      if (prevReplacement) {
        if (replacement.start + replacement.length > prevReplacement.start) {
          throw new Error('Internal error! Replacements must not intersect');
        }
      }
      result = result.substring(0, replacement.start) + replacement.newText + result.substring(replacement.start + replacement.length);
      prevReplacement = replacement;
    }
    return result;
  }

  private splitLongLines(content: string): string {
    return content
        .replace(
            "Polymer.LegacyDataMixin(Polymer.GestureEventListeners(Polymer.LegacyElementMixin(Polymer.Element)))",
            "Polymer.LegacyDataMixin(\nPolymer.GestureEventListeners(\nPolymer.LegacyElementMixin(\nPolymer.Element)))")
        .replace(
            "Polymer.GestureEventListeners(Polymer.LegacyElementMixin(Polymer.Element))",
            "Polymer.GestureEventListeners(\nPolymer.LegacyElementMixin(\nPolymer.Element))");
  }

  private addCommentsWarnings(newContent: string): string {
    const originalComments = this.collectAllComments(this.component.parsedFile.getFullText());
    const newComments = this.collectAllComments(newContent);
    const commentsWarnings = [];
    for (const [text, count] of originalComments) {
      const newCount = newComments.get(text);
      if (!newCount) {
        commentsWarnings.push(`Comment '${text}' is missing in the new content.`);
      }
      if (newCount != count) {
        commentsWarnings.push(`Comment '${text}' appears ${newCount} times in the new file and ${count} times in the old file.`);
      }
    }

    for (const [text, newCount] of newComments) {
      if (!originalComments.has(text)) {
        commentsWarnings.push(`Comment '${text}' appears only in the new content`);
      }
    }
    if (commentsWarnings.length === 0) {
      return newContent;
    }
    let commentsProblemStr = "";
    if (commentsWarnings.length > 0) {
      commentsProblemStr = commentsWarnings.join("-----------------------------\n");
      console.log(commentsProblemStr);
    }

    return "//This file has the following problems with comments:\n" + commentsProblemStr + "\n" + newContent;

  }

  private collectAllComments(content: string): Map<string, number> {
    const comments = CommentsParser.collectAllComments(content);
    const result = new Map<string, number>();
    for (const comment of comments) {
      const count = result.get(comment);
      if (count) {
        result.set(comment, count + 1);
      } else {
        result.set(comment, 1);
      }
    }
    return result;
  }

  private collectAllStrings(printer: ts.Printer, node: ts.Node, map: Map<ts.SyntaxKind, Map<string, Set<string>>>) {
    const formattedText = printer.printNode(ts.EmitHint.Unspecified, node, node.getSourceFile())
    const originalText = node.getFullText();
    this.addIfNotExists(map, node.kind, formattedText, originalText);
    ts.forEachChild(node, child => this.collectAllStrings(printer, child, map));
  }

  private collectReplacements(printer: ts.Printer, node: ts.Node, map: Map<ts.SyntaxKind, Map<string, Set<string>>>, replacements: Replacement[]) {
    if(node.kind === ts.SyntaxKind.ThisKeyword || node.kind === ts.SyntaxKind.Identifier || node.kind === ts.SyntaxKind.StringLiteral || node.kind === ts.SyntaxKind.NumericLiteral) {
      return;
    }
    const replacement = this.getReplacement(printer, node, map);
    if(replacement) {
      replacements.push(replacement);
      return;
    }
    ts.forEachChild(node, child => this.collectReplacements(printer, child, map, replacements));
  }

  private addIfNotExists(map: Map<ts.SyntaxKind, Map<string, Set<string>>>, kind: ts.SyntaxKind, formattedText: string, originalText: string) {
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

  private getReplacement(printer: ts.Printer, node: ts.Node, map: Map<ts.SyntaxKind, Map<string, Set<string>>>): Replacement | undefined {
    const replacementsForKind = map.get(node.kind);
    if(!replacementsForKind) {
      return;
    }
    // Use printer instead of getFullText to "isolate" node content.
    // node.getFullText returns text with indents from the original file.
    const newText = printer.printNode(ts.EmitHint.Unspecified, node, node.getSourceFile());
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

}