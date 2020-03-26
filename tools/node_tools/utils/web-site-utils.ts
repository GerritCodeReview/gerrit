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

import * as path from "path";
import {fail} from "./common";
import {FilePath, HtmlSrcFilePath, JsSrcFilePath} from "./file-utils";

export type AbsoluteWebPath = string & { __absoluteWebPath: undefined };
export type RelativeWebPath = string & { __relativeWebPath: undefined };
export type WebPath = AbsoluteWebPath | RelativeWebPath;

export type NodeModuleImportPath = string & {__nodeModuleImportPath: undefined};

export type AbsoluteTypedWebPath<T> = AbsoluteWebPath & { __type?: T, __absoluteTypedFilePath: undefined };
export type RelativeTypedWebPath<T> = RelativeWebPath & { __type?: T, __relativeTypedFilePath: undefined };

export type TypedWebPath<T> = AbsoluteTypedWebPath<T> | RelativeTypedWebPath<T>;

export function isAbsoluteWebPath(path: WebPath): path is AbsoluteWebPath {
  return path.startsWith("/");
}

export function isRelativeWebPath(path: WebPath): path is RelativeWebPath {
  return !isAbsoluteWebPath(path);
}
const node_modules_path_prefix = "/node_modules/";

/** Contains method to resolve absolute and relative paths */
export class SrcWebSite {
  public constructor(private readonly webSiteRoot: FilePath) {
  }

  public getFilePath(webPath: AbsoluteWebPath): FilePath {
    return path.resolve(this.webSiteRoot, webPath.substr(1)) as FilePath;
  }

  public getAbsoluteWebPathToFile(file: FilePath): AbsoluteWebPath {
    const relativePath = path.relative(this.webSiteRoot, file);
    if(relativePath.startsWith("..")) {
      fail(`The file ${file} is not under webSiteRoot`);
    }
    return ("/" + relativePath) as AbsoluteWebPath;
  }

  public static resolveReferenceFromFile(from: AbsoluteWebPath, to: WebPath): AbsoluteWebPath {
    return isAbsoluteWebPath(to) ? to : path.resolve(path.dirname(from), to) as AbsoluteWebPath;
  }

  public static resolveReference(from: AbsoluteWebPath, to: WebPath): AbsoluteWebPath {
    return isAbsoluteWebPath(to) ? to : path.resolve(from, to) as AbsoluteWebPath;
  }

  public static getRelativePath(from: AbsoluteWebPath, to: AbsoluteWebPath): RelativeWebPath {
    return path.relative(from, to) as RelativeWebPath;
  }

  public resolveHtmlImport(from: HtmlSrcFilePath, href: string): HtmlSrcFilePath {
    return this.resolveReferenceToAbsPath(from, href) as HtmlSrcFilePath;

  }
  public resolveScriptSrc(from: HtmlSrcFilePath, src: string): JsSrcFilePath {
    return this.resolveReferenceToAbsPath(from, src) as JsSrcFilePath;
  }

  public isNodeModuleReference(ref: string): boolean {
    return ref.startsWith(node_modules_path_prefix);
  }

  public getNodeModuleImport(ref: string): NodeModuleImportPath {
    if(!this.isNodeModuleReference(ref)) {
      fail(`Internal error! ${ref} must be inside node modules`);
    }
    return ref.substr(node_modules_path_prefix.length) as NodeModuleImportPath;
  }

  private resolveReferenceToAbsPath(from: string, ref: string): string {
    if(ref.startsWith("/")) {
      const relativeToRootPath = ref.substr(1);
      return path.resolve(this.webSiteRoot, relativeToRootPath);
    }
    return path.resolve(path.dirname(from), ref);
  }
}

export function getRelativeImport(from: FilePath, ref: FilePath) {
  const relativePath = path.relative(path.dirname(from), ref);
  if(relativePath.startsWith("../")) {
    return relativePath
  } else {
    return "./" + relativePath;
  }
}
