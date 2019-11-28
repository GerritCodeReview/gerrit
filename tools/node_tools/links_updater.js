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

function getNewPathToFile(parentHtml, href) {
  const originalPath = '/' + FileUtils.getPathRelativeToRoot(parentHtml, href, true);

  const resolvedInfo = redirectResolver.resolve(originalPath, true);
  if (!resolvedInfo.isNpmModule && resolvedInfo.path === originalPath) {
    return href;
  }
  if (resolvedInfo.isNpmModule) {
    return '/node_modules/' + resolvedInfo.path;
  }
  if (href.startsWith('/')) {
    return resolvedInfo.path;
  }
  return FileUtils.getPathRelativeToRoot(dirEntPath, resolvedInfo.path);
}

function updateRefAttribute(node, parentHtml, attributeName) {
  const ref = dom5.getAttribute(node, attributeName);
  const newRef = getNewPathToFile(parentHtml, ref);
  if(newRef === ref) {
    return;
  }
  dom5.setAttribute(node,  attributeName, newRef);
}

function updateTestFileAst(rootDir, htmlFileName, document, webComponentTesterImportNode) {
  const importTestLibNode = new dom5.constructors.element("link");
  dom5.setAttribute(importTestLibNode, "rel", "import");
  dom5.setAttribute(importTestLibNode, "href", FileUtils.getImportPathRelativeToParent(rootDir, htmlFileName, "/test/common-test-lib.html"));
  dom5.replace(webComponentTesterImportNode, importTestLibNode);

  // const isLegacyImportInTest = (node) => {
  //   if(!isScriptWithSrcTag(node)) {
  //     return false;
  //   }
  //   const srcPath = FileUtils.getPathRelativeToRoot(htmlFileName, dom5.getAttribute(node, "src"));
  //   return [""].indexOf(srcPath) >= 0;
  // };
  // dom5.queryAll(isLegacyImportInTest).forEach((node) => dom5.remove(node));

}

const updateLinksAction = (dirEnt, dirEntPath) => {
  const filePath = path.join(rootDir, dirEntPath);
  const html = fs.readFileSync(filePath, "utf-8");
  const ast = parse5.parseFragment(html, {locationInfo: true});
  //dom5.removeFakeRootElements(ast)

  const isScriptWithSrcTag = (node) => node.tagName === "script" && dom5.hasAttribute(node, "src");
  const isWebComponentTesterImport = (node) => {
    return isScriptWithSrcTag(node) &&
        dom5.getAttribute(node, "src").endsWith("/bower_components/web-component-tester/browser.js");
  };

  const webComponentTesterImportNode = dom5.query(ast, isWebComponentTesterImport);
  if(webComponentTesterImportNode) {
    updateTestFileAst(rootDir, dirEntPath, ast, webComponentTesterImportNode);
  }

  const isHtmlImport = (node) => node.tagName === "link" && dom5.getAttribute(node, "rel") === "import"
      && dom5.hasAttribute(node, "href");
  const updateHtmlImportHref = (htmlImportNode) => updateRefAttribute(htmlImportNode, dirEntPath, "href");
  dom5.queryAll(ast, isHtmlImport).forEach(updateHtmlImportHref);

  const updateScriptSrc = (scriptTagNode) => updateRefAttribute(scriptTagNode, dirEntPath, "src");

  dom5.queryAll(ast, isScriptWithSrcTag).forEach(updateScriptSrc);
  const newContent = parse5.serialize(ast);
  fs.writeFileSync(filePath, newContent);
};

const htmlFileProcessor = new RecursiveDirectoryProcessor(rootDir, isHtmlFilePredicate, updateLinksAction);
htmlFileProcessor.process(dirToUpdate);
