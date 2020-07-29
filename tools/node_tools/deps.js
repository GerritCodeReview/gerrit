const fs = require('fs');
const path = require('path');
const ts = require('typescript');

function createPathResolver(pathToTsBuildInfo) {
  const baseDir = path.dirname(pathToTsBuildInfo);
  return filePath => {
    const fullPath = path.resolve(baseDir, filePath);
    if(fullPath.endsWith('.d.ts')) {
      return fullPath.slice(0, -'.d.ts'.length) + '.js';
    }
    return fullPath;
  }
}
function isModuleFile(fullPath) {
  return fullPath.indexOf('/node_modules/') >= 0;
}
function isSrcFile(fullPath) {
  return !isModuleFile(fullPath);
}
function isFileToConvert(fullPath) {
  return isSrcFile(fullPath) &&
      !fullPath.endsWith('.d.ts') &&
      !fullPath.endsWith('_html.ts') &&
      !fullPath.endsWith('_html.js') &&
      !fullPath.endsWith('_test.js') &&
      fullPath.indexOf('/polygerrit-ui/app/samples/') < 0 &&
      fullPath.indexOf('/polygerrit-ui/app/test/') < 0 &&
      !fullPath.endsWith('/polygerrit-ui/app/types/custom-externs.ts') &&
      !fullPath.endsWith('/polygerrit-ui/app/types/globals.ts');
}

function createReverseDependenciesMap(importsMap) {
  const result = new Map();
  importsMap.forEach((imports, from) => {
    imports.forEach(item => {
      if(!result.has(item)) {
        result.set(item, new Set());
      }
      result.set(item, result.get(item).add(from));
    });
  });
  importsMap.forEach((imports, from) => {
    if(!result.has(from)) {
      result.set(from, new Set());
    }
  });
  return result;
}

function unionSet(a, b) {
  return new Set([...a, ...b]);
}

function getDirectAndIndirectRevDepSet(reverseDirectDependencies, dep) {
  const items = reverseDirectDependencies.get(dep);
  if(!items) {
    return new Set();
  }
  return [...items].reduce((set, item) => unionSet(set, getDirectAndIndirectRevDepSet(reverseDirectDependencies, item)), items);
}

function allImportsConverted(imports, alreadyConvertedSet) {
  return imports.every(imp => alreadyConvertedSet.has(imp));
}

function calculateClosureMap(reverseDirectDependencies) {
  return [...reverseDirectDependencies].reduce(
      (result, [dep]) => result.set(dep, getDirectAndIndirectRevDepSet(reverseDirectDependencies, dep)), new Map());
}

function getAllCandidates(importsMap, alreadyConvertedSet, explain) {
  return [...importsMap]
      .filter(([from, imports]) => {
        if (explain) {
          console.log(`Checking the file: ${from}`);
        }
        if(alreadyConvertedSet.has(from)) {
          if(explain) {
            console.log(`Skip, already converted`);
          }
          return false;
        }
        if(!allImportsConverted(imports, alreadyConvertedSet)) {
          if(explain) {
            console.log(`Not all imports is converted`);
          }
          return false;
        }
        return true;
      })
      .map(([from, imports]) => from);
}

function getNewlyAvailableFilesAfterFix(fileName, imports, reverseDirectDependencies, alreadyConvertedSet) {
  return [...reverseDirectDependencies.get(fileName)]
      .filter(otherFile => imports.get(otherFile).every(imp => alreadyConvertedSet.has(imp) || imp === fileName))
}

function getNextItemToConvert(importsMap, reverseDirectDependencies,
    reverseDependenciesClosure, alreadyConverted, ignoreDepSet, explain) {
  const candidates = getAllCandidates(importsMap, alreadyConverted, explain);
  if(candidates.length === 0) {
    return null;
  }
  const newlyAvailable = candidates.reduce((map, candidate) => map.set(candidate, getNewlyAvailableFilesAfterFix(candidate, importsMap, reverseDirectDependencies, alreadyConverted)), new Map());

  const sortedCandidates = candidates
      .sort((a, b) => {
        // console.log(a);
        // console.log(b);
        // return reverseDependenciesClosure.get(b).size - reverseDependenciesClosure.get(a).size
        const diff1 = newlyAvailable.get(b).length - newlyAvailable.get(a).length;
        if(diff1) {
          return diff1;
        }
        const diff2 = reverseDirectDependencies.get(b).size - reverseDirectDependencies.get(a).size;
        if(diff2) {
          return diff2;
        }
        return reverseDependenciesClosure.get(b).size - reverseDependenciesClosure.get(a).size;
      });

  const fileName = sortedCandidates[0];
  return {
    fileName,
    dependsOnAlreadyConverted: [...importsMap.get(fileName)].filter(item => alreadyConverted.has(item) && !ignoreDepSet.has(item)),
    directDependand: reverseDirectDependencies.get(fileName).size,
    allDependant: reverseDependenciesClosure.get(fileName).size,
    newlyAvailable: newlyAvailable.get(fileName).length,
  };
}

function createTsProgram(tsconfigFilePath)  {
  const config = ts.parseJsonConfigFileContent(
      ts.readConfigFile(tsconfigFilePath, ts.sys.readFile).config,
      {
        fileExists: ts.sys.fileExists,
        readFile: ts.sys.readFile,
        readDirectory: ts.sys.readDirectory,
      },
      path.dirname(tsconfigFilePath),
  );
  return ts.createProgram({
    options: config.options,
    rootNames: config.fileNames,
  });
}

