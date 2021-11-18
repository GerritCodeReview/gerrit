/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
export {};

const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<dom-module id="gr-syntax-theme">
  <template>
    <style>
      /**
       * @overview Highlight.js emits the following classes
       * @see {@link http://highlightjs.readthedocs.io/en/latest/css-classes-reference.html}
       */

      .contentText {
        color: var(--syntax-default-color);
      }
      .gr-syntax-attr {
        color: var(--syntax-attr-color);
      }
      .gr-syntax-attribute {
        color: var(--syntax-attribute-color);
      }
      .gr-syntax-built_in {
        color: var(--syntax-built_in-color);
      }
      .gr-syntax-bullet {
        color: var(--syntax-bullet-color);
      }
      .gr-syntax-code {
        color: var(--syntax-code-color);
      }
      .gr-syntax-comment {
        color: var(--syntax-comment-color);
      }
      .gr-syntax-doctag {
        font-weight: var(--syntax-doctag-weight);
      }
      .gr-syntax-emphasis {
        color: var(--syntax-emphasis-color);
	font-style: var(--syntax-emphasis-style);;
	font-weight: var(--syntax-emphasis-weight);;
      }
      .gr-syntax-formula {
        color: var(--syntax-formula-color);
      }
      .gr-syntax-function {
        color: var(--syntax-function-color);
      }
      .gr-syntax-keyword {
        color: var(--syntax-keyword-color);
      }
      .gr-syntax-link {
        color: var(--syntax-link-color);
      }
      .gr-syntax-literal { /* XML/HTML Attribute */
        color: var(--syntax-literal-color);
      }
      .gr-syntax-meta {
        color: var(--syntax-meta-color);
      }
      .gr-syntax-meta-keyword {
        color: var(--syntax-meta-keyword-color);
      }
      .gr-syntax-meta-string {
        color: var(--syntax-meta-string-color);
      }
      .gr-syntax-name {
        color: var(--syntax-name-color);
      }
      .gr-syntax-number {
        color: var(--syntax-number-color);
      }
      .gr-syntax-operator {
        color: var(--syntax-operator-color);
      }
      .gr-syntax-params {
        color: var(--syntax-params-color);
      }
      .gr-syntax-property {
        color: var(--syntax-property-color);
      }
      .gr-syntax-punctuation {
        color: var(--syntax-punctuation-color);
      }
      .gr-syntax-quote {
        color: var(--syntax-quote-color);
      }
      .gr-syntax-regexp {
        color: var(--syntax-regexp-color);
      }
      .gr-syntax-section {
        color: var(--syntax-section-color);
	font-style: var(--syntax-section-style);
	font-weight: var(--syntax-section-weight);
      }
      .gr-syntax-selector-attr {
        color: var(--syntax-selector-attr-color);
      }
      .gr-syntax-selector-class {
        color: var(--syntax-selector-class-color);
      }
      .gr-syntax-selector-id {
        color: var(--syntax-selector-id-color);
      }
      .gr-syntax-selector-pseudo {
        color: var(--syntax-selector-pseudo-color);
      }
      .gr-syntax-selector-tag {
        color: var(--syntax-selector-tag-color);
      }
      .gr-syntax-string {
        color: var(--syntax-string-color);
      }
      .gr-syntax-strong {
        color: var(--syntax-strong-color);
	font-style: var(--syntax-strong-style);
	font-weight: var(--syntax-strong-weight);
      }
      .gr-syntax-subst {
        color: var(--syntax-subst-color);
      }
      .gr-syntax-symbol {
        color: var(--syntax-symbol-color);
      }
      .gr-syntax-tag {
        color: var(--syntax-tag-color);
      }
      .gr-syntax-template-tag {
        color: var(--syntax-template-tag-color);
      }
      .gr-syntax-template-variable {
        color: var(--syntax-template-variable-color);
      }
      .gr-syntax-title {
        color: var(--syntax-title-color);
      }
      .gr-syntax-title-class {
        color: var(--syntax-title-class-color);
      }
      .gr-syntax-title-class-inherited {
        color: var(--syntax-title-class-inherited-color);
      }
      .gr-syntax-title-function {
        color: var(--syntax-title-function-color);
      }
      .gr-syntax-type {
        color: var(--syntax-type-color);
      }
      .gr-syntax-variable {
        color: var(--syntax-variable-color);
      }
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
