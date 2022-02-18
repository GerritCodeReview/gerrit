/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as fs from "fs";
import RewritingStream from "parse5-html-rewriting-stream";
import * as dom5 from "dom5";
import {HtmlFileUtils, RedirectsResolver} from "./utils";
import {Node} from 'dom5';
import {readMultilineParamFile} from "../utils/command-line";
import { fail } from "../utils/common";
import {JSONRedirects} from "./redirects";

/** Update links in HTML file
 * input_output_param_files - is a list of paths; each path is placed on a separate line
 *   The first line is the path to a first input file (relative to process working directory)
 *   The second line is the path to the output file  (relative to process working directory)
 *   The next 2 lines describe the second file and so on.
 * redirectFile.json describes how to update links (see {@link JSONRedirects} for exact format)
 * Additionaly, update some test links (related to web-component-tester)
 */

async function main() {
  if (process.argv.length < 4) {
    console.info("Usage:\n\tnode links_updater.js input_output_param_files redirectFile.json\n");
    process.exit(1);
  }

  const jsonRedirects: JSONRedirects = JSON.parse(fs.readFileSync(process.argv[3], {encoding: "utf-8"})) as JSONRedirects;
  const redirectsResolver = new RedirectsResolver(jsonRedirects.redirects);

  const input = readMultilineParamFile(process.argv[2]);
  const updater = new HtmlFileUpdater(redirectsResolver);
  for(let i = 0; i < input.length; i += 2) {
    const srcFile = input[i];
    const targetFile = input[i + 1];
    await updater.updateFile(srcFile, targetFile);
  }
}

/** Update all links in HTML file based on redirects.
 * Additionally, update references to web-component-tester */
class HtmlFileUpdater {
  private static readonly Predicates = {
    isScriptWithSrcTag: (node: Node) => node.tagName === "script" && dom5.hasAttribute(node, "src"),

    isWebComponentTesterImport: (node: Node) => HtmlFileUpdater.Predicates.isScriptWithSrcTag(node) &&
        dom5.getAttribute(node, "src")!.endsWith("/bower_components/web-component-tester/browser.js"),

    isHtmlImport: (node: Node) => node.tagName === "link" && dom5.getAttribute(node, "rel") === "import" &&
        dom5.hasAttribute(node, "href")
  };

  public constructor(private readonly redirectsResolver: RedirectsResolver) {
  }

  public async updateFile(srcFile: string, targetFile: string) {
    const html = fs.readFileSync(srcFile, "utf-8");
    const readStream = fs.createReadStream(srcFile, {encoding: "utf-8"});
    const rewriterOutput = srcFile === targetFile ? targetFile + ".tmp" : targetFile;
    const writeStream = fs.createWriteStream(rewriterOutput, {encoding: "utf-8"});
    const rewriter = new RewritingStream();
    (rewriter as any).tokenizer.preprocessor.bufferWaterline = Infinity;
    rewriter.on("startTag", (tag: any) => {
      if (HtmlFileUpdater.Predicates.isWebComponentTesterImport(tag)) {
        dom5.setAttribute(tag, "src", "/components/wct-browser-legacy/browser.js");
      } else if (HtmlFileUpdater.Predicates.isHtmlImport(tag)) {
        this.updateRefAttribute(tag, srcFile, "href");
      } else if (HtmlFileUpdater.Predicates.isScriptWithSrcTag(tag)) {
        this.updateRefAttribute(tag, srcFile, "src");
      } else {
        const location = tag.sourceCodeLocation;
        const raw = html.substring(location.startOffset, location.endOffset);
        rewriter.emitRaw(raw);
        return;
      }
      rewriter.emitStartTag(tag);
    });
    return new Promise<void>((resolve, reject) => {
      writeStream.on("close", () => {
        writeStream.close();
        if (rewriterOutput !== targetFile) {
          fs.renameSync(rewriterOutput, targetFile);
        }
        resolve();
      });
      readStream.pipe(rewriter).pipe(writeStream);
    });
  }

  private getResolvedPath(parentHtml: string, href: string) {
    const originalPath = '/' + HtmlFileUtils.getPathRelativeToRoot(parentHtml, href);

    const resolvedInfo = this.redirectsResolver.resolve(originalPath, true);
    if (!resolvedInfo.insideNodeModules && resolvedInfo.target === originalPath) {
      return href;
    }
    if (resolvedInfo.insideNodeModules) {
      return '/node_modules/' + resolvedInfo.target;
    }
    if (href.startsWith('/')) {
      return resolvedInfo.target;
    }
    return HtmlFileUtils.getPathRelativeToRoot(parentHtml, resolvedInfo.target);
  }

  private updateRefAttribute(node: Node, parentHtml: string, attributeName: string) {
    const ref = dom5.getAttribute(node, attributeName);
    if (!ref) {
      fail(`Internal error - ${node} in ${parentHtml} doesn't have attribute ${attributeName}`);
    }
    const newRef = this.getResolvedPath(parentHtml, ref);
    if (newRef === ref) {
      return;
    }
    dom5.setAttribute(node, attributeName, newRef);
  }
}

main();
