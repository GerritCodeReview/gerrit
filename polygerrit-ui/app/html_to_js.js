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

const RefType = {
  Html: "html",
  InlineJS: "inlineJS",
  JSFile: "JSFile"
};

function getPathRelativeToRoot(parentHtml, fileHref) {
  if(fileHref.startsWith('/')) {
    return fileHref.substring(1);
  }
  return path.join(path.dirname(parentHtml), fileHref);
}

function getFragmentFromHtmlImport(document) {
  dom5.isDocumentFragment()
}

class RedirectsResolver {
  constructor(redirects) {
    this.redirects = redirects;
  }
  resolve(pathRelativeToRoot, resolveNpmModules) {
    const redirect = this._findRedirect(pathRelativeToRoot);
    if (!redirect) {
      return {path: pathRelativeToRoot, isNpmModule: false};
    }
    if (redirect.dst.npm_module) {
      return {
        path: resolveNpmModules ? this._resolveNpmModule(redirect.dst,
            redirect.pathToFile) : pathRelativeToRoot,
        isNpmModule: resolveNpmModules
      };
    }
    if (redirect.dst.dir) {
      let newDir = redirect.dst.dir;
      if (!newDir.endsWith('/')) {
        newDir = newDir + '/';
      }
      return {path: `${newDir}${redirect.pathToFile}`, isNpmModule: false}
    }
    throw new Error(`Invalid redirect for path: ${pathRelativeToRoot}`);
  }

  _resolveNpmModule(npmRedirect, pathToFile) {
    if(npmRedirect.files && npmRedirect.files[pathToFile]) {
      pathToFile = npmRedirect.files[pathToFile];
    }
    return `${npmRedirect.npm_module}/${pathToFile}`;
  }

  _findRedirect(relativePathToRoot) {
    if(!relativePathToRoot.startsWith('/')) {
      relativePathToRoot = '/' + relativePathToRoot;
    }
    for(const redirect of this.redirects) {
      const normalizedFrom = redirect.from + (redirect.from.endsWith('/') ? '' : '/');
      if(relativePathToRoot.startsWith(normalizedFrom)) {
        return {
          dst: redirect.to,
          pathToFile: relativePathToRoot.substring(normalizedFrom.length)
        };
      }
    }
    return null;
  }
}

