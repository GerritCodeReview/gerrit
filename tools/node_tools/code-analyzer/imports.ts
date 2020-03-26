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

import {ImportDeclaration, Project, SourceFile} from "ts-morph";
import * as path from "path";
import {AbsoluteWebPath, RelativeWebPath, SrcWebSite, WebPath} from "../utils/web-site-utils";
import {fail} from "../utils/common";
import {FilePath} from "../utils/file-utils";
import * as fs from "fs";
import * as parse5 from "parse5";
import * as dom5 from "dom5";
import * as url from "url";
import {Node} from "dom5";
import {readMultilineParamFile} from "../utils/command-line";

interface WebAppProjectOptions {
  webSiteRoot: FilePath;
}

enum FileDependencyType {
  // One of the following:
  // import '...'
  // <script src='...'></script>
  // <script
  JsImportForSideEffect,
  // import ... from '...'
  JsModuleImport,
  // Any other dependency (like css, images, etc...)
  Other
}

interface FileDependency {
  dependencyType: FileDependencyType;
  path: AbsoluteWebPath;
}

class CacheableVariable<T> {
  private value: T | undefined;
  public constructor(private readonly getValue: () => T) {
  }
  public get(): T {
    if(!this.value) {
      this.value = this.getValue();
    }
    return this.value;
  }
}

class Cache<TKey, T> {
  private innerMap: Map<TKey, T> = new Map();
  public constructor(private readonly getValueByKey: (key: TKey) => T) {
  }

  public get(key: TKey): T {
    if(!this.innerMap.has(key)) {
      this.innerMap.set(key, this.getValueByKey(key));
    }
    return this.innerMap.get(key)!;
  }
}

class WebAppSourceFile {
  private dependencies: CacheableVariable<FileDependency[]>;
  public constructor(private readonly project: WebAppProject,
                     private readonly path: AbsoluteWebPath,
                     private readonly sourceFile: SourceFile) {
    this.dependencies = new CacheableVariable(() => this.extractDependencies());
  }

  private isImportForSideEffectsOnly(importDecl: ImportDeclaration): boolean {
    return !!importDecl.compilerNode.importClause;
  }

  private isAbsoluteOrRelativeImportPath(path: string): path is WebPath {
    return path.startsWith('/') ||
        path.startsWith('./') ||
        path.startsWith('../');
  }

  private extractDependencies(): FileDependency[] {
    const decls = this.sourceFile.getImportDeclarations();
    return decls.map((importDecl) => {
      const moduleSpecifierValue = importDecl.getModuleSpecifierValue();
      const dependencyType = this.isImportForSideEffectsOnly(importDecl) ?
          FileDependencyType.JsImportForSideEffect : FileDependencyType.JsModuleImport;
      if(!this.isAbsoluteOrRelativeImportPath(moduleSpecifierValue)) {
        return {
          dependencyType: dependencyType,
          path: <AbsoluteWebPath>('/node_modules/' + moduleSpecifierValue),
        };
      }
      return {
        dependencyType: dependencyType,
        path: SrcWebSite.resolveReferenceFromFile(this.path, moduleSpecifierValue),
      }
    });
  }

  public getDependencies(): FileDependency[] {
    return this.dependencies.get();
  }
}

type InlineScriptId = number;

class WebAppHtmlFile {
  private ast: CacheableVariable<parse5.AST.Document>;
  private dependencies: CacheableVariable<FileDependency[]>;
  private inlineScripts: Map<InlineScriptId, WebAppSourceFile>;

  public constructor(private readonly project: WebAppProject,
                     private readonly path: AbsoluteWebPath) {
    this.dependencies = new CacheableVariable(() => this.extractDependencies());
    this.ast = new CacheableVariable<parse5.AST.Document>(() => this.parseHtml());
    this.inlineScripts = new Map<InlineScriptId, WebAppSourceFile>();
  }

  private parseHtml(): parse5.AST.Document {
    const html = this.project.getFileContent(this.path);
    return parse5.parse(html, {locationInfo: true});
  }

  private resolveRefToAbsolutePath(ref: string): AbsoluteWebPath | null {
    const mockHost = 'gerrit__test__mock__domain';
    const resolvedUrl = new URL('ref', `http://${mockHost}${ref}`);
    if(resolvedUrl.host !== mockHost) {
      return null;
    }
    return resolvedUrl.pathname as AbsoluteWebPath;
  }

  private getInlineScriptId(node: Node): InlineScriptId {
    return 0;
    //return node.__location.startOffset;
  }

