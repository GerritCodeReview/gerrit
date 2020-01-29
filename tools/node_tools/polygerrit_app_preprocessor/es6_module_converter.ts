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
import * as fs from "fs";
import {AST} from "parse5";
import Node = AST.Default.Node;
import ParentNode = AST.Default.ParentNode;
import CommentNode = AST.Default.CommentNode;
import TextNode = AST.Default.TextNode;
import DocumentType = AST.Default.DocumentType;
import Document = AST.Default.Document;
import DocumentFragment = AST.Default.DocumentFragment;
import Element = AST.Default.Element;
import {fail, unexpectedSwitchValue} from '../utils/common';
import {template} from 'polymer-bundler/lib/matchers';

interface HtmlFileConversionResult {
  files: Map<FilePath, string>;
}

interface HtmlFileConverter {
  convert(fileName: FilePath, pathResolver: SrcWebSite): HtmlFileConversionResult;
}

type FilePathPredicate = (fileName: FilePath) => boolean;

type AstNode = Node | ParentNode;


function isCommentNode(node: AstNode): node is CommentNode {
  return !isParentNode(node) && node.nodeName === '#comment';
}

function isTextNode(node: AstNode): node is TextNode {
  return !isParentNode(node) && node.nodeName === '#text';
}
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
function isElementNode(node: AstNode): node is Element {
  return isParentNode(node) && node.hasOwnProperty("tagName");
}

function isParentNode(node: AstNode): node is ParentNode {
  return (node as any).childNodes !== undefined;
}

const htmlToJsImportMapping: {[htmlName: string]: string[]} = {
  "polymer-bridges/iron-a11y-keys-behavior/iron-a11y-keys-behavior.html": ["polymer-bridges/iron-a11y-keys-behavior/iron-a11y-keys-behavior_bridge.js"],
  "polymer-bridges/iron-autogrow-textarea/iron-autogrow-textarea.html": ["polymer-bridges/iron-autogrow-textarea/iron-autogrow-textarea_bridge.js"],
  "polymer-bridges/iron-dropdown/iron-dropdown.html": ["polymer-bridges/iron-dropdown/iron-dropdown_bridge.js"],
  "polymer-bridges/iron-fit-behavior/iron-fit-behavior.html": ["polymer-bridges/iron-fit-behavior/iron-fit-behavior_bridge.js"],
  "polymer-bridges/iron-iconset-svg/iron-iconset-svg.html": ["polymer-bridges/iron-iconset-svg/iron-iconset-svg_bridge.js"],
  "polymer-bridges/iron-input/iron-input.html": ["polymer-bridges/iron-input/iron-input_bridge.js"],
  "polymer-bridges/iron-overlay-behavior/iron-overlay-behavior.html": ["polymer-bridges/iron-overlay-behavior/iron-overlay-behavior_bridge.js"],
  "polymer-bridges/iron-overlay-behavior/iron-overlay-manager.html": ["polymer-bridges/iron-overlay-behavior/iron-overlay-manager_bridge.js"],
  "polymer-bridges/iron-selector/iron-selector.html": ["polymer-bridges/iron-selector/iron-selector_bridge.js"],
  "polymer-bridges/iron-test-helpers/iron-test-helpers.html": ["polymer-bridges/iron-test-helpers/iron-test-helpers_bridge.js"],
  "polymer-bridges/paper-button/paper-button.html": ["polymer-bridges/paper-button/paper-button_bridge.js"],
  "polymer-bridges/paper-input/paper-input.html": ["polymer-bridges/paper-input/paper-input_bridge.js"],
  "polymer-bridges/paper-item/paper-item.html": ["polymer-bridges/paper-item/paper-item_bridge.js"],
  "polymer-bridges/paper-listbox/paper-listbox.html": ["polymer-bridges/paper-listbox/paper-listbox_bridge.js"],
  "polymer-bridges/paper-tabs/paper-tab.html": ["polymer-bridges/paper-tabs/paper-tab_bridge.js"],
  "polymer-bridges/paper-toggle-button/paper-toggle-button.html": ["polymer-bridges/paper-toggle-button/paper-toggle-button_bridge.js"],
  "polymer-bridges/polymer-resin/standalone/polymer-resin.html": ["polymer-bridges/polymer-resin/standalone/polymer-resin_bridge.js"],
  "polymer-bridges/polymer/polymer.html": ["/scripts/polymer-legacy.js"],
};