class RefMapCollector {
  constructor(rootDir, redirectResolver) {
    this.rootDir = rootDir;
    this.redirectResolver = redirectResolver;
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
        const originalPath = getPathRelativeToRoot(htmlPathRelativeToRoot, href);
        refs.push({
          type: RefType.Html,
          path: this.redirectResolver.resolve(originalPath, false).path,
        });
        return;
      }
      if(node.tagName === "script") {
        const isModule = dom5.getAttribute(node, "type") === "module";
        if(dom5.hasAttribute(node, "src")) {
          let srcPath = dom5.getAttribute(node, "src");
          const isRelativePath = !srcPath.startsWith("/");
          const originalPath = getPathRelativeToRoot(htmlPathRelativeToRoot, srcPath);
          const resolvedPath = this.redirectResolver.resolve(originalPath, true)
          refs.push({
            type: RefType.JSFile,
            isModule: isModule,
            path: resolvedPath.path,
            isNpmModule: resolvedPath.isNpmModule,
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
    return getImportPathRelativeToParent(this.outputRootDir, parentFile, importPath);
  }

  _outFileExists(file) {
    return fs.existsSync(path.join(this.outputRootDir, file));
  }

  _saveFile(outFile, content) {
    saveFile(this.outputRootDir, outFile, content);
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
  }
  generateFor(htmlPathRelativeToRoot, data) {
    htmlProcessor(htmlPathRelativeToRoot, data.ast);
    const newContent = parse5.serialize(data.ast);
    this._saveFile(htmlPathRelativeToRoot, newContent);
  }
}

function collectScriptsTreeNew(rootDir, parent, fileHref, visitedMap) {
  const relativeToRootPath = getPathRelativeToRoot(parent, fileHref);
  if(visitedMap.has(relativeToRootPath)) {
    return;
  }
  visitedMap.add(relativeToRootPath);

  const refs = [];
  const filePath = path.join(rootDir, relativeToRootPath);
  const html = fs.readFileSync(filePath, "utf-8")
  const ast = parse5.parse(html, {locationInfo: true});
  const nodesToRemove = [];

  dom5.nodeWalkAll(ast, (node) => {
    if(node.tagName === "link") {
      let href = dom5.getAttribute(node, "rel");
      const subtree = collectScriptsTreeNew(rootDir, path.dirname(relativeToRootPath), href, visitedMap);
      refs.push({
        type: RefType.Html,
        linkInfo: linkInfo,
      });
    } else if(node.tagName === "script") {
      nodesToRemove.push(node);

      const isModule = dom5.getAttribute(node, "type") === "module";
      if(dom5.hasAttribute(node, "src")) {
        let srcPath = dom5.getAttribute(node, "src");
        const isRelativePath = !srcPath.startsWith("/");
        refs.push({
          type: RefType.JSFile,
          isModule: isModule,
          isRelativePath: true,//Html can't have reference to node_modules
          path: isRelativePath ? getPathRelativeToRoot(path.dirname(relativeToRootPath), srcPath) : srcPath,
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
  return {
    path: relativeToRootPath,
    refs: refs,
    ast: ast,
  }
}

function collectScriptsTree(rootDir, parent, fileHref, visitedMap) {
  const relativeToRootPath = getPathRelativeToRoot(parent, fileHref);
  if(visitedMap.has(relativeToRootPath)) {
    return { hrefRelativeToRoot: relativeToRootPath, hrefAst: null };
  }
  const refs = [];
  visitedMap.set(relativeToRootPath, refs);
  const filePath = path.join(rootDir, relativeToRootPath);
  const html = fs.readFileSync(filePath, "utf-8")
  const ast = parse5.parse(html, {locationInfo: true});
  const nodesToRemove = [];
  const replacements = [];

  dom5.nodeWalkAll(ast, (node) => {
    if(node.tagName === "link") {
      if(dom5.getAttribute(node, "rel") == "import") {
        let href = dom5.getAttribute(node, "href");
        const {hrefRelativeToRoot, hrefAst} = collectScriptsTree(rootDir, path.dirname(relativeToRootPath), href, visitedMap);
        if(typeof(hrefRelativeToRoot) !== "string") {
          console.log("Error");
        }
        refs.push({
          type: RefType.Html,
          path: hrefRelativeToRoot,
          newAst: hrefAst
        });
        // if(hrefAst) {
        //   replacements.push({
        //     srcNode: node,
        //     newNode: getFragmentFromHtmlImport(hrefAst),
        //   });
        // }
        // else {
        //   nodesToRemove.push(node);
        // }
        return;
      }
      return;
    }
    if(node.tagName === "script") {
      nodesToRemove.push(node);

      const isModule = dom5.getAttribute(node, "type") === "module";
      if(dom5.hasAttribute(node, "src")) {
        let srcPath = dom5.getAttribute(node, "src");
        const isRelativePath = !srcPath.startsWith("/");
        refs.push({
          type: RefType.JSFile,
          isModule: isModule,
          isRelativePath: true,//Html can't have reference to node_modules
          path: isRelativePath ? getPathRelativeToRoot(path.dirname(relativeToRootPath), srcPath) : srcPath,
        });
        return;
      }
      refs.push({
        type: RefType.InlineJS,
        isModule: isModule,
        content: dom5.getTextContent(node)
      });
    }
    return;
  });
  nodesToRemove.forEach((n) => {
    dom5.remove(n);
  });
  // replacements.forEach((v) => {
  //   dom5.replace(v.srcNode, v.newNode);
  // });
  return {
    hrefRelativeToRoot: relativeToRootPath,
    hrefAst: ast
  };
}

function ensureDirExistsForFile(filePath) {
  const dirName = path.dirname(filePath);
  if(!fs.existsSync(dirName)) {
    fs.mkdirSync(dirName, { recursive: true, mode: 0o744});
  }
}

function saveFile(outputRootDir, relativePathToRoot, content) {
  const filePath = path.resolve(path.join(outputRootDir, relativePathToRoot));
  ensureDirExistsForFile(filePath);
  fs.writeFileSync(filePath, content);
}

function copyFile(src, dst) {
  ensureDirExistsForFile(dst);
  fs.copyFileSync(src, dst);
}

function getImportPathRelativeToParent(rootDir, parentFile, importPath) {
  if (importPath.startsWith('/')) {
    importPath = importPath.substr(1);
  }
  const parentDir = path.dirname(path.resolve(path.join(rootDir, parentFile)));
  const fullImportPath = path.resolve(path.join(rootDir, importPath));
  const relativePath = path.relative(parentDir, fullImportPath);
  return relativePath.startsWith('../') ?
      relativePath : "./" + relativePath;
}

function generateOutputForHtmlImport(rootDir, outputRootDir, visitedMap, filePathRelativeToRoot, generatedMap) {
  if(generatedMap.has(filePathRelativeToRoot)) {
    return null;// generatedMap.get(filePathRelativeToRoot);
  }
  const jsOutFileName = filePathRelativeToRoot + "_generated.js";
  generatedMap.set(filePathRelativeToRoot, jsOutFileName);

  const refs = visitedMap.get(filePathRelativeToRoot);
  if(refs === undefined) {
    console.log("Error!");
  }
  const imports = [];
  for (const ref of refs) {
    if(ref.path && ref.path.startsWith('/')) {
      ref.path = ref.path.substr(1);
    }
    switch(ref.type) {
      case RefType.InlineJS:
        const relativeToRootPath = filePathRelativeToRoot + "_inline_" + imports.length + ".js";
        if(!generatedMap.has(relativeToRootPath)) {
          saveFile(outputRootDir, relativeToRootPath, ref.content);
          generatedMap.set(relativeToRootPath, relativeToRootPath);
        }
        imports.push({
          path: relativeToRootPath,
          isModule: ref.isModule,
          isRelativePath: true,
        });
        break;
      case RefType.JSFile:
        if(ref.isRelativePath) {
          if(!generatedMap.has(ref.path)) {
            copyFile(path.join(rootDir, ref.path),
                path.join(outputRootDir, ref.path));
            generatedMap.set(ref.path, ref.path);
          }
          imports.push({
            path: ref.path,
            isModule: ref.isModule,
            isRelativePath: true
          });
        } else {
          imports.push({
            path: ref.path,
            isModule: ref.isModule,
            isRelativePath: false,
          });
        }
        break;
      case RefType.Html:
        if(ref.newAst) {
          saveFile(outputRootDir, filePathRelativeToRoot,
              parse5.serialize(ref.newAst));
        }

        const importJsPath = generateOutputForHtmlImport(rootDir, outputRootDir, visitedMap, ref.path, generatedMap);
        if(importJsPath) {
          imports.push({
            path: importJsPath,
            isModule: true,
            isRelativePath: true
          });
        }
        break;
        throw new Error('Internal error');
    }
  }
  const jsContent = imports.map((imp) => {
    const relativePath = getImportPathRelativeToParent(outputRootDir, filePathRelativeToRoot, imp.path);
    return `import '${relativePath}'`;
  }).join("\n");
  saveFile(outputRootDir, jsOutFileName, jsContent);
  return jsOutFileName;
}

class DirectoryCopier {
  constructor(rootDir, outputRootDir) {
    this.rootDir = rootDir;
    this.outputRootDir = outputRootDir;
  }
  copyRecursively(dirNameRelativeToRoot, predicate) {
    const entries = fs.readdirSync(path.join(this.rootDir, dirNameRelativeToRoot), {withFileTypes: true});
    for(const dirEnt of entries) {
      const dirEntPath = path.join(dirNameRelativeToRoot, dirEnt.name);
      if(dirEnt.isDirectory()) {
        this.copyRecursively(dirEntPath, predicate);
        continue;
      }
      if((dirEnt.isFile() || dirEnt.isSymbolicLink()) && predicate(dirEntPath)) {
        copyFile(path.join(this.rootDir, dirEntPath), path.join(this.outputRootDir, dirEntPath));
        continue;
      }
    }
  }
}

if(process.argv.length < 6) {
  console.info("Usage:\nnode html_to_js.js rootDir outputDir entryPointRelativeToRootDir redirectFile\n");
  process.exit(1);
}

const rootDir = path.resolve(process.cwd(), process.argv[2]);
const outputRootDir = path.resolve(process.cwd(), process.argv[3]);
const entryPoint = process.argv[4];
const redirectFile = path.resolve(process.cwd(), process.argv[5]);
const redirectResolver = new RedirectsResolver(JSON.parse(fs.readFileSync(redirectFile)).redirects);

const collector = new RefMapCollector(rootDir, redirectResolver);
collector.addRefDataRecursively(entryPoint);

const directoryCopier = new DirectoryCopier(rootDir, outputRootDir);
const isJsFile = (fullPath) => path.extname(fullPath) === ".js";
directoryCopier.copyRecursively("app", isJsFile);

const scriptGenerator = new ScriptGenerator(rootDir, outputRootDir, collector.refMap);
scriptGenerator.generateRecursively(entryPoint);

const htmlProcessor = (filePath, ast) => {
  dom5.nodeWalkAll(ast, (node) => node.tagName === "script")
  .forEach((scriptNode) => dom5.remove(scriptNode));
  dom5.nodeWalkAll(ast, (node) => node.tagName === "link" && dom5.getAttribute(node, "rel") == "import")
  .forEach((importHtmlNode) => {
    let href = dom5.getAttribute(importHtmlNode, "href");
    const originalPath = getPathRelativeToRoot(filePath, href);
    const resolvedPath = redirectResolver.resolve(originalPath, false);
    if(resolvedPath.path != originalPath) {
      dom5.setAttribute(importHtmlNode, "href", getImportPathRelativeToParent(outputRootDir, filePath, resolvedPath.path));
    }
  });
};

const htmlGenerator = new HtmlGenerator(rootDir, outputRootDir, collector.refMap);
htmlGenerator.generateRecursively(entryPoint);
process.exit(1);
