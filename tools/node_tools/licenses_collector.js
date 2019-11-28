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

const path = require('path');
const fs = require('fs');

function resolve(topLevelAbsPath, baseDirAbsPath, moduleName) {
  const dirAbsPath = path.join(baseDirAbsPath, "node_modules", moduleName);
  if(fs.existsSync(dirAbsPath)) {
    if(!fs.lstatSync(dirAbsPath).isDirectory()) {
      console.error(`${dirAbsPath} is not a directory`);
      process.exit(1);
    }
    return dirAbsPath;
  }
  if(baseDirAbsPath === topLevelAbsPath) {
    console.error(`Can't resolve module '${moduleName}'`);
    process.exit(1);
  }
  return resolve(topLevelAbsPath, path.resolve(baseDirAbsPath, "../"), moduleName);

}

function addJsonWithDependenciesRecursively(targetMap, topLevelDir, packageJsonDir, includeDevDependencies) {
  if(targetMap.has(packageJsonDir)) {
    return;
  }
  const fileName = path.join(packageJsonDir, "package.json");
  const content = JSON.parse(fs.readFileSync(fileName));
  targetMap.set(packageJsonDir, {
    fileName,
    content
  });
  const allDependencies = Object.assign({}, content.dependencies ? content.dependencies : {});
  if(includeDevDependencies && content.devDependencies) {
    Object.assign(allDependencies, content.dependencies);
  }
  for(const depName in allDependencies) {
    if(!allDependencies.hasOwnProperty(depName)) {
      continue;
    }
    addJsonWithDependenciesRecursively(targetMap, topLevelDir, resolve(topLevelDir, packageJsonDir, depName), includeDevDependencies);
  }
}

function detectLicense(packageDir, packageInfo) {
  const packageJsonLicense = packageInfo.content.license;

  const licensesFiles = ["LICENSE"].map(name => path.resolve(packageDir, name)
    .filter(fs.existsSync).map(fs.readFileSync));


}

if(process.argv < 3) {
  console.log("Usage: node\n licenses_collector.js path_to_package.json");
  process.exit(1);
}

const licensesDir = path.join(__dirname, "license-templates");
const knownLicenses = fs.readdirSync(licensesDir)
  .filter(name => path.extname(name) === ".txt")
  .map(fileName => { return { licenseName: path.basename(fileName, ".txt"), content: fs.readFileSync(path.join(licensesDir, fileName), {encoding: 'utf-8'}) }});

const packages = new Map();
const topLevelDir = path.resolve(path.dirname(process.argv[2]));
addJsonWithDependenciesRecursively(packages, topLevelDir, topLevelDir, false);

console.log(packages.size);

// packages.forEach((packageInfo, packageDir) => {
//   detectLicense(packageDir, packageInfo);
// });
