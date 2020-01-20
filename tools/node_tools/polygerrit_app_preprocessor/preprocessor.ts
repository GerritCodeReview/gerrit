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
import {fail, FileUtils, DirectoryCopier, unexpectedSwitchValue, DirEntryPredicate} from "./utils";
import {readMultilineParamFile} from "../utils/command-line";

enum RefType {
  Html,
  InlineJS,
  JSFile
}

type LinkOrScript = HtmlFileRef | JsFileReference | InlineJS;

interface HtmlFileRef {
  type: RefType.Html,
  path: string;
}
function isHtmlFileRef(ref: LinkOrScript): ref is HtmlFileRef {
  return ref.type === RefType.Html;
}

interface JsFileReference {
  type: RefType.JSFile,
  path: string;
  isModule: boolean;
  isNodeModule: boolean;
}
interface InlineJS {
  type: RefType.InlineJS,
  isModule: boolean;
  content: string;
}

interface HtmlFileInfo {
  ast: parse5.AST.Document;
  linksAndScripts: LinkOrScript[];
}

type HtmlSrcFilePath = string;
type HtmlTargetFilePath = string;
type JsTargetFilePath = string;

interface Targets {
  html: HtmlTargetFilePath;
  js: JsTargetFilePath;
}

type SrcToTargetsMap = Map<HtmlSrcFilePath, Targets>;


class HtmlScriptAndLinksCollector {
  public static collect(src: HtmlSrcFilePath): HtmlFileInfo {
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
          return {
            type: RefType.Html,
            path: FileUtils.getPathRelativeToRoot(src, href),
          }
        } else {
          const isModule = dom5.getAttribute(node, "type") === "module";
          if (dom5.hasAttribute(node, "src")) {
            let srcPath = dom5.getAttribute(node, "src")!;
            const originalPath = FileUtils.getPathRelativeToRoot(src, srcPath);
            const nodeModulesPrefix = 'node_modules/';
            const isNodeModule = originalPath.startsWith(nodeModulesPrefix);
            const path = isNodeModule ? originalPath.substring(nodeModulesPrefix.length) : originalPath;
            return {
              type: RefType.JSFile,
              isModule: isModule,
              path: path,
              isNodeModule: isNodeModule,
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
      ast,
      linksAndScripts
    };
  };

  private static getAst(file: string): parse5.AST.Document {
    const html = fs.readFileSync(file, "utf-8");
    return parse5.parse(html, {locationInfo: true});
  }

}

abstract class RecursiveGenerator {
  private generatedSet: Set<string> = new Set();
  protected constructor(private readonly srcRootDir: string,
                     private readonly outputRootDir: string,
                     private readonly refMap: ReferenceMap) {
    this.generatedSet = new Set();
  }

  public generateRecursively(htmlPathRelativeToRoot: string) {
    if(this.generatedSet.has(htmlPathRelativeToRoot)) {
      return;
    }
    this.generatedSet.add(htmlPathRelativeToRoot);
    const data = this.refMap.get(htmlPathRelativeToRoot);
    if(!data) {
      fail(`Can't get data for '${htmlPathRelativeToRoot}'`);
    }
    this.generateFor(htmlPathRelativeToRoot, data);
    data.refs.filter(isHtmlFileRef).forEach((htmlRef) => this.generateRecursively(htmlRef.path));
  }

  protected getImportPathRelativeToParent(parentFile: string, importPath: string): string {
    return FileUtils.getImportPathRelativeToParent(this.outputRootDir, parentFile, importPath);
  }

  protected outFileExists(file: string): boolean {
    return fs.existsSync(path.join(this.outputRootDir, file));
  }

  protected saveFile(outFile: string, content: string) {
    FileUtils.saveFile(this.outputRootDir, outFile, content);
  }

  protected abstract generateFor(htmlPathRelativeToRoot: string, htmlFileReferences: HtmlFileReferences): void;
}

class ScriptGenerator extends RecursiveGenerator{
  public constructor(private readonly srcToTargets: SrcToTargetsMap) {
    super(srcRootDir, outputRootDir, refMap);
  }