  private getInlineScriptFromScriptNode(node: Node): WebAppSourceFile {
    const id = this.getInlineScriptId(node);
    if(!this.inlineScripts.has(id)) {
      const scriptCode = dom5.getTextContent(node);
      const tmpFileName = `${this.path}.${id}.tmp.js` as AbsoluteWebPath;
      const inlineScript: WebAppSourceFile = this.project.registerTemporaryJsFile(tmpFileName, scriptCode);
      this.inlineScripts.set(id, inlineScript);
    }
    return this.inlineScripts.get(id)!;
  }

  private extractDependencies(): FileDependency[] {
    const ast = this.ast.get();
    const isLinkTag = (node: Node) => node.tagName == "link";
    const isScriptTag = (node: Node) => node.tagName == "script";

    const links: FileDependency[] = dom5.nodeWalkAll(ast as Node, isLinkTag)
        .filter(node => dom5.hasAttribute(node, 'href'))
        .map(node => dom5.getAttribute(node, 'href')!)
        .map(ref => this.resolveRefToAbsolutePath(ref))
        .filter(absPath => !!absPath)
        .map(absPath => {
          return {
            dependencyType: FileDependencyType.Other,
            path: absPath!,
          }
        });

    const scriptTags = dom5.nodeWalkAll(ast as Node, isScriptTag);
    const externalScripts: FileDependency[] = scriptTags.filter(node => dom5.hasAttribute(node, 'src'))
        .map(node => dom5.getAttribute(node, 'src')!)
        .map(ref => this.resolveRefToAbsolutePath(ref))
        .filter(absPath => !!absPath)
        .map(absPath => {
          return {
            dependencyType: FileDependencyType.JsImportForSideEffect,
            path: absPath!
          }
        });

    const inlineScriptDependencies: FileDependency[] = [];
    scriptTags.filter(node => !dom5.hasAttribute(node, 'src'))
      .forEach((node, index) => {
        const dependencies = this.getInlineScriptFromScriptNode(node).getDependencies();
        inlineScriptDependencies.push(...dependencies);
      });
    return [...links, ...externalScripts, ...inlineScriptDependencies];
  }

  public getDependencies(): FileDependency[] {
    return this.dependencies.get();
  }
}
type WebAppFile = WebAppHtmlFile | WebAppSourceFile;
class WebAppProject {
  private readonly jsProject: Project = new Project();
  private readonly htmlFiles: Set<AbsoluteWebPath> = new Set();
  private readonly jsFiles: Set<AbsoluteWebPath> = new Set();
  private readonly jsFileDependencies: Map<AbsoluteWebPath, FileDependency[]> = new Map();
  private readonly htmlFileDependencies: Map<AbsoluteWebPath, FileDependency[]> = new Map();
  private readonly srcWebSite: SrcWebSite;
  private readonly sourceFilesCache: Cache<AbsoluteWebPath, WebAppSourceFile>;
  private readonly htmlFilesCache: Cache<AbsoluteWebPath, WebAppHtmlFile>;

  public constructor(private readonly options: Readonly<WebAppProjectOptions>) {
    this.srcWebSite = new SrcWebSite(options.webSiteRoot);
    this.sourceFilesCache = new Cache<AbsoluteWebPath, WebAppSourceFile>(
        path => this.createWebAppSourceFile(path));
    this.htmlFilesCache = new Cache<AbsoluteWebPath, WebAppHtmlFile>(
        path => this.createWebAppHtmlFile(path));
  }

  public registerHtmlFile(file: FilePath) {
    const fullPath = path.resolve(this.options.webSiteRoot, file) as FilePath;
    this.htmlFiles.add(this.srcWebSite.getAbsoluteWebPathToFile(fullPath));
  }

  public registerJsFile(file: FilePath) {
    const fullPath = path.resolve(this.options.webSiteRoot, file) as FilePath;
    const webPath = this.srcWebSite.getAbsoluteWebPathToFile(fullPath);
    this.jsFiles.add(webPath);
    this.jsProject.addSourceFileAtPath(this.srcWebSite.getFilePath(webPath));
  }

  public registerTemporaryJsFile(file: AbsoluteWebPath, content: string): WebAppSourceFile {
    this.jsFiles.add(file);
    // This file remains in memory file until jsProject.save() called
    this.jsProject.createSourceFile(this.srcWebSite.getFilePath(file), content);
    return this.getSourceFile(file);
  }

  public getSourceFile(file: AbsoluteWebPath): WebAppSourceFile {
    return this.sourceFilesCache.get(file);
  }

