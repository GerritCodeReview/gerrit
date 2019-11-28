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

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function exitWithError(message) {
  console.error(message);
  process.exit(1);
}

function checkYarnLockUnchanged(rootFolder) {
  const yarnLock = fs.readFileSync(path.join(rootFolder, "yarn.lock"));
  const yarnLockHash = crypto
    .createHmac('sha256', '')
    .update(yarnLock)
    .digest('hex');

  const expectedHash = fs.readFileSync(path.join(rootFolder, "node_modules_licenses", "yarn-lock.sha256"), {encoding: 'utf-8'}).trim();
  if(expectedHash != yarnLockHash) {
    exitWithError(`yarn.lock file was changed. Please check, that all licenses are valid and then update yarn-lock.sha256 file in node_modules_licenses folder. The new sha256 is: ${yarnLockHash}.`);
  }
}

/**
 * Returns array of { path: string, license: { ... } } sorted
 * descending by path length - i.e. most specific path is always first
 */
function getSortedByPathExceptions(licenseInfo) {
  if(!licenseInfo.exceptions) {
    return [];
  }
  const result = [];
  for(const exception in licenseInfo.exceptions) {
    result.push(...exception.paths.map(path => { return {path, license: exception} }));
  }

  result.sort((a, b) => b.path.length - a.path.length);
  return result;

}

function getLicensesSortedByPath(rootFolder, relativePathToModule, licenseInfo) {
  const sortedByPathLicenses = getSortedByPathExceptions(relativePathToModule, licenseInfo);
  sortedByPathLicenses.push({path: null, license: licenseInfo.default});
  return sortedByPathLicenses.map(l => {
    let actualPath = l.path;
    if(actualPath && fs.lstatSync(path.join(rootFolder, "node_modules", relativePathToModule, actualPath)).isDirectory()) {
      if(!actualPath.endsWith('/')) {
        actualPath = actualPath + '/';
      }
    }
    return {
      path: actualPath,
      licenseInfo: {
        licenseType: l.license.licenseType,
        licenseText: getLicenseText(rootFolder, relativePathToModule,
            l.license.licenseSource)
      }
    }
  });
}

function getLicenseText(rootFolder, relativePathToModule, licenseSource) {
  if(!licenseSource) {
    return null;
  }
  if(licenseSource.packageFile) {
    return fs.readFileSync(path.join(rootFolder, "node_modules", relativePathToModule, licenseSource.packageFile), {encoding: 'utf-8'});
  } else if(licenseSource.localFile) {
    return fs.readFileSync(path.join(rootFolder, "node_modules_licenses", relativePathToModule, licenseSource.localFile), {encoding: 'utf-8'});
  } else if(licenseSource.sharedFile) {
    return fs.readFileSync(path.join(rootFolder, "node_modules_licenses", licenseSource.sharedFile), {encoding: 'utf-8'});
  } else {
    exitWithError(`Invalid license source for ${relativePathToModule}`);
  }
}

function validateLicenseJson(relativePathToModule, licenseJson) {

}

function getLicenseForModule(rootFolder, relativePathToModule, defaultLicense) {
  const modulePath = path.join(rootFolder, "node_modules", relativePathToModule);
  const licenseInfoFileName = path.join(rootFolder, "node_modules_licenses", relativePathToModule, "license.json");
  let licenseInfo = defaultLicense;
  if(fs.existsSync(licenseInfoFileName)) {
     licenseInfo = JSON.parse(fs.readFileSync(licenseInfoFileName));
     validateLicenseJson(licenseJson);
  }
  return getLicensesSortedByPath(rootFolder, relativePathToModule, licenseInfo);
}

function shouldApplyLicense(pathRelativeToModule, licensePath) {
  if(!licensePath) {
    return true;
  }
  if(pathRelativeToModule === licensePath) {
    return true;
  }
  if(licensePath.endsWith("/") && pathRelativeToModule.startsWith(licensePath)) {
    //Path to directory
    return true;
  }
  return false;
}
function getLicenseForFile(pathRelativeToModule, licensesByPath) {
  for(const licenseWithPath of licensesByPath) {
    if(shouldApplyLicense(pathRelativeToModule, licenseWithPath.path)) {
      return licenseWithPath.licenseInfo;
    }

  }
  exitWithError(`Can't find license for file ${pathRelativeToModule}`);
}

/**
 * Returns Map<string, {licenseType: string, licenseText?: string}> for each file
 * Key is a path relative to node_modules
 * */
function collectFilesLicensesRecursively(rootFolder, parentNodeModuleRelativePath, relativePath, parentLicenseInfo, defaultLicense) {
  //rootFolder must contain node_modules, node_modules_licenses and yarn.lock
  //parentNodeModuleRelativePath is a path relative to node_modules. Point to the root folder of a current module
  //relativePath - path relative to current module;

  const nodeModulesFolderPath = path.join(rootFolder, "node_modules", parentNodeModuleRelativePath, relativePath);

  const isNodeModules = fs.existsSync(path.join(nodeModulesFolderPath, "package.json"));
  if(isNodeModules) {
    parentNodeModuleRelativePath = path.join(parentNodeModuleRelativePath, relativePath);
    relativePath = "";
  }
  const actualLicenseInfo = isNodeModules ? getLicenseForModule(rootFolder, parentNodeModuleRelativePath, defaultLicense) : parentLicenseInfo;

  const entries = fs.readdirSync(nodeModulesFolderPath, {withFileTypes: true});
  const result = new Map();
  for(const entry of entries) {
    const relativeEntryPath = path.join(relativePath, entry.name);
    if(entry.isFile() || entry.isSymbolicLink()) {
      const license = getLicenseForFile(relativeEntryPath,  actualLicenseInfo);
      result.set(path.join(parentNodeModuleRelativePath, relativeEntryPath), {licenseType: license.licenseType, licenseText: license.licenseText});
    } else if(entry.isDirectory) {
      const nestedInfo = collectFilesLicensesRecursively(rootFolder, parentNodeModuleRelativePath, relativeEntryPath, actualLicenseInfo, defaultLicense);
      nestedInfo.forEach((v, k) => result.set(k, v))
    } else {
      exitWithError(`Not supported directory entry '${relativeEntryPath}'`);
    }
  }
  return result;
}

if(process.argv.length < 3) {
  exitWithError(`Usage: node license_checker.js root_folder`);
}
const rootFolder = path.resolve(process.argv[2]);
checkYarnLockUnchanged(rootFolder);
const defaultLicense = JSON.parse(fs.readFileSync(path.join(rootFolder, "node_modules_licenses", "default-license.json")));
const licensesPerFile = collectFilesLicensesRecursively(rootFolder, ".", ".", getLicensesSortedByPath(rootFolder, ".", defaultLicense), defaultLicense);
licensesPerFile.forEach((v, k) => {
  console.log(k, v.licenseType);
})
