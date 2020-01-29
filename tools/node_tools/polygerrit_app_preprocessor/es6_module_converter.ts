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
//import * as dom5 from "dom5";
import * as path from "path";
import * as fs from "fs";
import {AST} from "parse5";
//import {Node} from 'dom5';
import CommentNode = AST.Default.CommentNode;
import TextNode = AST.Default.TextNode;
import DocumentType = AST.Default.DocumentType;
import Document = AST.Default.Document;
import DocumentFragment = AST.Default.DocumentFragment;
import Element = AST.Default.Element;
import {fail} from '../utils/common';

interface HtmlFileConversionResult {
  files: Map<FilePath, string>;
}

interface HtmlFileConverter {
  convert(fileName: FilePath, pathResolver: SrcWebSite): HtmlFileConversionResult;
}

type FilePathPredicate = (fileName: FilePath) => boolean;

interface JsFileBuilder {

}
//
// function isCommentNode(node: Node): node is CommentNode {
//   return node.nodeName === '#comment';
// }
//
// function isTextNode(node: Node): node is TextNode {
//   return node.nodeName === '#text';
// }
//
// function isDocumentTypeNode(node: Node): node is DocumentType {
//   return node.nodeName === '#documentType';
// }
//
// function isDocumentNode(node: Node): node is Document {
//   return node.nodeName === '#document';
// }
//
// function isDocumentFragmentNode(node: Node): node is DocumentFragment {
//   return node.nodeName === '#document-fragment';
// }
//
// function isElementNode(node: Node): node is Element {
//   return !node.nodeName.startsWith('#') && node.hasOwnProperty("tagName");
// }

function getJsImportInsteadOfHtml(htmlHref: string): string {
  const htmlExt = ".html";
  if(!href.endsWith(htmlExt)) {
    fail(`Href is not an html file: ${href}`);
  }
  //TODO: support import from node_modules
  return href.substr(0, href.length - htmlExt.length) + ".js";

}

function walkTree(node: Node, nodeProcessor: (node:))

function update(fragment: DocumentFragment, jsFileBuilder: JsFileBuilder) {
  const nodesToRemove: Node[] = [];

  for(const node of fragment.childNodes) {
    if(node.tagName === 'link' && dom5.getAttribute(node, "rel") === "import" && dom5.hasAttribute(node, "href")) {
      const href = dom5.getAttribute(node, "href");
      jsFileBuilder.addImportStatement(getJsImportInsteadOfHtml(href));
      nodesToRemove.push(node);
      continue;
    }

    //console.log(node);
    if(isCommentNode(node) || isTextNode(node)) {
      continue;
    } else if(isElementNode(node)) {

      if(node.tagName === "script") {
        //console.log(node);
        console.log(node.attrs);

        nodesToRemove.push(node);
        //if(node.attrs)
        continue;
      }
      continue;
    }
    fail(`Unsupported node type: '${node.nodeName}'`);
  }
}

function getAst(file: FilePath): DocumentFragment {
  const html = fs.readFileSync(file, "utf-8");
  const fragment =
      parse5.parseFragment(html, {locationInfo: true});
  update(fragment, {});
  return fragment;
}



function convertBehavior(htmlFile: FilePath) {
  const ast = getAst(htmlFile);

}

convertBehavior("/usr/local/google/home/dmfilippov/gerrit-p2/polygerrit-ui/app/behaviors/async-foreach-behavior/async-foreach-behavior.html" as FilePath);
convertBehavior("/usr/local/google/home/dmfilippov/gerrit-p2/polygerrit-ui/app/elements/shared/gr-textarea/gr-textarea.html" as FilePath);
