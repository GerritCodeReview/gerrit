
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

export function fail(message: string): never {
  console.error(message);
  process.exit(1);
}

export function unexpectedSwitchValue(_: never): never {
  fail(`Internal error - unexpected switch value`);
}

export class FileUtils {
  public static getPathRelativeToRoot(parentHtml: string, fileHref: string): string {
    if (fileHref.startsWith('/')) {
      return fileHref.substring(1);
    }
    return path.join(path.dirname(parentHtml), fileHref);
  }

  public static ensureDirExistsForFile(filePath: string) {
    const dirName = path.dirname(filePath);
    if (!fs.existsSync(dirName)) {
      fs.mkdirSync(dirName, {recursive: true, mode: 0o744});
    }
  }

  public static saveFile(outputRootDir: string, relativePathToRoot: string, content: string) {
    const filePath = path.resolve(path.join(outputRootDir, relativePathToRoot));
    if(fs.existsSync(filePath) && fs.lstatSync(filePath).isSymbolicLink()) {
      throw new Error(`Output file '${filePath}' is a symbolic link. Inplace update for links are not supported.`);
    }
    FileUtils.ensureDirExistsForFile(filePath);
    fs.writeFileSync(filePath, content);
    console.log(filePath);
  }

  public static copyFile(src: string, dst: string) {
    FileUtils.ensureDirExistsForFile(dst);
    fs.copyFileSync(src, dst);
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

interface ResolvedPath {
  target: string;
  insideNodeModules: boolean;
}

type PathRedirect = RedirectToDir | RedirectToNodeModule;

interface RedirectToDir {
  dir: string;
  files?: { [name: string]: string }
}

interface RedirectToNodeModule {
  npm_module: string;
  files?: { [name: string]: string }
}

function isRedirectToNodeModule(redirect: PathRedirect): redirect is RedirectToNodeModule {
  return (redirect as RedirectToNodeModule).npm_module !== undefined;
}

function isRedirectToDir(redirect: PathRedirect): redirect is RedirectToDir {
  return (redirect as RedirectToDir).dir !== undefined;
}

interface RedirectForFile {
  to: PathRedirect;
  pathToFile: string;
}

export interface Redirect {
  from: string;
  to: PathRedirect;
}

export class RedirectsResolver {
  public constructor(private readonly redirects: Redirect[]) {
  }
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

export type DirEntryPredicate = (dirEntry: fs.Dirent, dirEntryPath: string) => boolean;
export type DirEntryAction = (dirEntry: fs.Dirent, dirEntryPath: string) => void;

export class RecursiveDirectoryProcessor {
  public constructor(private readonly rootDir: string,
              private readonly predicate: DirEntryPredicate,
              private readonly dirEntryAction: DirEntryAction) {
  }
  public process(dirNameRelativeToRoot: string) {
    const entries = fs.readdirSync(path.join(this.rootDir, dirNameRelativeToRoot), {withFileTypes: true});
    for(const dirEnt of entries) {
      const dirEntPath = path.join(dirNameRelativeToRoot, dirEnt.name);
      if(dirEnt.isDirectory() || this.isLinkToDirectory(dirEnt, dirEntPath)) {
        this.process(dirEntPath);
        continue;
      }
      if(this.predicate(dirEnt, dirEntPath)) {
        this.dirEntryAction(dirEnt, dirEntPath);
      }
    }
  }
  private isLinkToDirectory(dirEntry: fs.Dirent, dirEntryPath: string): boolean {
    return dirEntry.isSymbolicLink() &&
        fs.lstatSync(fs.realpathSync(path.join(this.rootDir, dirEntryPath))).isDirectory();
  }
}

export class DirectoryCopier {
  public constructor(private readonly rootDir: string, private readonly outputRootDir: string) {
  }
  public copyRecursively(dirNameRelativeToRoot: string, filePredicate: DirEntryPredicate) {
    const dirEntPredicate: DirEntryPredicate = (dirEnt, dirEntryPath) => {
      return (dirEnt.isFile() || dirEnt.isSymbolicLink()) && filePredicate(dirEnt, dirEntryPath)
    };

    const copyAction: DirEntryAction = (dirEnt, dirEntPath) => {
      FileUtils.copyFile(path.join(this.rootDir, dirEntPath), path.join(this.outputRootDir, dirEntPath));
    };
    const copyProcessor = new RecursiveDirectoryProcessor(this.rootDir, dirEntPredicate, copyAction);
    copyProcessor.process(dirNameRelativeToRoot);
  }
}
