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

const fs = require("fs");
const parse5 = require("parse5");
const dom5 = require("dom5");
const path = require("path");
const {FileUtils, DirectoryCopier} = require('./code_utils');

const RefType = {
  Html: "html",
  InlineJS: "inlineJS",
  JSFile: "JSFile"
};



class RefMapCollector {
  constructor(rootDir) {
    this.rootDir = rootDir;
    this.refMap = new Map();
  }

  addRefDataRecursively(htmlPathRelativeToRoot) {
    if(this.refMap.has(htmlPathRelativeToRoot)) {
      return;
    }
    const ast = this._getAst(htmlPathRelativeToRoot);
    const refs = [];
    dom5.nodeWalkAll(ast, (node) => {
      if(node.tagName === "link" && dom5.getAttribute(node, "rel") == "import") {
        let href = dom5.getAttribute(node, "href");
        refs.push({
          type: RefType.Html,
          path: FileUtils.getPathRelativeToRoot(htmlPathRelativeToRoot, href),
        });
        return;
      }
      if(node.tagName === "script") {
        const isModule = dom5.getAttribute(node, "type") === "module";
        if(dom5.hasAttribute(node, "src")) {
          let srcPath = dom5.getAttribute(node, "src");
          const isRelativePath = !srcPath.startsWith("/");
          const originalPath = FileUtils.getPathRelativeToRoot(htmlPathRelativeToRoot, srcPath);
          const nodeModulesPrefix = 'node_modules/';
          const isNpmModule = originalPath.startsWith(nodeModulesPrefix);
          const path = isNpmModule ? originalPath.substring(nodeModulesPrefix.length) : originalPath;
          refs.push({
            type: RefType.JSFile,
            isModule: isModule,
            path: path,
            isNpmModule: isNpmModule,
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
    refs.filter((ref) => ref.type === RefType.Html).forEach((htmlRef) => this.addRefDataRecursively(htmlRef.path));
  };

  _getAst(file) {
    const filePath = path.join(this.rootDir, file);
    const html = fs.readFileSync(filePath, "utf-8");
    return parse5.parse(html, {locationInfo: true});
  }

}

class RecursiveGenerator {
  constructor(srcRootDir, outputRootDir, refMap) {
    this.srcRootDir = srcRootDir;
    this.outputRootDir = outputRootDir;
    this.refMap  = refMap;
    this.generatedSet = new Set();
  }

  generateRecursively(htmlPathRelativeToRoot) {
    if(this.generatedSet.has(htmlPathRelativeToRoot)) {
      return;
    }
    this.generatedSet.add(htmlPathRelativeToRoot);
    const data = this.refMap.get(htmlPathRelativeToRoot);
    if(!data) {
      throw new Error(`Can't get data for '${htmlPathRelativeToRoot}'`);
    }

    this.generateFor(htmlPathRelativeToRoot, data);

    data.refs.filter((ref) => ref.type === RefType.Html).forEach((htmlRef) => this.generateRecursively(htmlRef.path));
  }

  _getImportPathRelativeToParent(parentFile, importPath) {
    return FileUtils.getImportPathRelativeToParent(this.outputRootDir, parentFile, importPath);
  }

  _outFileExists(file) {
    return fs.existsSync(path.join(this.outputRootDir, file));
  }

  _saveFile(outFile, content) {
    FileUtils.saveFile(this.outputRootDir, outFile, content);
  }

  generateFor(htmlPathRelativeToRoot, data) {
    throw new Error(`Not implemented in this class`);
  }
}

class ScriptGenerator extends RecursiveGenerator{
  constructor(srcRootDir, outputRootDir, refMap) {
    super(srcRootDir, outputRootDir, refMap);
  }

  generateFor(htmlPathRelativeToRoot, data) {
    const imports = [];
    const jsFileName = htmlPathRelativeToRoot + "_generated.js";
    data.refs.forEach((ref) => {
      switch (ref.type) {
        case RefType.Html:
          const importPath = ref.path + "_generated" + ".js";
          const htmlRelativePath = this._getImportPathRelativeToParent(jsFileName, importPath);
          imports.push(`import '${htmlRelativePath}'`);
          break;
        case RefType.JSFile:
          if(ref.isNpmModule) {
            imports.push(`import '${ref.path}'`);
          } else {
            if(!this._outFileExists(ref.path)) {
              throw new Error(`Attempts to import non-existing file '${ref.path}' from ${htmlPathRelativeToRoot}`);
            }
            const scriptRelativePath = this._getImportPathRelativeToParent(
                jsFileName, ref.path);
            imports.push(`import '${scriptRelativePath}'`);
          }
          break;
        case RefType.InlineJS:
          const inlineJsName = htmlPathRelativeToRoot + `_inline_${imports.length}.js`;
          this._saveFile(inlineJsName, ref.content);
          const inlineRelativePath = this._getImportPathRelativeToParent(jsFileName, inlineJsName);
          imports.push(`import '${inlineRelativePath}'`);
          break;
        default:
          throw new Error(`Internal error: ${ref.type} is not processed`);
      }
    });
    const jsContent = imports.join("\n");
    this._saveFile(jsFileName, jsContent);
  }
}

class HtmlGenerator extends RecursiveGenerator{
  constructor(srcRootDir, outputRootDir, refMap, htmlProcessor) {
    super(srcRootDir, outputRootDir, refMap);
    this.htmlProcessor = htmlProcessor;
  }
  generateFor(htmlPathRelativeToRoot, data) {
    this.htmlProcessor(htmlPathRelativeToRoot, data.ast);
    const newContent = parse5.serialize(data.ast);
    this._saveFile(htmlPathRelativeToRoot, newContent);
  }
}

if(process.argv.length < 5) {
  console.info("Usage:\nnode app_preprocessor.js rootDir outputDir entryPointRelativeToRootDir\n");
  process.exit(1);
}

const rootDir = path.resolve(process.cwd(), process.argv[2]);
const outputRootDir = path.resolve(process.cwd(), process.argv[3]);
const entryPoint = process.argv[4];

const collector = new RefMapCollector(rootDir);
collector.addRefDataRecursively(entryPoint);

const directoryCopier = new DirectoryCopier(rootDir, outputRootDir);
const isJsFile = (fullPath) => path.extname(fullPath) === ".js";
directoryCopier.copyRecursively("./", isJsFile);

const scriptGenerator = new ScriptGenerator(rootDir, outputRootDir, collector.refMap);
scriptGenerator.generateRecursively(entryPoint);

const htmlProcessor = (filePath, ast) => {
  dom5.nodeWalkAll(ast, (node) => node.tagName === "script")
  .forEach((scriptNode) => dom5.remove(scriptNode));
};

const htmlGenerator = new HtmlGenerator(rootDir, outputRootDir, collector.refMap, htmlProcessor);
htmlGenerator.generateRecursively(entryPoint);
