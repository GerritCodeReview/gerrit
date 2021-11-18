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
      .gr-syntax-addition { /* added or changed line */
        color: var(--syntax-addition-color);
      }
      .gr-syntax-attr { /* name of an attribute, also sub-attribute within another highlighted object, like XML tag */
        color: var(--syntax-attr-color);
      }
      .gr-syntax-attribute { /* name of an attribute followed by a structured value part, like CSS properties */
        color: var(--syntax-attribute-color);
      }
      .gr-syntax-built_in { /* built-in or library object (constant, class, function) */
        color: var(--syntax-built_in-color);
      }
      .gr-syntax-bullet { /* list item bullet */
        color: var(--syntax-bullet-color);
      }
      .gr-syntax-code { /* code block */
        color: var(--syntax-code-color);
      }
      .gr-syntax-char-escape { /* an escape character such as \n */
        color: var(--syntax-char-escape-color);
      }
      .gr-syntax-comment { /* comments */
        color: var(--syntax-comment-color);
      }
      .gr-syntax-deletion { /* deleted line */
        color: var(--syntax-deletion-color);
      }
      .gr-syntax-doctag { /* documentation markup within comments, e.g. @params */
        color: var(--syntax-doctag-color);
        font-weight: var(--syntax-doctag-weight);
      }
      .gr-syntax-emphasis { /* emphasis in markup */
        color: var(--syntax-emphasis-color);
        font-style: var(--syntax-emphasis-style);
        font-weight: var(--syntax-emphasis-weight);
      }
      .gr-syntax-formula { /* mathematical formula */
        color: var(--syntax-formula-color);
      }
      .gr-syntax-keyword { /* keyword in a regular Algol-style language */
        color: var(--syntax-keyword-color);
      }
      .gr-syntax-link { /* hyperlink */
        color: var(--syntax-link-color);
        text-decoration: var(--syntax-link-decoration);
      }
      .gr-syntax-literal { /* special identifier for a built-in value (true, false, null, etc.) */
        color: var(--syntax-literal-color);
      }
      .gr-syntax-meta { /* flags, modifiers, annotations, processing instructions, preprocessor directives, etc */
        color: var(--syntax-meta-color);
      }
      .gr-syntax-meta-keyword { /* a keyword inside a meta block */
        color: var(--syntax-meta-keyword-color);
      }
      .gr-syntax-meta-string { /* a string inside a meta block */
        color: var(--syntax-meta-string-color);
      }
      .gr-syntax-name { /* name of an XML tag, the first word in an s-expression */
        color: var(--syntax-name-color);
      }
      .gr-syntax-number { /* number, including units and modifiers, if any. */
        color: var(--syntax-number-color);
      }
      .gr-syntax-operator { /* operators: +, -, >>, |, == */
        color: var(--syntax-operator-color);
      }
      .gr-syntax-params { /* block of function arguments (parameters) at the place of declaration */
        color: var(--syntax-params-color);
      }
      .gr-syntax-property { /* object property obj.prop1.prop2.value */
        color: var(--syntax-property-color);
      }
      .gr-syntax-punctuation { /* aux. punctuation that should be subtly highlighted (parentheses, brackets, etc.) */
        color: var(--syntax-punctuation-color);
      }
      .gr-syntax-quote { /* quotation or blockquote */
        color: var(--syntax-quote-color);
      }
      .gr-syntax-regexp { /* literal regular expression */
        color: var(--syntax-regexp-color);
      }
      .gr-syntax-section { /* heading of a section in a config file, heading in text markup */
        color: var(--syntax-section-color);
        font-style: var(--syntax-section-style);
        font-weight: var(--syntax-section-weight);
      }
      .gr-syntax-selector-attr { /* CSS [attr] selector */
        color: var(--syntax-selector-attr-color);
      }
      .gr-syntax-selector-class { /* CSS .class selector */
        color: var(--syntax-selector-class-color);
      }
      .gr-syntax-selector-id { /* CSS #id selector */
        color: var(--syntax-selector-id-color);
      }
      .gr-syntax-selector-pseudo { /* CSS :pseudo selector */
        color: var(--syntax-selector-pseudo-color);
      }
      .gr-syntax-selector-tag { /* CSS tag selector */
        color: var(--syntax-selector-tag-color);
      }
      .gr-syntax-string { /* literal string, character */
        color: var(--syntax-string-color);
      }
      .gr-syntax-strong { /* strong emphasis in markup */
        color: var(--syntax-strong-color);
        font-style: var(--syntax-strong-style);
        font-weight: var(--syntax-strong-weight);
      }
      .gr-syntax-subst { /* parsed section inside a literal string */
        color: var(--syntax-subst-color);
      }
      .gr-syntax-symbol { /* symbolic constant, interned string, goto label */
        color: var(--syntax-symbol-color);
      }
      .gr-syntax-tag { /* XML/HTML tag */
        color: var(--syntax-tag-color);
      }
      .gr-syntax-template-tag { /* tag of a template language */
        color: var(--syntax-template-tag-color);
      }
      .gr-syntax-template-variable { /* variable in a template language */
        color: var(--syntax-template-variable-color);
      }
      .gr-syntax-title { /* name of a class or a function */
        color: var(--syntax-title-color);
      }
      .gr-syntax-title-class { /* name of a class (interface, trait, module, etc) */
        color: var(--syntax-title-class-color);
      }
      .gr-syntax-title-class-inherited { /* name of class being inherited from, extended, etc. */
        color: var(--syntax-title-class-inherited-color);
      }
      .gr-syntax-title-function { /* name of a function */
        color: var(--syntax-title-function-color);
      }
      .gr-syntax-type { /* data type (in a language with syntactically significant types) (string, int, array, etc.) */
        color: var(--syntax-type-color);
      }
      .gr-syntax-variable { /* variables */
        color: var(--syntax-variable-color);
      }
      .gr-syntax-variable-language { /* variable with special meaning in a language, e.g.: this, window, super, self, etc. */
        color: var(--syntax-variable-language-color);
      }
      .gr-syntax-variable-constant { /* variable that is a constant value, ie MAX_FILES */
        color: var(--syntax-variable-constant-color);
      }
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