function getJsImportInsteadOfHtml(href: string): string[] {
  const nodeModulesPrefix = "/node_modules/";
  if(href.startsWith(nodeModulesPrefix)) {
    const nodeModulesPath = href.substring(nodeModulesPrefix.length);
    const newPaths = htmlToJsImportMapping[nodeModulesPath];
    if(!newPaths) {
      fail(`Can't find node_modules mapping for ${href}`);
    }
    return newPaths;
  }
  const htmlExt = ".html";
  if(!href.endsWith(htmlExt)) {
    fail(`Href is not an html file: ${href}`);
  }
  //TODO: support import from node_modules
  return [href.substr(0, href.length - htmlExt.length) + ".js"];
}

interface JsFileBuilder {
  addImportStatement(from: string): void;
  addJsContent(content: string): void;
  addHtmlTemplate(variableName: string, templateContent: string): void;
  addPolymerHtmlTagImport(): void;
}

enum ProcessNodeCallbackResult {
  Continue,
  SkipChildren,
}

type ProcessNodeCallback = (node: AstNode) => ProcessNodeCallbackResult;

function walkTree(node: AstNode, cb: ProcessNodeCallback) {
  const processResult = cb(node);
  switch(processResult) {
    case ProcessNodeCallbackResult.SkipChildren:
      return;
    case ProcessNodeCallbackResult.Continue:
      if(isParentNode(node)) {
        node.childNodes.forEach((child) => walkTree(child, cb));
      }
      return;
    default:
      unexpectedSwitchValue(processResult);
  }
}

function ensureNoChildNodes(node: AstNode) {
  if(isParentNode(node) && node.childNodes.length > 0) {
    fail(`Node ${node} must not have child nodes`);
  }
}

function getNodeStartCharOffset(node: AstNode): number {
  if(isTextNode(node) || isCommentNode(node)) {
    if(!node.__location) {
      fail(`Node must have location`);
    }
    return node.__location.startOffset;
  }
  fail(`Unsupported node ${node}`);
}

function getNodeEndCharOffset(node: AstNode): number {
  if(isTextNode(node) || isCommentNode(node)) {
    if(!node.__location) {
      fail(`Node must have location`);
    }
    return node.__location.endOffset;
  }
  fail(`Unsupported node ${node}`);
}

function getTemplateContent(htmlContent: string, templateElement: Element): string {
  if(templateElement.childNodes.length !== 0) {
    fail(`Internal error. Template tag must not have childNodes`);
  }
  let templateContent = parse5.treeAdapters.default.getTemplateContent(templateElement) as DocumentFragment;
  const childCount = templateContent.childNodes.length;
  if(childCount === 0) {
    return "";
  }
  const lastTemplateNode = templateContent.childNodes[childCount - 1];
  const startCharIndex = getNodeStartCharOffset(templateContent.childNodes[0]);
  const lastCharIndex = getNodeEndCharOffset(templateContent.childNodes[childCount - 1]);
  return htmlContent.substring(startCharIndex, lastCharIndex);
}

function getTemplateFromDomModule(htmlContent: string, node: Element): string {
  let templateContent: string | null = "";
  for(const child of node.childNodes) {
    if(isElementNode(child)) {
      if(child.tagName === "template") {
        if(templateContent) {
          fail(`Expected only one template tag in dom-module`);
        }
        templateContent = getTemplateContent(htmlContent, child);
        continue;
      }
      if(child.tagName === "script") {
        continue;
      }
      fail(`'${child.tagName}' tag is not allowed inside dom-module`);
    }
    if(isTextNode(child)) {
      if(child.value.trim() === "") {
        continue;
      }
      console.log(`Text in node:!!!`);
      console.log(child.value.trim());
    }

    fail(`Node ${child.nodeName} is not allowed inside dom-module`);
  }
  return templateContent;
}

function getRawText(node: Element): string {
  if(node.childNodes.length > 1) {
    fail(`Expected zero or one child nodes`);
  }
  if(node.childNodes.length === 0) {
    return "";
  }
  const singleChildNode = node.childNodes[0];
  if(!isTextNode(singleChildNode)) {
    fail(`Expected that node has the only text child node`);
  }
  return singleChildNode.value;
}

