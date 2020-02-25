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
}

/** Update all links in HTML file based on redirects.
 * Additionally, update references to web-component-tester */
class HtmlFileUpdater {
  private static readonly Predicates = {
    isScriptWithSrcTag: (node: Node) => node.tagName === "script" && dom5.hasAttribute(node, "src"),
  };

  private readonly jsToGeneratedHtmlFileMap = new Map<string, string>();

  public async updateFile(root: string, srcFile: string) {
    const srcFilePath = path.join(root, srcFile);
    const html = fs.readFileSync(srcFilePath, "utf-8");
    const readStream = fs.createReadStream(srcFilePath, {encoding: "utf-8"});
    const rewriterOutput = srcFilePath + ".tmp";
    const writeStream = fs.createWriteStream(rewriterOutput, {encoding: "utf-8"});
    const rewriter = new RewritingStream();
    (rewriter as any).tokenizer.preprocessor.bufferWaterline = Infinity;
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
    return new Promise<void>((resolve) => {
      writeStream.on("close", () => {
        writeStream.close();
        fs.renameSync(rewriterOutput, srcFilePath);
        resolve();
      });
      readStream.pipe(rewriter).pipe(writeStream);
    });
  }

  private getHtmlImportPathForScript(node: Node, root: string, parentHtml: string): string | undefined {
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
