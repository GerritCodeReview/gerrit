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

enum RefType {
  Html,
  InlineJS,
  JSFile
}

type Reference = HtmlFileRef | JsFileReference | InlineJS;

interface HtmlFileRef {
  type: RefType.Html,
  path: string;
}
function isHtmlFileRef(ref: Reference): ref is HtmlFileRef {
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

interface HtmlFileReferences {
  ast: parse5.AST.Document;
  refs: Reference[];
}

type ReferenceMap = Map<string, HtmlFileReferences>;

class RefMapCollector {
  public static buildReferencesMap(rootDir: string, entryPoints: string[]): ReferenceMap {
    const collector = new RefMapCollector(rootDir);
    entryPoints.forEach(entryPoint => collector.addRefDataRecursively(entryPoint));
    return collector.refMap;
  }

  private refMap: ReferenceMap = new Map();
  private constructor(private rootDir: string) {
  }

  private addRefDataRecursively(htmlPathRelativeToRoot: string) {
    if(this.refMap.has(htmlPathRelativeToRoot)) {
      return;
    }
    const ast = this.getAst(htmlPathRelativeToRoot);
    const refs: Reference[] = [];
    dom5.nodeWalkAll(ast as Node, () => true).forEach((node) => {
      if(node.tagName === "link" && dom5.getAttribute(node, "rel") == "import") {
        const href = dom5.getAttribute(node, "href");
        if(!href) {
          fail(`Tag <link rel="import...> in the file '${path.join(this.rootDir, htmlPathRelativeToRoot)}' doesn't have href attribute`);
        }
        refs.push({
          type: RefType.Html,
          path: FileUtils.getPathRelativeToRoot(htmlPathRelativeToRoot, href),
        });
        return;
      }
      if(node.tagName === "script") {
        const isModule = dom5.getAttribute(node, "type") === "module";
        if(dom5.hasAttribute(node, "src")) {
          let srcPath = dom5.getAttribute(node, "src")!;
          const originalPath = FileUtils.getPathRelativeToRoot(htmlPathRelativeToRoot, srcPath);
          const nodeModulesPrefix = 'node_modules/';
          const isNodeModule = originalPath.startsWith(nodeModulesPrefix);
          const path = isNodeModule ? originalPath.substring(nodeModulesPrefix.length) : originalPath;
          refs.push({
            type: RefType.JSFile,
            isModule: isModule,
            path: path,
            isNodeModule: isNodeModule,
          });
          return;
        }
        refs.push({
          type: RefType.InlineJS,
          isModule: isModule,
          content: dom5.getTextContent(node)
        });
      }
    });
    this.refMap.set(htmlPathRelativeToRoot, {
      refs: refs,
      ast: ast
    });
    refs.filter(isHtmlFileRef).forEach((htmlRef) => this.addRefDataRecursively(htmlRef.path));
  };

  private getAst(file: string): parse5.AST.Document {
    const filePath = path.join(this.rootDir, file);
    const html = fs.readFileSync(filePath, "utf-8");
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
  public constructor(srcRootDir: string, outputRootDir: string, refMap: ReferenceMap) {
    super(srcRootDir, outputRootDir, refMap);
  }

  protected generateFor(htmlPathRelativeToRoot: string, data: HtmlFileReferences) {
    const imports: string[] = [];
    const jsFileName = htmlPathRelativeToRoot + "_generated.js";
    data.refs.forEach((ref) => {
      switch (ref.type) {
        case RefType.Html:
          const importPath = ref.path + "_generated" + ".js";
          const htmlRelativePath = this.getImportPathRelativeToParent(jsFileName, importPath);
          imports.push(`import '${htmlRelativePath}'`);
          break;
        case RefType.JSFile:
          if(ref.isNodeModule) {
            imports.push(`import '${ref.path}'`);
          } else {
            if(!this.outFileExists(ref.path)) {
              throw new Error(`Attempts to import non-existing file '${ref.path}' from ${htmlPathRelativeToRoot}`);
            }
            const scriptRelativePath = this.getImportPathRelativeToParent(
                jsFileName, ref.path);
            imports.push(`import '${scriptRelativePath}'`);
          }
          break;
        case RefType.InlineJS:
          const inlineJsName = htmlPathRelativeToRoot + `_inline_${imports.length}.js`;
          this.saveFile(inlineJsName, ref.content);
          const inlineRelativePath = this.getImportPathRelativeToParent(jsFileName, inlineJsName);
          imports.push(`import '${inlineRelativePath}'`);
          break;
        default:
          unexpectedSwitchValue(ref);
      }
    });
    const jsContent = imports.join("\n");
    this.saveFile(jsFileName, jsContent);
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

if(process.argv.length < 5) {
  const execFileName = path.basename(__filename);
  fail(`Usage:\nnode ${execFileName} rootDir outputDir entryPointRelativeToRootDir\n`);
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
