// Copyright (C) 2020 The Android Open Source Project
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

import * as fs from "fs";
import RewritingStream from "parse5-html-rewriting-stream";
import * as dom5 from "dom5";
import {HtmlFileUtils} from "./utils";
import {Node} from 'dom5';
import {readMultilineParamFile} from "../utils/command-line";
import { fail } from "../utils/common";
import * as path from "path";
import * as parse5 from "parse5";

async function main() {
  if (process.argv.length < 5) {
    console.info("Usage:\n\tnode script-tag-replacer.js root_dir html_files_param_file output_list_file_name\n");
    process.exit(1);
  }

  const root = path.normalize(process.argv[2]);
  const htmlFiles = readMultilineParamFile(process.argv[3]);
  const updater = new HtmlFileUpdater();
  for(const htmlFile of htmlFiles) {
    await updater.updateFile(root, htmlFile);
  }
  updater.writeListOfGeneratedHtmlFiles(process.argv[4]);
  const errors = updater.getErrors();
  if(errors.length > 0) {
    fail(errors.join("\n"));
  }
}

/** Update all links in HTML file based on redirects.
 * Additionally, update references to web-component-tester */
class HtmlFileUpdater {
  private static readonly Predicates = {
    isScriptWithSrcTag: (node: Node) => node.tagName === "script" && dom5.hasAttribute(node, "src"),
    isNonEmptyNode: (node: Node) => node.tagName || (node.data && node.data.trim() !== ""),
  };

  private readonly jsToGeneratedHtmlFileMap = new Map<string, string>();
  private readonly errors: string[] = [];

  public async updateFile(root: string, srcFile: string) {
    const srcFilePath = path.join(root, srcFile);
    const html = fs.readFileSync(srcFilePath, "utf-8");
    const readStream = fs.createReadStream(srcFilePath, {encoding: "utf-8"});
    const rewriterOutput = srcFilePath + ".tmp";
    const writeStream = fs.createWriteStream(rewriterOutput, {encoding: "utf-8"});
    const rewriter = new RewritingStream();
    (rewriter as any).tokenizer.preprocessor.bufferWaterline = Infinity;
    const initialErrorsCount = this.errors.length;

    rewriter.on("startTag", (tag: any) => {
     if (HtmlFileUpdater.Predicates.isScriptWithSrcTag(tag)) {
        const htmlImportPath = this.getHtmlImportPathForScript(tag, root, srcFile);
        if(htmlImportPath) {
          rewriter.emitRaw(`<link rel="import" href="${htmlImportPath}"/>`);
          // Ignore script close tag
          if(!tag.selfClosing) {
            rewriter.once("endTag", (tag: any) => {
            });
          }
          return;
        }
      }
      const location = tag.sourceCodeLocation;
      const raw = html.substring(location.startOffset, location.endOffset);
      rewriter.emitRaw(raw);
      return;
    });
    if(!srcFile.endsWith("_test.html") && !srcFile.endsWith("/gr-js-api-interface-element.html")) {
      // /gr-js-api-interface-element.html was created manually
      const html = fs.readFileSync(srcFilePath, {encoding: "utf-8"});
      const ast = parse5.parseFragment(html) as Node;
      this.checkJsFileSafeToConvert(ast, srcFile);
    }

    return new Promise<void>((resolve) => {
      writeStream.on("close", () => {
        writeStream.close();
        if(this.errors.length === initialErrorsCount) {
          fs.renameSync(rewriterOutput, srcFilePath);
        } else {
          // Errors during conversion - do not overwrite file
          fs.unlinkSync(rewriterOutput);
        }
        resolve();
      });
      readStream.pipe(rewriter).pipe(writeStream);
    });
  }

  /**
   * Ensure that the html file meets the following properties:
   * 1. The file either has one dom-module element or doesn't have dom-module at all
   * 2. There is exactly only one script tag inside dom-module and this tag is a direct child
   *    of dom-module (i.e. it is not a child of a child, etc..)
   * 4. The script tag inside dom-module refers to the appropriate script file
   *    (see the isAllowedSrcInDomModule method) or contains element declaration
   *    (code starts with "Polymer(")
   * 4. The dom-module is the last element in the html file.
   * @param ast - ast tree of an html file
   * @param srcFile - relative path to the html file
   *
   * Method fails if it is not safe to convert the html file with polymer-modulizer
   */
  private checkJsFileSafeToConvert(ast: Node, srcFile: string) {
    let domModuleFound: boolean = false;
    if(!ast.childNodes) {
      return;
    }
    for(const node of this.getNonEmptyChildNodes(ast)) {
      if(domModuleFound) {
        this.reportError(srcFile, `No content allowed after the <dom-module> tag`);
      }
      if(node.tagName === 'dom-module') {
        if(domModuleFound) {
          this.reportError(srcFile, `Only one <dom-module> is allowed per html file`);
        }
        this.checkDomModuleSafeToConvert(node, srcFile);
        domModuleFound = true;
      }
    }
  }

