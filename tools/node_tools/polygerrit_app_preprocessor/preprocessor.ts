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
import * as path from "path";
import {Node} from 'dom5';
import {fail, unexpectedSwitchValue} from "../utils/common";
import {readMultilineParamFile} from "../utils/command-line";
import {
  HtmlSrcFilePath,
  JsSrcFilePath,
  HtmlTargetFilePath,
  JsTargetFilePath,
  FileUtils,
  FilePath
} from "../utils/file-utils";
import {
  AbsoluteWebPath,
  getRelativeImport,
  NodeModuleImportPath,
  SrcWebSite
} from "../utils/web-site-utils";

/**
 * update srcs by moving all scripts out of HTML files.
 * Input:
 *   input_output_html_param_file - list of file paths, each file path on a separate line
 *      The first 3 line contains the path to the first input HTML file and 2 output paths
 *         (for HTML and JS files)
 *      The second 3 line contains paths for the second HTML file, and so on.
 *
 *   input_output_js_param_file - similar to input_output_html_param_file, but has only 2 lines
 *      per file (input JS file and output JS file)
 *
 *   input_web_root_path - path (in filesystem) which should be treated as a web-site root path.

 *    For each HTML file it creates 2 output files - HTML and JS file.
 *      HTML file contains everything from HTML input file, except <script> tags.
 *      JS file contains (in the same order, as in original HTML):
 *      - inline javascript code from HTML file
 *      - each <script src = "path/to/file.js" > from HTML is converted to
 *           import 'path/to/output/file.js'
 *        statement. Such import statement run all side-effects in file.js (i.e. it run all    #
 *        global code).
 *      - each <link rel="import" href = "path/to/file.html"> adds to .js file as
 *           import 'path/to/output/file.html.js
 *        i.e. instead of html, the .js script imports
 *    Because output JS keeps the order of imports, all global variables are
 *    initialized in a correct order (this is important for gerrit; it is impossible to use
 *    AMD modules here).
 */

enum RefType {
  Html,
  InlineJS,
  JSFile
}

type LinkOrScript = HtmlFileRef | HtmlFileNodeModuleRef | JsFileReference | JsFileNodeModuleReference | InlineJS;

interface HtmlFileRef {
  type: RefType.Html,
  path: HtmlSrcFilePath;
  isNodeModule: false;
}

interface HtmlFileNodeModuleRef {
  type: RefType.Html,
  path: NodeModuleImportPath;
  isNodeModule: true;
}


function isHtmlFileRef(ref: LinkOrScript): ref is HtmlFileRef {
  return ref.type === RefType.Html;
}

interface JsFileReference {
  type: RefType.JSFile,
  path: JsSrcFilePath;
  isModule: boolean;
  isNodeModule: false;
}

interface JsFileNodeModuleReference {
  type: RefType.JSFile,
  path: NodeModuleImportPath;
  isModule: boolean;
  isNodeModule: true;
}

interface InlineJS {
  type: RefType.InlineJS,
  isModule: boolean;
  content: string;
}

interface HtmlOutputs {
  html: HtmlTargetFilePath;
  js: JsTargetFilePath;
}

interface JsOutputs {
  js: JsTargetFilePath;
}

type HtmlSrcToOutputMap = Map<HtmlSrcFilePath, HtmlOutputs>;
type JsSrcToOutputMap = Map<JsSrcFilePath, JsOutputs>;

interface HtmlFileInfo {
  src: HtmlSrcFilePath;
  ast: parse5.AST.Document;
  linksAndScripts: LinkOrScript[]
}

/** HtmlScriptAndLinksCollector walks through HTML file and collect
 * all links and inline scripts.
 */
