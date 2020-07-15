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

function getDirectAndIndirectRevDepSet(reverseDirectDependencies, dep, alreadyVisited) {
  const items = reverseDirectDependencies.get(dep);
  if(!items) {
    return new Set();
  }
  if(!alreadyVisited) {
    alreadyVisited = new Set();
  }
  alreadyVisited.add(dep);
  try {
    return [...items].reduce(
        (set, item) => alreadyVisited.has(item) ? set : unionSet(set,
            getDirectAndIndirectRevDepSet(reverseDirectDependencies, item,
                alreadyVisited)), items);
  }
  finally {
    alreadyVisited.delete(dep);
  }
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

function  getNextItemToConvert(importsMap, reverseDirectDependencies,
    reverseDependenciesClosure, alreadyConverted, ignoreDepSet, explain) {
  const candidates = getAllCandidates(importsMap, alreadyConverted, explain);
  if(candidates.length === 0) {
    return null;
  }
  const newlyAvailable = candidates.reduce((map, candidate) => map.set(candidate, getNewlyAvailableFilesAfterFix(candidate, importsMap, reverseDirectDependencies, alreadyConverted)), new Map());

  const sortedCandidates = candidates
      .sort((a, b) => {
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

const nodeIdToLabelMap = new Map();
let nextNodeIndex = 1;
const urlPrefix = "https://gerrit.googlesource.com/gerrit/+/master/polygerrit-ui/app/";
function getNodeId(digraph, filePath, attrs) {
  const id = nodeIdToLabelMap.get(filePath);
  if(id) {
    return id;
  }
  const nodeId = `n${nextNodeIndex}`;
  nextNodeIndex++;
  nodeIdToLabelMap.set(filePath, nodeId);
  digraph.push(`${nodeId} [label="${path.basename(filePath, '.js')}" tooltip="${filePath}" URL="${urlPrefix}${filePath}" ${attrs ? attrs : ''}]`);
  return nodeId;
}

async function main() {
  const tsconfigFullPath = path.resolve(__dirname, '../../polygerrit-ui/app/tsconfig.json');
  const tsProgram = createTsProgram(tsconfigFullPath);
  const sourceFiles = tsProgram.getSourceFiles().filter(sourceFile => isFileToConvert(sourceFile.fileName));
  const importsMap = sourceFiles.reduce(
      (map, sourceFile) =>
          map.set(sourceFile.fileName, getAllImports(sourceFile)), new Map());

  const reverseDirectDependencies = createReverseDependenciesMap(importsMap);
  const reverseDependenciesClosure = calculateClosureMap(reverseDirectDependencies);
  const alreadyConverted = new Set();
  importsMap.forEach((imports, from) => {
    if(from.endsWith('.ts') && !from.endsWith('.d.ts')) {
      alreadyConverted.add(from);
      alreadyConverted.add(from.slice(0, -'.ts'.length) + '.js');
    }
  });
  const ignoreDepSet = new Set(alreadyConverted);
  const maxFilesToConvert = 1000;
  const withDepsCandidates = [];


  const relPath = (filePath) => path.relative(path.dirname(tsconfigFullPath), filePath);

  const withoutDepsCandidates = [];
  for(let i = 0; i < maxFilesToConvert; i++) {
    const candidate = getNextItemToConvert(importsMap, reverseDirectDependencies, reverseDependenciesClosure, alreadyConverted, ignoreDepSet);
    if(!candidate) {
      break;
    }
    if(candidate.dependsOnAlreadyConverted.length > 0) {
      withDepsCandidates.push(candidate);
    } else {
      withoutDepsCandidates.push(candidate)
    }
    alreadyConverted.add(candidate.fileName);
  }
  digraph = [];
  for(const candidate of withoutDepsCandidates) {
    getNodeId(digraph, relPath(candidate.fileName), 'fillcolor="green4" style="filled"');
  }
  let depsInfo = [];
  for(const candidate of withDepsCandidates) {
    const source = relPath(candidate.fileName);
    const dependencies = candidate.dependsOnAlreadyConverted.map(relPath);
    for(const dep of dependencies) {
      digraph.push(`${getNodeId(digraph, source)} -> ${getNodeId(digraph, dep)} [tooltip="${dep}"]`);
    }
    depsInfo.push(source + '\n' + dependencies.map(d => `    ${d}`).join('\n'))
  }
  const txtFile = path.join(process.cwd(), 'files_js_to_ts.txt');
  const htmlFile = path.join(process.cwd(), 'files_js_to_ts.html');
  fs.writeFileSync(txtFile, [
      "---------Ready to convert----------",
      withoutDepsCandidates.map(candidate => relPath(candidate.fileName)).join('\n'),
      "---------Dependencies--------------",
    depsInfo.join('\n'),
  ].join('\n'));
  fs.writeFileSync(htmlFile, `<html>
<head>
  <script language="javascript" type="text/javascript" src="https://graphviz.corp.google.com/js"></script>
</head>
<body>
<!-- Create a 'script' element with 'type="text/dot"' and class equal to the layout -->
<!-- engine to use: dot, neato, fdp, sfdp, twopi, or circo -->
<script type="text/dot" class="dot" format="svg">
digraph {
${digraph.join('\n')}
}
</script>
</body>
</html>
`)

  const readyToConvert = withoutDepsCandidates.length;
  const jsFilesLeft = readyToConvert + withDepsCandidates.length;
  console.log(`Ready to convert: ${readyToConvert} files`);
  console.log(`JS files left (total): ${jsFilesLeft} files`);
  console.log(`Generated files:\n${txtFile}\nfile://${htmlFile}`);
}

main().then().catch(err => {
  setTimeout(() => {
    console.error(err);
    process.exit(1);
  }, 1000);
});