function hasAttribute(node: Element, name: string): boolean {
  for(const attr of node.attrs) {
    if(attr.name === name) {
      return true;
    }
  }
  return false;
}

function getAttribute(node: Element, name: string): string | null {
  for(const attr of node.attrs) {
    if(attr.name === name) {
      return attr.value;
    }
  }
  return null;
}

function update(htmlContent: string, fragment: DocumentFragment, jsTemplateFileBuilder: JsFileBuilder, jsFileBuilder: JsFileBuilder) {
  const nodesToRemove: Node[] = [];
  let domModuleNode: Element | null = null;
  walkTree(fragment, (node: Node | ParentNode) => {
    if(isElementNode(node)) {
      if(node.tagName === 'link' && getAttribute(node, "rel") === "import" && hasAttribute(node, "href")) {
        ensureNoChildNodes(node);
        const href = getAttribute(node, "href")!;
        getJsImportInsteadOfHtml(href).forEach(jsImport => {
          jsFileBuilder.addImportStatement(jsImport);
        });
        nodesToRemove.push(node);
        return ProcessNodeCallbackResult.Continue;
      }
      if(node.tagName === 'dom-module') {
        if(domModuleNode) {
          fail(`file must have only one dom-module`);
        }
        domModuleNode = node;
        nodesToRemove.push(node);
        return ProcessNodeCallbackResult.Continue;
      }
      if(node.tagName === 'script') {
        if(hasAttribute(node, "src")) {
          ensureNoChildNodes(node);
          jsFileBuilder.addImportStatement(getAttribute(node, "src")!);
          return ProcessNodeCallbackResult.Continue;
        } else {
          jsFileBuilder.addJsContent(getRawText(node));
        }
        nodesToRemove.push(node);
      }
    }

    return ProcessNodeCallbackResult.Continue;
  });
  if(domModuleNode) {
    const template = getTemplateFromDomModule(htmlContent, domModuleNode);
    jsTemplateFileBuilder.addPolymerHtmlTagImport();
    jsTemplateFileBuilder.addHtmlTemplate("template", template);
  }
  nodesToRemove.forEach(node => {
    parse5.treeAdapters.default.detachNode(node);
  });
  if(domModuleNode) {
    parse5.treeAdapters.default.detachNode(domModuleNode);
  }
}

class DefaultJsTemplateFileBuilder implements JsFileBuilder {
  public constructor(private readonly name: string) {
  }

  addHtmlTemplate(variableName: string, templateContent: string): void {
    console.log(`${this.name}: export ${variableName} = html\`${templateContent}\`;`);
  }

  addImportStatement(from: string): void {
    console.log(`${this.name}: import from '${from}';`);
  }

  addPolymerHtmlTagImport(): void {
    console.log(`${this.name}: import {html} from '@polymer/polymer/lib/utils/html-tag.js';`);
  }

  addJsContent(content: string): void {
    console.log(`${this.name}: ${content}`);
  }
}


function convertFile(htmlFile: FilePath) {
  const html = fs.readFileSync(htmlFile, "utf-8");
  const fragment =
      parse5.parseFragment(html, {locationInfo: true}) as DocumentFragment;
  const templateBuilder = new DefaultJsTemplateFileBuilder("tplt");
  const codeBuilder = new DefaultJsTemplateFileBuilder("code");
  update(html, fragment, templateBuilder, codeBuilder);
  //return fragment;
  for(const child of fragment.childNodes) {
    if(isCommentNode(child)) {
      continue;
    }
    if(isTextNode(child)) {
      if(child.value.trim() === "") {
        continue;
      }
    }
    fail(`Html file still have some meaningful content`);
  }
}

//convertBehavior("/usr/local/google/home/dmfilippov/gerrit-p2/polygerrit-ui/app/behaviors/async-foreach-behavior/async-foreach-behavior.html" as FilePath);
const usrPath = "/usr/local/google/home/dmfilippov";
convertFile(usrPath + "/output-updated/usr/local/google/home/dmfilippov/gerrit-p2/polygerrit-ui/app/elements/shared/gr-textarea/gr-textarea.html" as FilePath);
