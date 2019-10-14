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

import * as fs from "fs";
import * as path from "path";
import {LegacyPolymerComponent, LegacyPolymerComponentParser} from './funcToClassConversion/polymerComponentParser';
import {ClassBasedPolymerElement} from './funcToClassConversion/polymerElementBuilder';
import {PolymerFuncToClassBasedConverter} from './funcToClassConversion/funcToClassBasedElementConverter';
import {LegacyPolymerFuncReplacer} from './funcToClassConversion/legacyPolymerFuncReplacer';
import {UpdatedFileWriter} from './funcToClassConversion/updatedFileWriter';
import {CommandLineParser} from './utils/commandLineParser';

interface UpdaterParameters {
  htmlFiles: Set<string>;
  jsFiles: Set<string>;
  out: string;
  inplace: boolean;
  writeOutput: boolean;
  rootDir: string;
}

interface InputFilesFilter {
  includeDir(path: string): boolean;
  includeFile(path: string): boolean;
}

function addFile(filePath: string, params: UpdaterParameters, filter: InputFilesFilter) {
  const parsedPath = path.parse(filePath);
  const ext = parsedPath.ext.toLowerCase();
  const relativePath = path.relative(params.rootDir, filePath);
  if(!filter.includeFile(relativePath)) return;
  if(relativePath.startsWith("../")) {
    throw new Error(`${filePath} is not in rootDir ${params.rootDir}`);
  }
  if(ext === ".html") {
    params.htmlFiles.add(relativePath);
  } if(ext === ".js") {
    params.jsFiles.add(relativePath);
  }
}

function addDirectory(dirPath: string, params: UpdaterParameters, recursive: boolean, filter: InputFilesFilter): void {
  const entries = fs.readdirSync(dirPath, {withFileTypes: true});
  for(const entry of entries) {
    const dirEnt = entry as fs.Dirent;
    const fullPath = path.join(dirPath, dirEnt.name);
    const relativePath = path.relative(params.rootDir, fullPath);
    if(dirEnt.isDirectory()) {
      if (!filter.includeDir(relativePath)) continue;
      if(recursive) {
        addDirectory(fullPath, params, recursive, filter);
      }
    }
    else if(dirEnt.isFile()) {
      addFile(fullPath, params, filter);
    } else {
      throw Error(`Unsupported dir entry '${entry.name}' in '${fullPath}'`);
    }
  }
}

async function updateLegacyComponent(component: LegacyPolymerComponent, params: UpdaterParameters) {
  const classBasedElement: ClassBasedPolymerElement = PolymerFuncToClassBasedConverter.convert(component);

  const replacer = new LegacyPolymerFuncReplacer(component);
  const replaceResult = replacer.replace(classBasedElement);
  try {
    const writer = new UpdatedFileWriter(component, params);
    writer.write(replaceResult, classBasedElement.eventsComments, classBasedElement.generatedComments);
  }
  finally {
    replaceResult.dispose();
  }
}

async function main() {
  const params: UpdaterParameters = await getParams();
  if(params.jsFiles.size === 0) {
    console.log("No files found");
    return;
  }
  const legacyPolymerComponentParser = new LegacyPolymerComponentParser(params.rootDir, params.htmlFiles)
  for(const jsFile of params.jsFiles) {
    console.log(`Processing ${jsFile}`);
    const legacyComponent = await legacyPolymerComponentParser.parse(jsFile);
    if(legacyComponent) {
      await updateLegacyComponent(legacyComponent, params);
      continue;
    }
  }
}

interface CommandLineParameters {
  src: string[];
  recursive: boolean;
  excludes: string[];
  out: string;
  inplace: boolean;
  noOutput: boolean;
  rootDir: string;
}

async function getParams(): Promise<UpdaterParameters> {
  const parser = new CommandLineParser({
    src: CommandLineParser.createStringArrayOption("src", ".js file or folder to process", []),
    recursive: CommandLineParser.createBooleanOption("r", "process folder recursive", false),
    excludes: CommandLineParser.createStringArrayOption("exclude", "List of file prefixes to exclude. If relative file path starts with one of the prefixes, it will be excluded", []),
    out: CommandLineParser.createStringOption("out", "Output folder.", null),
    rootDir: CommandLineParser.createStringOption("root", "Root directory for src files", "/"),
    inplace: CommandLineParser.createBooleanOption("i", "Update files inplace", false),
    noOutput: CommandLineParser.createBooleanOption("noout", "Do everything, but do not write anything to files", false),
  });
  const commandLineParams: CommandLineParameters = parser.parse(process.argv) as CommandLineParameters;

  const params: UpdaterParameters = {
    htmlFiles: new Set(),
    jsFiles: new Set(),
    writeOutput: !commandLineParams.noOutput,
    inplace: commandLineParams.inplace,
    out: commandLineParams.out,
    rootDir: path.resolve(commandLineParams.rootDir)
  };

  if(params.writeOutput && !params.inplace && !params.out) {
    throw new Error("You should specify output directory (--out directory_name)");
  }

  const filter = new ExcludeFilesFilter(commandLineParams.excludes);
  for(const srcPath of commandLineParams.src) {
    const resolvedPath = path.resolve(params.rootDir, srcPath);
    if(fs.lstatSync(resolvedPath).isFile()) {
      addFile(resolvedPath, params, filter);
    } else {
      addDirectory(resolvedPath, params, commandLineParams.recursive, filter);
    }
  }
  return params;
}

class ExcludeFilesFilter implements InputFilesFilter {
  public constructor(private readonly excludes: string[]) {
  }
  includeDir(path: string): boolean {
    return this.excludes.every(exclude => !path.startsWith(exclude));
  }

  includeFile(path: string): boolean {
    return this.excludes.every(exclude => !path.startsWith(exclude));
  }
}

main().then(() => {
  process.exit(0);
}).catch(e => {
  console.error(e);
  process.exit(1);
});
