
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
import * as path from "path";
import {FileUtils} from "../utils/file-utils";
import {
  Redirect,
  isRedirectToNodeModule,
  isRedirectToDir,
  RedirectToNodeModule,
  PathRedirect
} from "./redirects";

export class HtmlFileUtils {
  public static getPathRelativeToRoot(parentHtml: string, fileHref: string): string {
    if (fileHref.startsWith('/')) {
      return fileHref.substring(1);
    }
    return path.join(path.dirname(parentHtml), fileHref);
  }

  public static getImportPathRelativeToParent(rootDir: string, parentFile: string, importPath: string) {
    if (importPath.startsWith('/')) {
      importPath = importPath.substr(1);
    }
    const parentDir = path.dirname(
        path.resolve(path.join(rootDir, parentFile)));
    const fullImportPath = path.resolve(path.join(rootDir, importPath));
    const relativePath = path.relative(parentDir, fullImportPath);
    return relativePath.startsWith('../') ?
        relativePath : "./" + relativePath;
  }
}
interface RedirectForFile {
  to: PathRedirect;
  pathToFile: string;
}

interface ResolvedPath {
  target: string;
  insideNodeModules: boolean;
}

/** RedirectsResolver based on the list of redirects, calculates
 *  new import path
 */
export class RedirectsResolver {
  public constructor(private readonly redirects: Redirect[]) {
  }

  /** resolve returns new path instead of pathRelativeToRoot; */
  public resolve(pathRelativeToRoot: string, resolveNodeModules: boolean): ResolvedPath {
    const redirect = this.findRedirect(pathRelativeToRoot);
    if (!redirect) {
      return {target: pathRelativeToRoot, insideNodeModules: false};
    }
    if (isRedirectToNodeModule(redirect.to)) {
      return {
        target: resolveNodeModules ? RedirectsResolver.resolveNodeModuleFile(redirect.to,
            redirect.pathToFile) : pathRelativeToRoot,
        insideNodeModules: resolveNodeModules
      };
    }
    if (isRedirectToDir(redirect.to)) {
      let newDir = redirect.to.dir;
      if (!newDir.endsWith('/')) {
        newDir = newDir + '/';
      }
      return {target: `${newDir}${redirect.pathToFile}`, insideNodeModules: false}
    }
    throw new Error(`Invalid redirect for path: ${pathRelativeToRoot}`);
  }

  private static resolveNodeModuleFile(npmRedirect: RedirectToNodeModule, pathToFile: string): string {
    if(npmRedirect.files && npmRedirect.files[pathToFile]) {
      pathToFile = npmRedirect.files[pathToFile];
    }
    return `${npmRedirect.npm_module}/${pathToFile}`;
  }

  private findRedirect(relativePathToRoot: string): RedirectForFile | undefined {
    if(!relativePathToRoot.startsWith('/')) {
      relativePathToRoot = '/' + relativePathToRoot;
    }
    for(const redirect of this.redirects) {
      const normalizedFrom = redirect.from + (redirect.from.endsWith('/') ? '' : '/');
      if(relativePathToRoot.startsWith(normalizedFrom)) {
        return {
          to: redirect.to,
          pathToFile: relativePathToRoot.substring(normalizedFrom.length)
        };
      }
    }
    return undefined;
  }
}
