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

/**
 * @fileoverview We should consider dropping support for this API:
 *
 * 1. we need to try avoid using `innerHTML` for xss concerns
 * 2. we have css variables which are more recommended way to custom styling
 */

/**
 * // import { useShadow } from '@polymer/polymer/lib/utils/settings';
 * TODO(TS): polymer/lib/utils/settings.d.ts is not exporting useShadow
 * while the js is, to avoid the error, re-define it here
 */
const useShadow = !window.ShadyDOM || !window.ShadyDOM.inUse;

let styleObjectCount = 0;

interface PgElement extends Element {
  __pg_js_api_style_tags: {
    [className: string]: boolean;
  };
}

class GrStyleObject {
  private className = '';

  constructor(private readonly rulesStr: string) {
    this.className = `__pg_js_api_class_${styleObjectCount}`;
    styleObjectCount++;
  }

  /**
   * Creates a new unique CSS class and injects it in a root node of the element
   * if it hasn't been added yet. A root node is an document or is the
   * associated shadowRoot. This class can be added to any element with the same
   * root node.
   *
   */
  getClassName(element: Element) {
    let rootNodeEl = useShadow ? element.getRootNode() : document.body;
    if (rootNodeEl === document) {
      rootNodeEl = document.head;
    }
    // TODO(TS): type casting to have correct interface
    // maybe move this __pg_xxx to attribute
    const rootNode: PgElement = rootNodeEl as PgElement;
    if (!rootNode.__pg_js_api_style_tags) {
      rootNode.__pg_js_api_style_tags = {};
    }
    if (!rootNode.__pg_js_api_style_tags[this.className]) {
      const styleTag = document.createElement('style');
      styleTag.innerHTML = `.${this.className} { ${this.rulesStr} }`;
      rootNode.appendChild(styleTag);
      rootNode.__pg_js_api_style_tags[this.className] = true;
    }
    return this.className;
  }

  /**
   * Apply shared style to the element.
   *
   */
  apply(element: Element) {
    element.classList.add(this.getClassName(element));
  }
}

/**
 * TODO(TS): move to util
 */
export class GrStylesApi {
  /**
   * Creates a new GrStyleObject with specified style properties.
   */
  css(ruleStr: string) {
    return new GrStyleObject(ruleStr);
  }
}
