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

import {SrcWebSite} from "../utils/web-site-utils";
import {FilePath} from "../utils/file-utils";
import * as parse5 from "parse5";
import * as dom5 from "dom5";
import * as path from "path";
import {Node} from 'dom5';
import * as fs from "fs";


interface HtmlFileConversionResult {
  files: Map<FilePath, string>;
}

interface HtmlFileConverter {
  convert(fileName: FilePath, pathResolver: SrcWebSite): HtmlFileConversionResult;
}

type FilePathPredicate = (fileName: FilePath) => boolean;

interface JsFileBuilder {

}

function update(fragment: parse5.AST.DocumentFragment, jsFileBuilder: JsFileBuilder) {

}

function getAst(file: FilePath): parse5.AST.DocumentFragment {
  const html = fs.readFileSync(file, "utf-8");
  const fragment = parse5.parseFragment(html, {locationInfo: true});
  update(fragment);
}



function convertBehavior(htmlFile: FilePath) {
  const ast = getAst(htmlFile);

}

convertBehavior("/usr/local/google/home/dmfilippov/gerrit-p2/polygerrit-ui/app/behaviors/async-foreach-behavior/async-foreach-behavior.html" as FilePath);
