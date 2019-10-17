/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
(function(window) {
  'use strict';

  // Prevent redefinition.
  if (window.GrStylesApi) { return; }

  let styleObjectCount = 0;

  function GrStyleObject(rulesStr) {
    this._rulesStr = rulesStr;
    this._className = `__pg_js_api_class_${styleObjectCount}`;
    styleObjectCount++;
  }

  /**
   * Creates a new unique CSS class and injects it in a root node of the element
   * if it hasn't been added yet. A root node is an document or is the
   * associated shadowRoot. This class can be added to any element with the same
   * root node.
   * @param {HTMLElement} element The element to get class name for.
   * @return {string} Appropriate class name for the element is returned
   */
  GrStyleObject.prototype.getClassName = function(element) {
    let rootNode = Polymer.Settings.useShadow
      ? element.getRootNode() : document.body;
    if (rootNode === document) {
      rootNode = document.head;
    }
    if (!rootNode.__pg_js_api_style_tags) {
      rootNode.__pg_js_api_style_tags = {};
    }
    if (!rootNode.__pg_js_api_style_tags[this._className]) {
      const styleTag = document.createElement('style');
      styleTag.innerHTML = `.${this._className} { ${this._rulesStr} }`;
      rootNode.appendChild(styleTag);
      rootNode.__pg_js_api_style_tags[this._className] = true;
    }
    return this._className;
  };

  /**
   * Apply shared style to the element.
   * @param {HTMLElement} element The element to apply style for
   */
  GrStyleObject.prototype.apply = function(element) {
    element.classList.add(this.getClassName(element));
  };


  function GrStylesApi() {
  }

  /**
   * Creates a new GrStyleObject with specified style properties.
   * @param {string} String with style properties.
   * @return {GrStyleObject}
  */
  GrStylesApi.prototype.css = function(ruleStr) {
    return new GrStyleObject(ruleStr);
  };


  window.GrStylesApi = GrStylesApi;
})(window);