class HtmlScriptAndLinksCollector {
  public constructor(private readonly webSite: SrcWebSite) {
  }
  public collect(src: HtmlSrcFilePath): HtmlFileInfo {
    const ast = HtmlScriptAndLinksCollector.getAst(src);
    const isHtmlImpport = (node: Node) => node.tagName == "link" &&
        dom5.getAttribute(node, "rel") == "import";
    const isScriptTag = (node: Node) => node.tagName == "script";

    const linksAndScripts: LinkOrScript[] = dom5
      .nodeWalkAll(ast as Node, (node) => isHtmlImpport(node) || isScriptTag(node))
      .map((node) => {
        if (isHtmlImpport(node)) {
          const href = dom5.getAttribute(node, "href");
          if (!href) {
            fail(`Tag <link rel="import...> in the file '${src}' doesn't have href attribute`);
          }
          if(this.webSite.isNodeModuleReference(href)) {
            return {
              type: RefType.Html,
              path: this.webSite.getNodeModuleImport(href),
              isNodeModule: true,
            }
          } else {
            return {
              type: RefType.Html,
              path: this.webSite.resolveHtmlImport(src, href),
              isNodeModule: false,
            }
          }
        } else {
          const isModule = dom5.getAttribute(node, "type") === "module";
          if (dom5.hasAttribute(node, "src")) {
            let srcPath = dom5.getAttribute(node, "src")!;
            if(this.webSite.isNodeModuleReference(srcPath)) {
              return {
                type: RefType.JSFile,
                isModule: isModule,
                path: this.webSite.getNodeModuleImport(srcPath),
                isNodeModule: true
              };
            } else {
              return {
                type: RefType.JSFile,
                isModule: isModule,
                path: this.webSite.resolveScriptSrc(src, srcPath),
                isNodeModule: false
              };
            }
          }
          return {
            type: RefType.InlineJS,
            isModule: isModule,
            content: dom5.getTextContent(node)
          };
        }
      });
    return {
      src,
      ast,
      linksAndScripts
    };
  };

  private static getAst(file: string): parse5.AST.Document {
    const html = fs.readFileSync(file, "utf-8");
    return parse5.parse(html, {locationInfo: true});
  }

}

/** Generate js files */
class ScriptGenerator {
  public constructor(private readonly pathMapper: SrcToTargetPathMapper) {
  }
  public generateFromJs(src: JsSrcFilePath) {
    FileUtils.copyFile(src, this.pathMapper.getJsTargetForJs(src));
  }

  public generateFromHtml(html: HtmlFileInfo) {
    const content: string[] = [];
    const src = html.src;
    const targetJsFile: JsTargetFilePath = this.pathMapper.getJsTargetForHtml(src);
    html.linksAndScripts.forEach((linkOrScript) => {
      switch (linkOrScript.type) {
        case RefType.Html:
          if(linkOrScript.isNodeModule) {
            const importPath = this.pathMapper.getJsTargetForHtmlInNodeModule(linkOrScript.path)
            content.push(`import '${importPath}';`);
          } else {
            const importPath = this.pathMapper.getJsTargetForHtml(linkOrScript.path);
            const htmlRelativePath = getRelativeImport(targetJsFile, importPath);
            content.push(`import '${htmlRelativePath}';`);
          }
          break;
        case RefType.JSFile:
          if(linkOrScript.isNodeModule) {
            content.push(`import '${linkOrScript.path}'`);
          } else {
            const importFromJs = this.pathMapper.getJsTargetForJs(linkOrScript.path);
            const scriptRelativePath = getRelativeImport(targetJsFile, importFromJs);
            content.push(`import '${scriptRelativePath}';`);
          }
          break;
        case RefType.InlineJS:
          content.push(linkOrScript.content);
          break;
        default:
          unexpectedSwitchValue(linkOrScript);
      }
    });
    FileUtils.writeContent(targetJsFile, content.join("\n"));
  }
}

/** Generate html files*/
class HtmlGenerator {
  constructor(private readonly pathMapper: SrcToTargetPathMapper) {
  }
  public generateFromHtml(html: HtmlFileInfo) {
    const ast = html.ast;
    dom5.nodeWalkAll(ast as Node, (node) => node.tagName === "script")
      .forEach((scriptNode) => dom5.remove(scriptNode));
    const newContent = parse5.serialize(ast);
    if(newContent.indexOf("<script") >= 0) {
      fail(`Has content ${html.src}`);
    }
    FileUtils.writeContent(this.pathMapper.getHtmlTargetForHtml(html.src), newContent);
  }
}