  public generate(src: HtmlSrcFilePath, linksAndScripts: LinkOrScript[]) {
    const content: string[] = [];
    if(!this.srcToTargets.has(src)) {
      fail(`The file ${src} doesn't exists in input files`);
    }
    const targets = this.srcToTargets.get(src)!;
    const targetJsFile: JsTargetFilePath = targets.js;
    linksAndScripts.forEach((linkOrScript) => {
      switch (linkOrScript.type) {
        case RefType.Html:
          const importPath = this.srcToTargets.get(linkOrScript.path).js;
          const htmlRelativePath = this.getImportPathRelativeToParent(targetJsFile, importPath);
          content.push(`import '${htmlRelativePath}'`);
          break;
        case RefType.JSFile:
          if(linkOrScript.isNodeModule) {
            content.push(`import '${linkOrScript.path}'`);
          } else {
            if(!this.outFileExists(linkOrScript.path)) {
              throw new Error(`Attempts to import non-existing file '${linkOrScript.path}' from ${htmlPathRelativeToRoot}`);
            }
            const scriptRelativePath = this.getImportPathRelativeToParent(
                targetJsFile, linkOrScript.path);
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
    this.saveFile(targetJsFile, content.join("\n"));
  }
}

class HtmlGenerator extends RecursiveGenerator{
  constructor(srcRootDir: string, outputRootDir: string, refMap: ReferenceMap, private readonly htmlProcessor: (htmlPathRelativeToRoot: string, ast: parse5.AST.Document) => void) {
    super(srcRootDir, outputRootDir, refMap);
    this.htmlProcessor = htmlProcessor;
  }
  generateFor(htmlPathRelativeToRoot: string, htmlFileReferences: HtmlFileReferences) {
    this.htmlProcessor(htmlPathRelativeToRoot, htmlFileReferences.ast);
    const newContent = parse5.serialize(htmlFileReferences.ast);
    this.saveFile(htmlPathRelativeToRoot, newContent);
  }
}

function main() {
  if(process.argv.length < 3) {
    const execFileName = path.basename(__filename);
    fail(`Usage:\nnode ${execFileName} input_output_param_files\n`);
  }
  const input = readMultilineParamFile(process.argv[2]);
  const srcToTargets: SrcToTargetsMap = new Map();
  for(let i = 0; i < input.length; i += 3) {
    const srcHtmlFile = input[i];
    const targetHtmlFile = input[i + 1];
    const targetJsFile = input[i + 2];
    srcToTargets.set(srcHtmlFile, {
      html: targetHtmlFile,
      js: targetJsFile
    });
  }
  const scriptGenerator = new ScriptGenerator(srcToTargets);
  const htmlGenerator = new HtmlGenerator(srcToTargets);

  srcToTargets.forEach((targets, src) => {
    const linksAndScripts = HtmlScriptAndLinksCollector.collect(src);
    scriptGenerator.generate(linksAndScripts, targets.js);
    htmlGenerator.generate(src, target.html);

  })
}



const rootDir = path.resolve(process.cwd(), process.argv[2]);
const outputRootDir = path.resolve(process.cwd(), process.argv[3]);
const entryPoint = process.argv[4];

const refMap = RefMapCollector.buildReferencesMap(rootDir, [entryPoint]);

const directoryCopier = new DirectoryCopier(rootDir, outputRootDir);
const isJsFile: DirEntryPredicate = (_: fs.Dirent, dirEntryPath: string) => path.extname(dirEntryPath) === ".js";
directoryCopier.copyRecursively("./", isJsFile);

const scriptGenerator = new ScriptGenerator(rootDir, outputRootDir, refMap);
scriptGenerator.generateRecursively(entryPoint);

const htmlProcessor = (filePath: string, ast: parse5.AST.Document) => {
  dom5.nodeWalkAll(ast as Node, (node) => node.tagName === "script")
  .forEach((scriptNode) => dom5.remove(scriptNode));
};

const htmlGenerator = new HtmlGenerator(rootDir, outputRootDir, refMap, htmlProcessor);
htmlGenerator.generateRecursively(entryPoint);
