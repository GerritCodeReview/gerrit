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
import * as parse5 from "parse5";
import * as dom5 from "dom5";
import {HtmlFileUtils, RedirectsResolver} from "./utils";
import {Node} from 'dom5';
import {readMultilineParamFile} from "../utils/command-line";
import {FileUtils} from "../utils/file-utils";
import { fail } from "../utils/common";
import {JSONRedirects} from "./redirects";

function main() {
  console.log(process.cwd());

  if (process.argv.length < 4) {
    console.info("Usage:\n\tnode links_updater.js input_output_param_files redirectFile.json\n");
    process.exit(1);
  }

  const jsonRedirects: JSONRedirects = JSON.parse(fs.readFileSync(process.argv[3], {encoding: "utf-8"}));
  const redirectsResolver = new RedirectsResolver(jsonRedirects.redirects);

  const input = readMultilineParamFile(process.argv[2]);
  const updater = new HtmlFileUpdater(redirectsResolver);
  for(let i = 0; i < input.length; i += 2) {
    const srcFile = input[i];
    const targetFile = input[i + 1];
    updater.updateFile(srcFile, targetFile);
  }
}

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

  public updateFile(srcFile: string, targetFile: string) {
    const html = fs.readFileSync(srcFile, "utf-8");
    const ast = parse5.parseFragment(html, {locationInfo: true}) as Node;


    const webComponentTesterImportNode = dom5.query(ast, HtmlFileUpdater.Predicates.isWebComponentTesterImport);
    if(webComponentTesterImportNode) {
      dom5.setAttribute(webComponentTesterImportNode,  "src", "/components/wct-browser-legacy/browser.js");
    }

    const updateHtmlImportHref = (htmlImportNode: Node) => this.updateRefAttribute(htmlImportNode, srcFile, "href");
    dom5.queryAll(ast, HtmlFileUpdater.Predicates.isHtmlImport).forEach(updateHtmlImportHref);

    const updateScriptSrc = (scriptTagNode: Node) => this.updateRefAttribute(scriptTagNode, srcFile, "src");
    dom5.queryAll(ast, HtmlFileUpdater.Predicates.isScriptWithSrcTag).forEach(updateScriptSrc);

    const newContent = parse5.serialize(ast);
    FileUtils.writeContent(targetFile, newContent);
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
    if(!ref) {
      fail(`Internal error - ${node} in ${parentHtml} doesn't have attribute ${attributeName}`);
    }
    const newRef = this.getResolvedPath(parentHtml, ref);
    if(newRef === ref) {
      return;
    }
    dom5.setAttribute(node,  attributeName, newRef);
  }
}

main();