function getAllImports(sourceFile) {
  const dirname = path.dirname(sourceFile.fileName);
  const importResolver = moduleSpecifierText =>
      moduleSpecifierText.startsWith('./')
      || moduleSpecifierText.startsWith('../') ?
          path.resolve(dirname, moduleSpecifierText) : null;

  const fixExtension = path => {
    if(fs.existsSync(path)) {
      return path;
    }
    if(fs.existsSync(path + '.js')) {
      return path + '.js';
    }
    if(fs.existsSync(path + '.ts')) {
      return path + '.ts';
    }
    if(path.endsWith('.js')) {
      const tsPath = path.slice(0, -'.js'.length) + '.ts';
      if(fs.existsSync(tsPath)) {
        return tsPath;
      }

    }
    throw Error(`Can't find import: ${path}`);
  };


  return sourceFile
      .statements.filter(ts.isImportDeclaration)
      .map(decl => decl.moduleSpecifier.text)
      .map(moduleSpecifierText => importResolver(moduleSpecifierText))
      .filter(path => path)
      .filter(isFileToConvert)
      .map(fixExtension);
}

async function main() {
  const tsconfigFullPath = path.resolve(process.cwd(), process.argv[2]);
  const tsProgram = createTsProgram(tsconfigFullPath);
  // console.log(tsProgram.getSourceFiles().map(s => s.fileName).join('\n'));
  const sourceFiles = tsProgram.getSourceFiles().filter(sourceFile => isFileToConvert(sourceFile.fileName));
  const importsMap = sourceFiles.reduce(
      (map, sourceFile) =>
          map.set(sourceFile.fileName, getAllImports(sourceFile)), new Map());
  //console.log(importsMap);

  // const pathToTsBuildInfo = path.resolve(process.cwd(), process.argv[2]);
  //
  // const tsbuildInfo = JSON.parse(
  //     fs.readFileSync(pathToTsBuildInfo, {encoding: 'utf-8'}));
  // const referencedMap = tsbuildInfo.program.referencedMap;
  // const resolvePath = createPathResolver(pathToTsBuildInfo);
  //
  // const importsMap =  Object.keys(referencedMap)
  //     .map(key => {
  //       return {
  //         from: resolvePath(key),
  //         imports: new Set(referencedMap[key].map(resolvePath).filter(isFileToConvert))
  //       }
  //     })
  //     .filter(item => isFileToConvert(item.from))
  //     .reduce((map, value) => map.set(value.from, value.imports), new Map());

  const reverseDirectDependencies = createReverseDependenciesMap(importsMap);
  // console.log(reverseDirectDependencies);
  const reverseDependenciesClosure = calculateClosureMap(reverseDirectDependencies);

  // console.log(importsMap);
  // console.log([...importsMap].filter(([from, imports]) => imports.length === 0).map(([from, imports]) => from).length);
  // console.log(importsMap.size);
  //
  // console.log(reverseDirectDependencies);

  // console.log(reverseDependenciesClosure);
  //
  const alreadyConverted = new Set([
       //'/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/styles/shared-styles.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/services/app-context.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/url-util.ts',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/scripts/rootElement.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/constants/constants.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/patch-set-util.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/dom-util.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/date-util.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-etag-decorator.js',
    // '/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.js',
  ]);
  // console.log(getNextItemToConvert(importsMap, reverseDirectDependencies, reverseDependenciesClosure, alreadyConverted));
  //
  // console.log(importsMap.size);
  //const newAvailable = getNewlyAvailableFilesAfterFix('/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/url-util.ts', importsMap, reverseDirectDependencies, alreadyConverted);
  //console.log(newAvailable);
  // alreadyConverted.add('/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/url-util.ts');
  // alreadyConverted.add('/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/constants/constants.js');
  // alreadyConverted.add('/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/display-name-util.js');
  // alreadyConverted.add('/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/elements/core/gr-navigation/gr-navigation.js');
  // alreadyConverted.add('/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/services/app-context.js');
  // alreadyConverted.add('/Users/dmfilippov/gerrit/gerrit-2/polygerrit-ui/app/utils/patch-set-util.js');
  // console.log(getNextItemToConvert(importsMap, reverseDirectDependencies, reverseDependenciesClosure, alreadyConverted));
  importsMap.forEach((imports, from) => {
    if(from.endsWith('.ts') && !from.endsWith('.d.ts')) {
      alreadyConverted.add(from);
      alreadyConverted.add(from.slice(0, -'.ts'.length) + '.js');
    }
  });
  const ignoreDepSet = new Set(alreadyConverted);
  const maxFilesToConvert = parseInt(process.argv[3]);
  const withDepsCandidates = [];


  const relPath = (filePath) => path.relative(path.dirname(tsconfigFullPath), filePath);

  for(let i = 0; i < maxFilesToConvert; i++) {
    const candidate = getNextItemToConvert(importsMap, reverseDirectDependencies, reverseDependenciesClosure, alreadyConverted, ignoreDepSet);
    if(!candidate) {
      break;
    }
    console.log(relPath(candidate.fileName), candidate.dependsOnAlreadyConverted.map(relPath).length > 0 ? '*' : '');
    if(candidate.dependsOnAlreadyConverted.length > 0) {
      withDepsCandidates.push(candidate);

    }
    // if(candidate.dependsOnAlreadyConverted.length > 0) {
    //   console.log(`Depends on:`);
    //   console.log(candidate.dependsOnAlreadyConverted);
    // }
    alreadyConverted.add(candidate.fileName);
  }
  console.log('---------Dependencies------------');
  for(const candidate of withDepsCandidates) {
    console.log(relPath(candidate.fileName));
    console.log(candidate.dependsOnAlreadyConverted.map(relPath));
  }
}

main().then().catch(err => {
  setTimeout(() => {
    console.error(err);
    process.exit(1);
  }, 1000);
});
