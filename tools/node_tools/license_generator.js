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

const fs = require("fs");
const path = require("path");

function fail(message) {
  console.error(message);
  process.exit(1);
}

function createPackageInfoFromPackageJson(packageJsonFilePath) {
  const nameParts = [];
  const rootPath = path.dirname(packageJsonFilePath);
  let currentDir = rootPath;
  while(currentDir != "") {
    const partName = path.basename(currentDir);
    if(partName === "node_modules") {
      const version = JSON.parse(fs.readFileSync(packageJsonFilePath))["version"];
      if(!version) {
        fail(`Can't get version for ${packageJsonFilePath}`)
      }
      return {
        name: nameParts.reverse().join("/"),
        rootPath: rootPath,
        files: new Set(),
        version: version
      };
    }
    nameParts.push(partName);
    currentDir = path.dirname(currentDir);
  }
  fail(`Can't create package info for '${packageJsonFilePath}'`)
}

function  findPackageForFile(packagesPaths, filePath) {
  let currentDir = path.dirname(filePath);
  while(currentDir != "") {
    if(packagesPaths.has(currentDir)) {
      return packagesPaths.get(currentDir);
    }
    currentDir = path.dirname(currentDir);
  }
  fail(`Can't find package for '${filePath}'`);
}

function getPackages(files) {
  const packageJsonFiles = files.filter(f => path.basename(f) === "package.json");
  const packages = new Map();
  const packagesPaths = new Map();
  packageJsonFiles.map(packageJsonFilePath => createPackageInfoFromPackageJson(packageJsonFilePath))
    .forEach(p => {
      packages.set(p.name, p);
      packagesPaths.set(p.rootPath, p)
    });

  let i = 0;
  files.forEach(f => {
    i++;
    const p = findPackageForFile(packagesPaths, f);
    p.files.add(path.relative(p.rootPath, f));
  });
  return packages;
}

function validateObjectKeys(obj, allowedKeys) {
  const keys = Object.keys(obj);
  keys.forEach(k => {
    if(!allowedKeys.has(k)) {
      fail(`Object ${JSON.stringify(obj)} has invalid key '${k}'`);
    }
  })
}

const packageLicenseObjectKeys = new Set(["name", "versions", "include", "exclude", "license"]);
const licenseObjectKeys = new Set(["type", "source", "name"]);
function validatePackageLicense(packageLicense) {
  validateObjectKeys(packageLicense, packageLicenseObjectKeys);
  if(!packageLicense.name) {
    fail(`Package license object ${JSON.stringify(packageLicense)} must have 'name' propery`);
  }
  if(!packageLicense.license) {
    fail(`Package license object ${JSON.stringify(packageLicense)} must have 'license' propery`);
  }
  const license = packageLicense.license;
  validateObjectKeys(license, licenseObjectKeys);

  if(!license.type || !license.source || !license.name) {
    fail(`license for package ${packageLicense.name}' must have the following properties: 'type', 'source' and 'name'`);
  }
}

function filterLicensesByPackage(packagesLicenses, packageInfo) {
  return packagesLicenses.filter(packageLicense => {
    validatePackageLicense(packageLicense);

    if(packageInfo.name !== packageLicense.name) {
      return false;
    }
    if(!packageLicense.versions) {
      return true;
    }
    return packageLicense.versions.indexOf(packageInfo.version) >= 0;
  });
}

function filterFilesByLicense(license, files) {
  if(!license.include && !license.exclude) {
    return {
      filteredFiles: files,
      excludedFiles: []
    }
  }

  if(license.include && license.exclude) {
    fail("'include' and 'exclude' can't be used together");
  }

  const filteredFiles = [];
  const excludedFiles = [];

  const predicate = license.include ?
      f => license.include.some(prefix => prefix.startsWith(f)) :
      f => !license.exclude.some(prefix => prefix.startsWith(f));

  files.forEach(f => {
    if(predicate(f)) {
      filteredFiles.push(f);
    } else {
      excludedFiles.push(f);
    }
  });
  return {
    filteredFiles,
    excludedFiles
  }
}

function checkSingleLicensePerFile(licenses, pacakgeInfo) {
  const licensedFiles = new Set();
  licenses.forEach(lic => {
    lic.files.forEach(f => {
      if(licensedFiles.has(f)) {
        fail(`File '${f}' in '${packageInfo.name}' has multiple licenses`);
      }
    })
  });
}