  private checkDomModuleSafeToConvert(domModuleNode: Node, srcFile: string) {
    let scriptTagFound = false;
    let templateTagFound = false;
    for(const node of this.getNonEmptyChildNodes(domModuleNode)) {
      if(node.tagName === "script") {
        if(scriptTagFound) {
          this.reportError(srcFile, `<dom-module> can contain only one script tag`);
        }
        scriptTagFound = true;
        if(HtmlFileUpdater.Predicates.isScriptWithSrcTag(node)) {
          const src = dom5.getAttribute(node, "src")!;
          if(!this.isAllowedSrcInDomModule(srcFile, src)) {
            this.reportError(srcFile, `Script src='${src}' is not valid inside dom-module. Move script tag outside of dom-module`);
          }
        } else {
          const script = dom5.getTextContent(node);
          if(!script.trimLeft().startsWith("Polymer(")) {
            this.reportError(srcFile, 'File can contain only one element script tag inside dom-module! Move all other scripts outside of dom-module');
          }
        }
      } else if(node.tagName === "template") {
        if (templateTagFound) {
          this.reportError(srcFile, `<dom-module> can contain only one template tag`);
        }
        if (node.attrs.length > 0) {
          this.reportError(srcFile, `<tempalte> must not have an attribute`)
        }
        templateTagFound = true;
      }
    }
  }

  private getNonEmptyChildNodes(node: Node): Node[] {
    if(!node.childNodes) {
      return [];
    }
    return node.childNodes.filter(HtmlFileUpdater.Predicates.isNonEmptyNode);
  }

  private isAllowedSrcInDomModule(srcFile: string, scriptSrc: string) {
    return path.basename(srcFile, ".html") + ".js" === scriptSrc;
  }
  private reportError(fileName: string, errorText: string): void {
    this.errors.push(`${fileName}: ${errorText}`);
  }

  public getErrors(): string[] {
    return this.errors;
  }

  private getHtmlImportPathForScript(node: Node, root: string, parentHtml: string): string | undefined {
    if(parentHtml.endsWith('/gr-js-api-interface-element.html')) {
      // This is a manually created file; skip it
      return undefined;
    }
    const ref = dom5.getAttribute(node, "src");
    if (!ref) {
      fail(`Internal error - ${node} in ${parentHtml} doesn't have attribute 'src'`);
    }
    if(ref.startsWith("/")) {
      return undefined;
    }
    const originalPath = path.join(root, HtmlFileUtils.getPathRelativeToRoot(parentHtml, ref));
    const pathRelativeToRoot = path.relative(root, originalPath);
    if(pathRelativeToRoot.startsWith('bower_components/') || pathRelativeToRoot.startsWith('node_modules/') || pathRelativeToRoot.startsWith('components')) {
      return undefined;
    }
    const parsedPath = path.parse(originalPath);
    parsedPath.base = path.basename(parsedPath.base, ".js") + ".html";
    const htmlFilePath = path.format(parsedPath);
    const parentHtmlFullPath = path.join(root, parentHtml);
    if(!this.jsToGeneratedHtmlFileMap.has(originalPath)) {
      if (fs.existsSync(htmlFilePath)) {
        if (parentHtmlFullPath !== htmlFilePath) {
          if(htmlFilePath.endsWith('/gr-etag-decorator.html')) {
            // Has reference from test to .js; ignore this error
            return undefined;
          }
          fail(`Can't create html for the ${originalPath} file. The html file already exists`);
        }
        return undefined;
      }
      const scriptFileName = path.basename(originalPath);
      fs.writeFileSync(htmlFilePath, `<script src="${scriptFileName}"></script>`);
      this.jsToGeneratedHtmlFileMap.set(originalPath, htmlFilePath)
    }
    return path.relative(path.dirname(parentHtmlFullPath), this.jsToGeneratedHtmlFileMap.get(originalPath)!);
  }

  public writeListOfGeneratedHtmlFiles(fileName: string) {
    const allFiles = new Array(...this.jsToGeneratedHtmlFileMap.values()).join('\n');
    fs.writeFileSync(fileName, allFiles);
  }
}

main();
