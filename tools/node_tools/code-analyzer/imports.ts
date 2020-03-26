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

class WebAppProject {
  private readonly jsProject: Project = new Project();
  private readonly htmlFiles: Set<AbsoluteWebPath> = new Set();
  private readonly jsFiles: Set<AbsoluteWebPath> = new Set();
  private readonly jsFileDependencies: Map<AbsoluteWebPath, FileDependency[]> = new Map();
  private readonly htmlFileDependencies: Map<AbsoluteWebPath, FileDependency[]> = new Map();
  private readonly srcWebSite: SrcWebSite;

  public constructor(private readonly options: Readonly<WebAppProjectOptions>) {
    this.srcWebSite = new SrcWebSite(options.webSiteRoot);
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

  private registerTemporaryJsFile(file: AbsoluteWebPath, content: string) {
    this.jsFiles.add(file);
    this.jsProject.createSourceFile(this.srcWebSite.getFilePath(file), content);
  }

  private isImportForSideEffectsOnly(importDecl: ImportDeclaration): boolean {
    return !!importDecl.compilerNode.importClause;
  }

  private isAbsoluteOrRelativeImportPath(path: string): path is WebPath {
    return path.startsWith('/') ||
        path.startsWith('./') ||
        path.startsWith('../');
  }


  private extractDependenciesForJsFile(jsFilePath: AbsoluteWebPath): FileDependency[] {
    if(!this.jsFiles.has(jsFilePath)) {
      fail(`${jsFilePath} was not added to WebAppProject`);
    }
    const sourceFile = this.jsProject.getSourceFileOrThrow(
        this.srcWebSite.getFilePath(jsFilePath));
    const decls = sourceFile.getImportDeclarations();
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
        path: SrcWebSite.resolveReferenceFromFile(jsFilePath, moduleSpecifierValue),
      }
    });
  }

  public getDependenciesForJsFile(jsFile: AbsoluteWebPath): FileDependency[] {
    if(!this.jsFileDependencies.has(jsFile)) {
      this.jsFileDependencies.set(jsFile, this.extractDependenciesForJsFile(jsFile));
    }
    return this.jsFileDependencies.get(jsFile)!;
  }

  private getAstForHtml(htmlFilePath: AbsoluteWebPath): parse5.AST.Document {
    const filePath = this.srcWebSite.getFilePath(htmlFilePath);
    const html = fs.readFileSync(filePath, "utf-8");
    return parse5.parse(html, {locationInfo: true});
  }

  private extractDependenciesForHtmlFile(htmlFilePath: AbsoluteWebPath): FileDependency[] {
    if(!this.htmlFiles.has(htmlFilePath)) {
      fail(`${htmlFilePath} was not added to WebAppProject`);
    }
    const ast = this.getAstForHtml(htmlFilePath);
    const isLinkTag = (node: Node) => node.tagName == "link";
    const isScriptTag = (node: Node) => node.tagName == "script";

    const htmlDependencyPathMapper = (path: string) =>
        this.isAbsoluteOrRelativeImportPath(path) ? path :
          <RelativeWebPath>`./${path}`;

    const links = dom5.nodeWalkAll(ast as Node, isLinkTag)
      .filter(node => dom5.hasAttribute(node, 'href'))
      .map(node => dom5.getAttribute(node, 'href')!)
      .map(htmlDependencyPathMapper)
      .map(webPath => {
        return {
          dependencyType: FileDependencyType.Other,
          path: SrcWebSite.resolveReferenceFromFile(htmlFilePath, webPath),
        }
      });

    const scriptTags = dom5.nodeWalkAll(ast as Node, isScriptTag);
    const exteranlScripts = scriptTags.filter(node => dom5.hasAttribute(node, 'src'))
      .map(node => dom5.getAttribute(node, 'src')!)
      .map(htmlDependencyPathMapper)
      .map(webPath => {
        return {
          dependencyType: FileDependencyType.JsImportForSideEffect,
          path: SrcWebSite.resolveReferenceFromFile(htmlFilePath, webPath)
        }
      });

    const inlineScriptDependencies: FileDependency[] = [];
    scriptTags.filter(node => !dom5.hasAttribute(node, 'src'))
      .forEach((node, index) => {
        const scriptCode = dom5.getTextContent(node);
        const tmpFileName = `${htmlFilePath}.__tmp__.${index}.js` as AbsoluteWebPath;
        // This file remains in memory file until jsProject.save() called
        this.registerTemporaryJsFile(tmpFileName, scriptCode);
        const dependencies = this.extractDependenciesForJsFile(tmpFileName);
        inlineScriptDependencies.push(...dependencies);
      });
    return [...links, ...exteranlScripts, ...inlineScriptDependencies];
  }

  public getDependenciesForHtmlFile(htmlFile: AbsoluteWebPath): FileDependency[] {
    if(!this.htmlFileDependencies.has(htmlFile)) {
      this.jsFileDependencies.set(htmlFile, this.extractDependenciesForHtmlFile(htmlFile));
    }
    return this.jsFileDependencies.get(htmlFile)!;
  }

  public getDependenciesForFile(file: AbsoluteWebPath): FileDependency[] {
    if(this.htmlFiles.has(file)) {
      return this.getDependenciesForHtmlFile(file);
    } else if(this.jsFiles.has(file)) {
      return this.getDependenciesForJsFile(file);
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
    const dependencies = this.app.getDependenciesForFile(file).filter(this.dependencyFilter);
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
console.log(calc.getTransitiveDependencies(<AbsoluteWebPath>'/elements/change/gr-related-changes-list/gr-related-changes-list_test.html'));