function getLicensesForPackage(packagesLicenses, packageInfo) {
  const licensesInfo = filterLicensesByPackage(packagesLicenses, packageInfo);
  const licenses = [];
  licensesInfo.forEach((lic) => {
    const filterResult = filterFilesByLicense(lic, packageInfo.files);
    licenses.push({
      license: lic.license,
      files: filterResult.filteredFiles,
      excludedFiles: filterResult.excludedFiles,
      package: packageInfo
    })
  });
  checkSingleLicensePerFile(licenses);
  return licenses;
}

function substituteLicensesToPackages(licenses, packages) {
  const packageLicensesNames = new Set();
  const substituteLicense = pack => {
    const licenseInfoOrName = pack.license;
    if(typeof licenseInfoOrName !== "string") {
      if(packageLicensesNames.has(licenseInfoOrName.name) || licenses.has(licenseInfoOrName.name)) {
        fail(`License with the name '${lic.name}' defined more than once`);
      }
      packageLicensesNames.add(licenseInfoOrName.name);
      return pack;
    }
    const license = licenses.get(licenseInfoOrName);
    if(!license) {
      fail(`Unknown license '${licenseInfoOrName}' for package '${pack.name}'`);
    }
    return Object.assign(pack, {license: license});
  };
  return packages.map(p => substituteLicense(p));
}

function readLicenseText(licenseInfo, packages, licensesRootPath) {
  if(licenseInfo.source.startsWith("./")) {
    return fs.readFileSync(path.join(licensesRootPath, licenseInfo.source), {encoding: 'utf-8'});
  }
  if(packages.length !== 1) {
    fail(`License text from one package is shared with another package. This is not allowed. If you want to share license text between package, put it under node_modules_licenses/licenses folder`);
  }
  const licFileName = path.join(packages[0].package.rootPath, licenseInfo.source);
  return fs.readFileSync(licFileName, {encoding: 'utf-8'});
}

const licenseTypeKeys = new Set(["allowed"]);

function getPackageInfoForLicenseMap(package) {
  if(package.files.length) {
    return null;
  }
  return {
    name: package.package.name
  };
}

function getLicenseMapContent(packagesGroupedByLicense, licensesTypes, licensesRootPath) {
  const result = [];
  packagesGroupedByLicense.forEach((packages, licenseInfo) => {
    const licenseType = licensesTypes[licenseInfo.type];
    if(!licenseType) {
      fail(`Unknown license type '${licenseInfo.type}'`);
    }
    validateObjectKeys(licenseType, licenseTypeKeys);
    if(!licenseType.allowed) {
      fail(`The license type ${licenseInfo.type} is not allowed, but some files with this license exists`);
    }
    const text = readLicenseText(licenseInfo, packages, licensesRootPath);
    result.push({
      license_text: text,
      license_name: licenseInfo.name,
      packages: packages.map(p => getPackageInfoForLicenseMap(p)).filter(p => p !== null)
    });
  });
  return result.filter(item => item.packages.length > 0);
}

function groupLicensesByName(licensesInfo) {
  const result = new Map();
  licensesInfo.forEach(lic => {
    if(result.has(lic.name)) {
      fail(`License with the name '${lic.name}' defined more than once`);
    }
    result.set(lic.name, lic);
  });
  return result;
}

function main() {
  const licenseFilePath = process.argv[2];
  const licensesRootPath = path.dirname(licenseFilePath);
  const licensesInfo = JSON.parse(fs.readFileSync(licenseFilePath));
  const nameToLicense = groupLicensesByName(licensesInfo["licenses"]);
  const packagesLicenses = substituteLicensesToPackages(nameToLicense, licensesInfo["packages"]);
  const nodeModulesFiles = fs.readFileSync(process.argv[3], {encoding: 'utf-8'}).split(/\r?\n/).filter(f => f.length > 0);

  const packages = getPackages(nodeModulesFiles);
  const packagesGroupedByLicense = new Map();

  packages.forEach(p => {
    const packageLicenses = getLicensesForPackage(packagesLicenses, p);
    packageLicenses.forEach(packageLicense => {
      const license = packageLicense.license;
      if(!packagesGroupedByLicense.has(license)) {
        packagesGroupedByLicense.set(license, []);
      }
      packagesGroupedByLicense.get(license).push(packageLicense);
    })
  });
  const licenseMapContent = getLicenseMapContent(packagesGroupedByLicense, licensesInfo["license_types"], licensesRootPath);
  console.log(JSON.stringify(licenseMapContent, null, 2))
  fs.writeFileSync(process.argv[4], JSON.stringify(licenseMapContent));
}

main();