  public getHtmlFile(file: AbsoluteWebPath): WebAppHtmlFile {
    return this.htmlFilesCache.get(file);
  }

  private createWebAppSourceFile(file: AbsoluteWebPath): WebAppSourceFile {
    if(!this.jsFiles.has(file)) {
      fail(`WebAppProject doesn't contain the following js file: ${file}`);
    }
    const sourceFile = this.jsProject.getSourceFileOrThrow(this.srcWebSite.getFilePath(file));
    return new WebAppSourceFile(this, file, sourceFile);
  }

  private createWebAppHtmlFile(file: AbsoluteWebPath): WebAppHtmlFile {
    if(!this.htmlFiles.has(file)) {
      fail(`WebAppProject doesn't contain the following html file: ${file}`);
    }
    return new WebAppHtmlFile(this, file);
  }

  public getFileContent(file: AbsoluteWebPath) {
    return fs.readFileSync(this.srcWebSite.getFilePath(file), "utf-8");
  }

  public getFile(file: AbsoluteWebPath): WebAppFile {
    if(this.htmlFiles.has(file)) {
      return this.getHtmlFile(file);
    } else if(this.jsFiles.has(file)) {
      return this.getSourceFile(file);
    } else {
      fail(`${file} was not added to WebAppProject`);
    }
  }
}

type Predicate<T> = (value: T) => boolean;

class KeyedSet<Key, T> {
  private readonly innerMap: Map<Key, T> = new Map();
  public constructor(private readonly keyAccessor: (item: T) => Key) {
  }
  public add(item: T): void {
    const key = this.keyAccessor(item);
    if(!this.innerMap.has(key)) {
      this.innerMap.set(key, item);
    }
  }

  public has(item: T): boolean {
    const key = this.keyAccessor(item);
    return this.hasKey(key);
  }

  public hasKey(key: Key): boolean {
    return this.innerMap.has(key);
  }
}

class FileDependencySet extends KeyedSet<AbsoluteWebPath, FileDependency> {
  public constructor() {
    super((dependency) => dependency.path);
  }
}

class ValidTypeImport {

}

class JsTransitiveDependenciesCalculator {
  private readonly transitiveDependencies: Map<AbsoluteWebPath, FileDependencySet> = new Map();
  public constructor(private readonly app: WebAppProject, private dependencyFilter: Predicate<FileDependency>) {
  }
  public getTransitiveDependencies(file: AbsoluteWebPath): FileDependencySet {
    if(!this.transitiveDependencies.has(file)) {
      const dependencySet = new FileDependencySet();
      this.addTransitiveDependencies(dependencySet, file);
      this.transitiveDependencies.set(file, dependencySet);
    }
    return this.transitiveDependencies.get(file)!;
  }
  private addTransitiveDependencies(dependencySet: FileDependencySet, file: AbsoluteWebPath) {
    const dependencies = this.app.getFile(file).getDependencies().filter(this.dependencyFilter);
    for(const dependency of dependencies) {
      if(dependencySet.has(dependency)) {
        continue;
      }
      dependencySet.add(dependency);
      this.addTransitiveDependencies(dependencySet, dependency.path);
    }
  }
}

const webAppProject = new WebAppProject({webSiteRoot: <FilePath>'/Users/dmfilippov/gerrit/gerrit/polygerrit-ui/app'});
webAppProject.registerHtmlFile(<FilePath>'./elements/change/gr-related-changes-list/gr-related-changes-list_test.html');
const jsfiles = readMultilineParamFile('/Users/dmfilippov/js_files');
const testfiles = readMultilineParamFile('/Users/dmfilippov/htmlfiles');
jsfiles.forEach(file => webAppProject.registerJsFile(<FilePath>file));
testfiles.forEach(file => webAppProject.registerHtmlFile(<FilePath>file));

const specialPathPrefixes = new Set<string>(["node_modules", "components"]);

function isSpecialPath(path: AbsoluteWebPath): boolean {
  const parts = path.split('/');
  // parts[0] always empty, because AbsoluteWebPath always starts with '/'
  return parts.length > 1 && specialPathPrefixes.has(parts[1]);
}
const dependencyFilter = (dependency: FileDependency) =>  !isSpecialPath(dependency.path);
console.log("Caclulate");
const calc: JsTransitiveDependenciesCalculator = new JsTransitiveDependenciesCalculator(webAppProject, dependencyFilter);
testfiles.forEach(ft => {
  console.log(calc.getTransitiveDependencies(<AbsoluteWebPath>ft.substr(1)));
})



