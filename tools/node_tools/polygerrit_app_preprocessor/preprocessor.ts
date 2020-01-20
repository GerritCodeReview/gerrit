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
import {SrcWebSite} from "../utils/web-site-utils";

enum RefType {
  Html,
  InlineJS,
  JSFile
}

type LinkOrScript = HtmlFileRef | JsFileReference | InlineJS;

interface HtmlFileRef {
  type: RefType.Html,
  path: HtmlSrcFilePath;
}
function isHtmlFileRef(ref: LinkOrScript): ref is HtmlFileRef {
  return ref.type === RefType.Html;
}

interface JsFileReference {
  type: RefType.JSFile,
  path: JsSrcFilePath;
  isModule: boolean;
  isNodeModule: boolean;
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
          const hrefAbsPath = this.webSite.resolveHtmlImport(src, href);
          return {
            type: RefType.Html,
            path: this.webSite.resolveHtmlImport(src, href),
          }
        } else {
          const isModule = dom5.getAttribute(node, "type") === "module";
          if (dom5.hasAttribute(node, "src")) {
            let srcPath = dom5.getAttribute(node, "src")!;
            const originalPath = this.webSite.resolveScriptSrc(src, srcPath);
            const isNodeModule = this.webSite.isNodeModule(originalPath);
            return {
              type: RefType.JSFile,
              isModule: isModule,
              path: originalPath,
              isNodeModule: this.webSite.isNodeModule(originalPath)
            };
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
          const importPath = this.pathMapper.getJsTargetForHtml(linkOrScript.path);
          const htmlRelativePath = path.relative(targetJsFile, importPath);
          content.push(`import '${htmlRelativePath}'`);
          break;
        case RefType.JSFile:
          if(linkOrScript.isNodeModule) {
            content.push(`import '${linkOrScript.path}'`);
          } else {
            const importFromJs = this.pathMapper.getJsTargetForJs(linkOrScript.path);
            const scriptRelativePath = path.relative(targetJsFile, importFromJs);
            content.push(`import '${scriptRelativePath}'`);
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

class HtmlGenerator {
  constructor(private readonly pathMapper: SrcToTargetPathMapper) {
  }
  public generateFromHtml(html: HtmlFileInfo) {
    const ast = html.ast;
    dom5.nodeWalkAll(ast as Node, (node) => node.tagName === "script")
      .forEach((scriptNode) => dom5.remove(scriptNode));
    const newContent = parse5.serialize(ast);
    FileUtils.writeContent(this.pathMapper.getHtmlTargetForHtml(html.src), newContent);
  }
}

function readHtmlSrcToTargetMap(paramFile: string): HtmlSrcToOutputMap {
  const htmlSrcToTarget: HtmlSrcToOutputMap = new Map();
  const input = readMultilineParamFile(paramFile);
  for(let i = 0; i < input.length; i += 3) {
    const srcHtmlFile = input[i] as HtmlSrcFilePath;
    const targetHtmlFile = input[i + 1] as HtmlTargetFilePath;
    const targetJsFile = input[i + 2] as JsTargetFilePath;
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
  for(let i = 0; i < input.length; i += 1) {
    const srcJsFile = input[i] as JsSrcFilePath;
    const targetJsFile = input[i + 1] as JsTargetFilePath;
    jsSrcToTarget.set(srcJsFile, {
      js: targetJsFile
    });
  }
  return jsSrcToTarget;
}

class SrcToTargetPathMapper {
  public constructor(
      private readonly htmlSrcToTarget: HtmlSrcToOutputMap,
      private readonly jsSrcToTarget: JsSrcToOutputMap) {
  }
  public getJsTargetForHtml(html: HtmlSrcFilePath): JsTargetFilePath {
    return this.getHtmlOutputs(html).js;
  }
  public getHtmlTargetForHtml(html: HtmlSrcFilePath): JsTargetFilePath {
    return this.getHtmlOutputs(html).js;
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

  const srcWebSite = new SrcWebSite(process.argv[2] as FilePath);
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
