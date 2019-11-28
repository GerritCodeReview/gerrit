
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
const path = require("path");

class FileUtils {
  static getPathRelativeToRoot(parentHtml, fileHref) {
    if (fileHref.startsWith('/')) {
      return fileHref.substring(1);
    }
    return path.join(path.dirname(parentHtml), fileHref);
  }

  static ensureDirExistsForFile(filePath) {
    const dirName = path.dirname(filePath);
    if (!fs.existsSync(dirName)) {
      fs.mkdirSync(dirName, {recursive: true, mode: 0o744});
    }
  }

  static saveFile(outputRootDir, relativePathToRoot, content) {
    const filePath = path.resolve(path.join(outputRootDir, relativePathToRoot));
    if(fs.existsSync(filePath) && fs.lstatSync(filePath).isSymbolicLink()) {
      throw new Error(`Output file '${filePath}' is a symbolic link. Inplace update for links are not supported.`);
    }
    FileUtils.ensureDirExistsForFile(filePath);
    fs.writeFileSync(filePath, content);
  }

  static copyFile(src, dst) {
    FileUtils.ensureDirExistsForFile(dst);
    fs.copyFileSync(src, dst);
  }

  static getImportPathRelativeToParent(rootDir, parentFile, importPath) {
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

class RecursiveDirectoryProcessor {
  constructor(rootDir, predicate, dirEntryAction) {
    this.rootDir = rootDir;
    this.predicate = predicate;
    this.dirEntryAction = dirEntryAction;
  }
  process(dirNameRelativeToRoot) {
    const entries = fs.readdirSync(path.join(this.rootDir, dirNameRelativeToRoot), {withFileTypes: true});
    for(const dirEnt of entries) {
      const dirEntPath = path.join(dirNameRelativeToRoot, dirEnt.name);
      if(dirEnt.isDirectory()) {
        this.process(dirEntPath);
        continue;
      }
      if(this.predicate(dirEnt, dirEntPath)) {
        this.dirEntryAction(dirEnt, dirEntPath);
      }
    }
  }
}

class DirectoryCopier {
  constructor(rootDir, outputRootDir) {
    this.rootDir = rootDir;
    this.outputRootDir = outputRootDir;
  }
  copyRecursively(dirNameRelativeToRoot, predicate) {
    const dirEntPredicate = (dirEnt, dirEntPath) => {
      return (dirEnt.isFile() || dirEnt.isSymbolicLink()) && predicate(dirEntPath)
    };

    const copyAction = (dirEnt, dirEntPath) => {
      FileUtils.copyFile(path.join(this.rootDir, dirEntPath), path.join(this.outputRootDir, dirEntPath));
    };
    const copyProcessor = new RecursiveDirectoryProcessor(this.rootDir, dirEntPredicate, copyAction);
    copyProcessor.process(dirNameRelativeToRoot);
  }
}

module.exports = {
  FileUtils,
  RedirectsResolver,
  RecursiveDirectoryProcessor,
  DirectoryCopier
};