function readHtmlSrcToTargetMap(paramFile: string): HtmlSrcToOutputMap {
  const htmlSrcToTarget: HtmlSrcToOutputMap = new Map();
  const input = readMultilineParamFile(paramFile);
  for(let i = 0; i < input.length; i += 3) {
    const srcHtmlFile = path.resolve(input[i]) as HtmlSrcFilePath;
    const targetHtmlFile = path.resolve(input[i + 1]) as HtmlTargetFilePath;
    const targetJsFile = path.resolve(input[i + 2]) as JsTargetFilePath;
    htmlSrcToTarget.set(srcHtmlFile, {
      html: targetHtmlFile,
      js: targetJsFile
    });
  }
  return htmlSrcToTarget;
}

function readJsSrcToTargetMap(paramFile: string): JsSrcToOutputMap {
  const jsSrcToTarget: JsSrcToOutputMap = new Map();
  const input = readMultilineParamFile(paramFile);
  for(let i = 0; i < input.length; i += 2) {
    const srcJsFile = path.resolve(input[i]) as JsSrcFilePath;
    const targetJsFile = path.resolve(input[i + 1]) as JsTargetFilePath;
    jsSrcToTarget.set(srcJsFile as JsSrcFilePath, {
      js: targetJsFile as JsTargetFilePath
    });
  }
  return jsSrcToTarget;
}

class SrcToTargetPathMapper {
  public constructor(
      private readonly htmlSrcToTarget: HtmlSrcToOutputMap,
      private readonly jsSrcToTarget: JsSrcToOutputMap) {
  }
  public getJsTargetForHtmlInNodeModule(file: NodeModuleImportPath): JsTargetFilePath {
    return `${file}_gen.js` as JsTargetFilePath;
  }

  public getJsTargetForHtml(html: HtmlSrcFilePath): JsTargetFilePath {
    return this.getHtmlOutputs(html).js;
  }
  public getHtmlTargetForHtml(html: HtmlSrcFilePath): HtmlTargetFilePath {
    return this.getHtmlOutputs(html).html;
  }
  public getJsTargetForJs(js: JsSrcFilePath): JsTargetFilePath {
    return this.getJsOutputs(js).js;
  }

  private getHtmlOutputs(html: HtmlSrcFilePath): HtmlOutputs {
    if(!this.htmlSrcToTarget.has(html)) {
      fail(`There are no outputs for the file '${html}'`);
    }
    return this.htmlSrcToTarget.get(html)!;
  }
  private getJsOutputs(js: JsSrcFilePath): JsOutputs {
    if(!this.jsSrcToTarget.has(js)) {
      fail(`There are no outputs for the file '${js}'`);
    }
    return this.jsSrcToTarget.get(js)!;
  }
}

function main() {
  if(process.argv.length < 5) {
    const execFileName = path.basename(__filename);
    fail(`Usage:\nnode ${execFileName} input_web_root_path input_output_html_param_file input_output_js_param_file\n`);
  }

  const srcWebSite = new SrcWebSite(path.resolve(process.argv[2]) as FilePath);
  const htmlSrcToTarget: HtmlSrcToOutputMap = readHtmlSrcToTargetMap(process.argv[3]);
  const jsSrcToTarget: JsSrcToOutputMap = readJsSrcToTargetMap(process.argv[4]);
  const pathMapper = new SrcToTargetPathMapper(htmlSrcToTarget, jsSrcToTarget);

  const scriptGenerator = new ScriptGenerator(pathMapper);
  const htmlGenerator = new HtmlGenerator(pathMapper);
  const scriptAndLinksCollector = new HtmlScriptAndLinksCollector(srcWebSite);

  htmlSrcToTarget.forEach((targets, src) => {
    const htmlFileInfo = scriptAndLinksCollector.collect(src);
    scriptGenerator.generateFromHtml(htmlFileInfo);
    htmlGenerator.generateFromHtml(htmlFileInfo);
  });
  jsSrcToTarget.forEach((targets, src) => {
    scriptGenerator.generateFromJs(src);
  });
}

main();
