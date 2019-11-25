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
const parse5 = require("parse5");
const dom5 = require("dom5");
const path = require("path");
const {FileUtils, RedirectsResolver, RecursiveDirectoryProcessor} = require('./code_utils');


if(process.argv.length < 5) {
  console.info("Usage:\nnode links_updater.js rootDir dirToUpdate redirectFile\n");
  process.exit(1);
}

const rootDir = path.resolve(process.cwd(), process.argv[2]);
const dirToUpdate = path.relative(rootDir, path.resolve(rootDir, process.argv[3]));
const redirectFile = path.resolve(rootDir, process.argv[4]);
const redirectResolver = new RedirectsResolver(JSON.parse(fs.readFileSync(redirectFile)).redirects);

const isHtmlFilePredicate = (dirEnt, dirEntPath) => {
  return dirEnt.isFile() && path.extname(dirEntPath) === ".html";
};

const updateLinksAction = (dirEnt, dirEntPath) => {
  const filePath = path.join(rootDir, dirEntPath);
  const html = fs.readFileSync(filePath, "utf-8");
  const ast = parse5.parse(html, {locationInfo: true});
  const isHtmlImport = (node) => node.tagName === "link" && dom5.getAttribute(node, "rel") === "import"
      && dom5.hasAttribute(node, "href");
  dom5.nodeWalkAll(ast, isHtmlImport).forEach((htmlImportNode) => {
    const href = dom5.getAttribute(htmlImportNode, "href");
    const originalPath = FileUtils.getPathRelativeToRoot(dirEntPath, href);
    const resolvedInfo = redirectResolver.resolve(originalPath, true);
    if(!resolvedInfo.isNpmModule && resolvedInfo.path === originalPath) {
      return;
    }
    let newHref;
    if(resolvedInfo.isNpmModule) {
      newHref = '/node_modules/' + resolvedInfo.path;
    } else {
      newHref = resolvedInfo.path;
      if(!href.startsWith('/')) {
        newHref = FileUtils.getPathRelativeToRoot(dirEntPath, newHref);
      }
    }
    dom5.setAttribute(htmlImportNode, "href", newHref);
  });
  const newContent = parse5.serialize(ast);
  fs.writeFileSync(filePath, newContent);
};

const htmlFileProcessor = new RecursiveDirectoryProcessor(rootDir, isHtmlFilePredicate, updateLinksAction);
htmlFileProcessor.process(dirToUpdate);
