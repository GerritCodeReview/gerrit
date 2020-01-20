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
var fs = require("fs");
var path = require("path");
var FileUtils = /** @class */ (function () {
    function FileUtils() {
    }
    FileUtils.getPathRelativeToRoot = function (parentHtml, fileHref) {
        if (fileHref.startsWith('/')) {
            return fileHref.substring(1);
        }
        return path.join(path.dirname(parentHtml), fileHref);
    };
    FileUtils.ensureDirExistsForFile = function (filePath) {
        var dirName = path.dirname(filePath);
        if (!fs.existsSync(dirName)) {
            fs.mkdirSync(dirName, { recursive: true, mode: 484 });
        }
    };
    FileUtils.saveFile = function (outputRootDir, relativePathToRoot, content) {
        var filePath = path.resolve(path.join(outputRootDir, relativePathToRoot));
        if (fs.existsSync(filePath) && fs.lstatSync(filePath).isSymbolicLink()) {
            throw new Error("Output file '" + filePath + "' is a symbolic link. Inplace update for links are not supported.");
        }
        FileUtils.ensureDirExistsForFile(filePath);
        fs.writeFileSync(filePath, content);
    };
    FileUtils.copyFile = function (src, dst) {
        FileUtils.ensureDirExistsForFile(dst);
        fs.copyFileSync(src, dst);
    };
    FileUtils.getImportPathRelativeToParent = function (rootDir, parentFile, importPath) {
        if (importPath.startsWith('/')) {
            importPath = importPath.substr(1);
        }
        var parentDir = path.dirname(path.resolve(path.join(rootDir, parentFile)));
        var fullImportPath = path.resolve(path.join(rootDir, importPath));
        var relativePath = path.relative(parentDir, fullImportPath);
        return relativePath.startsWith('../') ?
            relativePath : "./" + relativePath;
    };
    return FileUtils;
}());
var RedirectsResolver = /** @class */ (function () {
    function RedirectsResolver(redirects) {
        this.redirects = redirects;
    }
    RedirectsResolver.prototype.resolve = function (pathRelativeToRoot, resolveNpmModules) {
        var redirect = this._findRedirect(pathRelativeToRoot);
        if (!redirect) {
            return { path: pathRelativeToRoot, isNpmModule: false };
        }
        if (redirect.dst.npm_module) {
            return {
                path: resolveNpmModules ? this._resolveNpmModule(redirect.dst, redirect.pathToFile) : pathRelativeToRoot,
                isNpmModule: resolveNpmModules
            };
        }
        if (redirect.dst.dir) {
            var newDir = redirect.dst.dir;
            if (!newDir.endsWith('/')) {
                newDir = newDir + '/';
            }
            return { path: "" + newDir + redirect.pathToFile, isNpmModule: false };
        }
        throw new Error("Invalid redirect for path: " + pathRelativeToRoot);
    };
    RedirectsResolver.prototype._resolveNpmModule = function (npmRedirect, pathToFile) {
        if (npmRedirect.files && npmRedirect.files[pathToFile]) {
            pathToFile = npmRedirect.files[pathToFile];
        }
        return npmRedirect.npm_module + "/" + pathToFile;
    };
    RedirectsResolver.prototype._findRedirect = function (relativePathToRoot) {
        if (!relativePathToRoot.startsWith('/')) {
            relativePathToRoot = '/' + relativePathToRoot;
        }
        for (var _i = 0, _a = this.redirects; _i < _a.length; _i++) {
            var redirect = _a[_i];
            var normalizedFrom = redirect.from + (redirect.from.endsWith('/') ? '' : '/');
            if (relativePathToRoot.startsWith(normalizedFrom)) {
                return {
                    dst: redirect.to,
                    pathToFile: relativePathToRoot.substring(normalizedFrom.length)
                };
            }
        }
        return null;
    };
    return RedirectsResolver;
}());
var RecursiveDirectoryProcessor = /** @class */ (function () {
    function RecursiveDirectoryProcessor(rootDir, predicate, dirEntryAction) {
        this.rootDir = rootDir;
        this.predicate = predicate;
        this.dirEntryAction = dirEntryAction;
    }
    RecursiveDirectoryProcessor.prototype.process = function (dirNameRelativeToRoot) {
        var entries = fs.readdirSync(path.join(this.rootDir, dirNameRelativeToRoot), { withFileTypes: true });
        for (var _i = 0, entries_1 = entries; _i < entries_1.length; _i++) {
            var dirEnt = entries_1[_i];
            var dirEntPath = path.join(dirNameRelativeToRoot, dirEnt.name);
            if (dirEnt.isDirectory() || this.isLinkToDirectory(dirEnt, dirEntPath)) {
                this.process(dirEntPath);
                continue;
            }
            if (this.predicate(dirEnt, dirEntPath)) {
                this.dirEntryAction(dirEnt, dirEntPath);
            }
        }
    };
    RecursiveDirectoryProcessor.prototype.isLinkToDirectory = function (dirEntry, dirEntryPath) {
        return dirEntry.isSymbolicLink() &&
            fs.lstatSync(fs.realpathSync(path.join(this.rootDir, dirEntryPath))).isDirectory();
    };
    return RecursiveDirectoryProcessor;
}());
var DirectoryCopier = /** @class */ (function () {
    function DirectoryCopier(rootDir, outputRootDir) {
        this.rootDir = rootDir;
        this.outputRootDir = outputRootDir;
    }
    DirectoryCopier.prototype.copyRecursively = function (dirNameRelativeToRoot, predicate) {
        var _this = this;
        var dirEntPredicate = function (dirEnt, dirEntPath) {
            return (dirEnt.isFile() || dirEnt.isSymbolicLink()) && predicate(dirEntPath);
        };
        var copyAction = function (dirEnt, dirEntPath) {
            FileUtils.copyFile(path.join(_this.rootDir, dirEntPath), path.join(_this.outputRootDir, dirEntPath));
        };
        var copyProcessor = new RecursiveDirectoryProcessor(this.rootDir, dirEntPredicate, copyAction);
        copyProcessor.process(dirNameRelativeToRoot);
    };
    return DirectoryCopier;
}());
module.exports = {
    FileUtils: FileUtils,
    RedirectsResolver: RedirectsResolver,
    RecursiveDirectoryProcessor: RecursiveDirectoryProcessor,
    DirectoryCopier: DirectoryCopier
};
